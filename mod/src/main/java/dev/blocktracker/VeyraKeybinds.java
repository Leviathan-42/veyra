package dev.blocktracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VeyraKeybinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.tryParse("blocktracker:veyra"));

    public static final KeyMapping OPEN_SEARCH = new KeyMapping(
            "key.blocktracker.open_search",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_BACKSLASH,
            CATEGORY
    );
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.blocktracker.open_menu",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_RSHIFT,
            CATEGORY
    );
    public static final KeyMapping TOGGLE_FREECAM = new KeyMapping(
            "key.blocktracker.toggle_freecam",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            CATEGORY
    );

    private static final KeyMapping[] ALL = {
            OPEN_SEARCH,
            OPEN_MENU,
            TOGGLE_FREECAM
    };

    private VeyraKeybinds() {
    }

    public static void tick(Minecraft client) {
        if (client.player == null) {
            drainClicks();
            return;
        }

        while (OPEN_SEARCH.consumeClick()) {
            if (isShiftDown(client)) {
                BlockTrackerState.clear();
                if (client.gui.screen() instanceof BlockSearchScreen) {
                    client.gui.setScreen(null);
                }
                continue;
            }

            if (client.gui.screen() instanceof BlockSearchScreen) {
                client.gui.setScreen(null);
            } else if (client.gui.screen() == null) {
                client.gui.setScreen(new BlockSearchScreen());
            }
        }

        while (OPEN_MENU.consumeClick()) {
            if (client.gui.screen() instanceof BlockTrackerConfigScreen) {
                client.gui.setScreen(null);
            } else if (client.gui.screen() == null) {
                client.gui.setScreen(new BlockTrackerConfigScreen());
            }
        }
    }

    public static KeyMapping[] appendTo(KeyMapping[] existing) {
        List<KeyMapping> mappings = new ArrayList<>();
        if (existing != null) {
            mappings.addAll(Arrays.asList(existing));
        }

        for (KeyMapping mapping : ALL) {
            if (!contains(mappings, mapping.getName())) {
                mappings.add(mapping);
            }
        }

        return mappings.toArray(KeyMapping[]::new);
    }

    public static boolean cancelBlockSearchMatches(KeyEvent event) {
        return event.key() == InputConstants.KEY_BACKSLASH && (event.modifiers() & InputConstants.MOD_SHIFT) != 0;
    }

    public static boolean openSearchMatches(KeyEvent event) {
        return OPEN_SEARCH.matches(event);
    }

    public static boolean openMenuMatches(KeyEvent event) {
        return OPEN_MENU.matches(event);
    }

    public static boolean openSearchMatches(MouseButtonEvent event) {
        return OPEN_SEARCH.matchesMouse(event);
    }

    public static boolean openMenuMatches(MouseButtonEvent event) {
        return OPEN_MENU.matchesMouse(event);
    }

    public static String searchKeyName() {
        return keyName(OPEN_SEARCH);
    }

    public static String menuKeyName() {
        return keyName(OPEN_MENU);
    }

    public static String freecamKeyName() {
        return keyName(TOGGLE_FREECAM);
    }

    private static String keyName(KeyMapping mapping) {
        Component key = mapping.getTranslatedKeyMessage();
        String text = key.getString();
        return text == null || text.isBlank() ? mapping.saveString() : text;
    }

    private static boolean isShiftDown(Minecraft client) {
        return InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RSHIFT);
    }

    private static boolean contains(List<KeyMapping> mappings, String name) {
        for (KeyMapping mapping : mappings) {
            if (mapping.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void drainClicks() {
        for (KeyMapping mapping : ALL) {
            while (mapping.consumeClick()) {
                // drop clicks while no world/player is active
            }
        }
    }
}
