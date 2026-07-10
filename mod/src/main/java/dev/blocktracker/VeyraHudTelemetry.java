package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/** Collects client-observed HUD metrics without changing gameplay or network traffic. */
public final class VeyraHudTelemetry {
    private static final int PING_SAMPLE_COUNT = 60;
    private static final int PING_SAMPLE_INTERVAL_TICKS = 10;
    private static final long CLICK_WINDOW_NANOS = 1_000_000_000L;
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("h:mm:ss a");

    private static final int[] pingSamples = new int[PING_SAMPLE_COUNT];
    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static int pingWriteIndex;
    private static int pingSampleSize;
    private static int sampleTicks;
    private static boolean wasInWorld;
    private static long sessionStartedNanos = System.nanoTime();

    private VeyraHudTelemetry() {
    }

    public static void tick(Minecraft client) {
        boolean inWorld = client.player != null && client.level != null;
        if (inWorld && !wasInWorld) {
            sessionStartedNanos = System.nanoTime();
            pingWriteIndex = 0;
            pingSampleSize = 0;
            sampleTicks = 0;
            Arrays.fill(pingSamples, 0);
            leftClicks.clear();
        }
        wasInWorld = inWorld;

        pruneClicks();
        if (!inWorld || ++sampleTicks < PING_SAMPLE_INTERVAL_TICKS) {
            return;
        }

        sampleTicks = 0;
        pingSamples[pingWriteIndex] = currentPing(client);
        pingWriteIndex = (pingWriteIndex + 1) % pingSamples.length;
        pingSampleSize = Math.min(pingSamples.length, pingSampleSize + 1);
    }

    public static void recordLeftClick() {
        long now = System.nanoTime();
        leftClicks.addLast(now);
        pruneClicks(now);
    }

    public static int cps() {
        pruneClicks();
        return leftClicks.size();
    }

    public static int currentPing(Minecraft client) {
        try {
            if (client.player == null || client.getConnection() == null) {
                return 0;
            }
            PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            return info == null ? 0 : Math.max(0, info.getLatency());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static int[] pingSamples() {
        int[] ordered = new int[pingSampleSize];
        int first = (pingWriteIndex - pingSampleSize + pingSamples.length) % pingSamples.length;
        for (int index = 0; index < pingSampleSize; index++) {
            ordered[index] = pingSamples[(first + index) % pingSamples.length];
        }
        return ordered;
    }

    public static int averagePing() {
        int[] samples = pingSamples();
        if (samples.length == 0) {
            return 0;
        }
        long total = 0;
        for (int sample : samples) {
            total += sample;
        }
        return (int) Math.round(total / (double) samples.length);
    }

    public static int maxPing() {
        int maximum = 0;
        for (int sample : pingSamples()) {
            maximum = Math.max(maximum, sample);
        }
        return maximum;
    }

    public static String sessionText() {
        long totalSeconds = Math.max(0L, (System.nanoTime() - sessionStartedNanos) / 1_000_000_000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }

    public static String clockText() {
        return LocalTime.now().format(CLOCK_FORMAT);
    }

    public static String musicText(Minecraft client) {
        String key;
        try {
            key = client.getMusicManager().getCurrentMusicTranslationKey();
        } catch (RuntimeException ignored) {
            return "No music playing";
        }
        if (key == null || key.isBlank()) {
            return "No music playing";
        }

        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) {
            return translated;
        }

        int separator = Math.max(key.lastIndexOf('.'), key.lastIndexOf('/'));
        String fallback = separator >= 0 ? key.substring(separator + 1) : key;
        return titleCase(fallback.replace('_', ' '));
    }

    private static void pruneClicks() {
        pruneClicks(System.nanoTime());
    }

    private static void pruneClicks(long now) {
        long oldest = now - CLICK_WINDOW_NANOS;
        while (!leftClicks.isEmpty() && leftClicks.peekFirst() < oldest) {
            leftClicks.removeFirst();
        }
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = Character.isWhitespace(character);
        }
        return result.toString();
    }
}
