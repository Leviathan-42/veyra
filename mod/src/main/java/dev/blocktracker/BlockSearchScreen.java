package dev.blocktracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class BlockSearchScreen extends Screen {
    private static final int MAX_SUGGESTIONS = 6;
    private static final int SUGGESTION_ROW_HEIGHT = 18;
    private static final int NO_MATCH = 10_000;

    private EditBox input;
    private Component status = Component.translatable("message.blocktracker.enter_block");
    private List<Suggestion> suggestions = List.of();
    private int selectedSuggestion = -1;
    private int boxX;
    private int boxY;
    private int boxWidth;
    private int suggestionsY;
    private int buttonY;
    private int statusY;

    public BlockSearchScreen() {
        super(Component.translatable("screen.blocktracker.search"));
    }

    @Override
    protected void init() {
        boxWidth = Math.min(360, this.width - 40);
        boxX = (this.width - boxWidth) / 2;
        boxY = Math.max(12, (this.height - 224) / 2);
        int inputY = boxY + 34;
        suggestionsY = inputY + 28;
        buttonY = suggestionsY + (MAX_SUGGESTIONS * SUGGESTION_ROW_HEIGHT) + 8;
        statusY = buttonY + 30;

        input = new EditBox(this.font, boxX + 10, inputY + 2, boxWidth - 20, 18, Component.literal("Block ID"));
        input.setHint(Component.literal("chest or minecraft:diamond_ore"));
        input.setMaxLength(128);
        input.setResponder(this::updateSuggestions);
        addRenderableWidget(input);
        setInitialFocus(input);

        updateSuggestions(input.getValue());
    }

    private void submit() {
        String raw = input.getValue().trim();
        Identifier id = resolveBlockId(raw);
        Block block = null;

        if (id != null) {
            block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        }

        if (block == null && !suggestions.isEmpty()) {
            Suggestion suggestion = suggestions.get(Math.max(0, selectedSuggestion));
            id = suggestion.id();
            block = suggestion.block();
            input.setValue(id.toString());
        }

        if (id == null || block == null) {
            status = Component.translatable("message.blocktracker.unknown_block");
            return;
        }

        List<Block> targets = oreVariants(id, block);
        BlockPos nearest = BlockScan.findClosest(Minecraft.getInstance(), targets, BlockScan.DEFAULT_CHUNK_RADIUS);
        BlockTrackerState.setTarget(id, targets, nearest);

        if (nearest == null) {
            status = Component.literal("No matching loaded block found");
            return;
        }

        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_BACKSLASH) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }

        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            submit();
            return true;
        }

        if (event.key() == InputConstants.KEY_DOWN && !suggestions.isEmpty()) {
            selectedSuggestion = Math.min(suggestions.size() - 1, selectedSuggestion + 1);
            return true;
        }

        if (event.key() == InputConstants.KEY_UP && !suggestions.isEmpty()) {
            selectedSuggestion = Math.max(0, selectedSuggestion - 1);
            return true;
        }

        if (event.key() == InputConstants.KEY_TAB && !suggestions.isEmpty()) {
            Suggestion suggestion = suggestions.get(Math.max(0, selectedSuggestion));
            input.setValue(suggestion.id().toString());
            input.moveCursorToEnd(false);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int index = suggestionIndexAt(event.x(), event.y());
            if (index >= 0) {
                selectedSuggestion = index;
                input.setValue(suggestions.get(index).id().toString());
                submit();
                return true;
            }

            if (event.x() >= boxX && event.x() <= boxX + boxWidth && event.y() >= buttonY && event.y() <= buttonY + 22) {
                submit();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        VeyraUi.screenBackground(graphics, this.width, this.height, true);
        VeyraUi.panel(graphics, boxX - 16, boxY, boxWidth + 32, 214);
        VeyraUi.text(graphics, this.font, "Block search", boxX, boxY + 12, 0xFFF4F7FA);
        VeyraUi.text(graphics, this.font, "Type a block, pick a suggestion, then track it", boxX, boxY + 27, 0xFF8B96A5);
        VeyraUi.button(graphics, boxX, boxY + 34, boxWidth, 22, true, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        extractSuggestions(graphics, mouseX, mouseY);
        VeyraUi.button(graphics, boxX, buttonY, boxWidth, 22, true,
                mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= buttonY && mouseY <= buttonY + 22);
        VeyraUi.centeredText(graphics, this.font, "Track", this.width / 2, buttonY + 7, 0xFFF4F7FA);
        VeyraUi.centeredText(graphics, this.font, status.getString(), this.width / 2, statusY, 0xFF8B96A5);
    }

    private void updateSuggestions(String raw) {
        String query = raw.trim();
        if (query.isEmpty()) {
            suggestions = List.of();
            selectedSuggestion = -1;
            status = Component.translatable("message.blocktracker.enter_block");
            return;
        }

        List<Suggestion> matches = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
            if (block.isEmpty()) {
                continue;
            }

            String label = labelFor(id);
            int score = score(query, id, label);
            if (score < NO_MATCH) {
                matches.add(new Suggestion(id, block.get(), label, score));
            }
        }

        matches.sort(Comparator
                .comparingInt(Suggestion::score)
                .thenComparing(Suggestion::label)
                .thenComparing(suggestion -> suggestion.id().toString()));

        suggestions = matches.stream().limit(MAX_SUGGESTIONS).toList();
        selectedSuggestion = suggestions.isEmpty() ? -1 : 0;
        status = suggestions.isEmpty()
                ? Component.translatable("message.blocktracker.no_matches")
                : Component.translatable("message.blocktracker.suggestions");
    }

    private Identifier resolveBlockId(String raw) {
        if (raw.isBlank()) {
            return null;
        }

        Identifier exact = parseId(raw.toLowerCase(Locale.ROOT));
        if (exact != null && BuiltInRegistries.BLOCK.containsKey(exact)) {
            return exact;
        }

        Identifier normalized = parseId(normalizeIdentifierText(raw));
        if (normalized != null && BuiltInRegistries.BLOCK.containsKey(normalized)) {
            return normalized;
        }

        return null;
    }

    private Identifier parseId(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return Identifier.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
    }

    private List<Block> oreVariants(Identifier id, Block block) {
        Set<Block> variants = new LinkedHashSet<>();
        variants.add(block);

        if (!"minecraft".equals(id.getNamespace())) {
            return List.copyOf(variants);
        }

        String path = id.getPath();
        String counterpart = null;
        if (path.endsWith("_ore") && !path.startsWith("deepslate_")) {
            counterpart = "deepslate_" + path;
        } else if (path.startsWith("deepslate_") && path.endsWith("_ore")) {
            counterpart = path.substring("deepslate_".length());
        }

        if (counterpart != null) {
            Identifier counterpartId = Identifier.tryParse("minecraft:" + counterpart);
            if (counterpartId != null) {
                BuiltInRegistries.BLOCK.getOptional(counterpartId).ifPresent(variants::add);
            }
        }

        return List.copyOf(variants);
    }

    private void extractSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int height = MAX_SUGGESTIONS * SUGGESTION_ROW_HEIGHT;
        graphics.fill(boxX, suggestionsY, boxX + boxWidth, suggestionsY + height, VeyraUi.SURFACE);
        graphics.outline(boxX, suggestionsY, boxWidth, height, VeyraUi.EDGE);

        if (suggestions.isEmpty()) {
            VeyraUi.text(graphics, this.font, "No close blocks found", boxX + 8, suggestionsY + 7, 0xFF8795A5);
            return;
        }

        for (int index = 0; index < suggestions.size(); index++) {
            Suggestion suggestion = suggestions.get(index);
            int rowY = suggestionsY + (index * SUGGESTION_ROW_HEIGHT);
            boolean hovered = suggestionIndexAt(mouseX, mouseY) == index;
            boolean selected = selectedSuggestion == index;

            if (selected || hovered) {
                graphics.fill(boxX + 1, rowY + 1, boxX + boxWidth - 1, rowY + SUGGESTION_ROW_HEIGHT - 1,
                        selected ? 0x6638BDF8 : 0x552A313C);
            }

            String id = suggestion.id().toString();
            int idWidth = VeyraUi.width(this.font, id);
            int labelMaxWidth = idWidth < boxWidth / 2 ? boxWidth - idWidth - 22 : boxWidth - 12;
            String label = fitText(suggestion.label(), labelMaxWidth);

            VeyraUi.text(graphics, this.font, label, boxX + 7, rowY + 5, 0xFFFFFFFF);

            if (idWidth < boxWidth / 2) {
                VeyraUi.text(graphics, this.font, id, boxX + boxWidth - idWidth - 7, rowY + 5, 0xFF91A4B8);
            }
        }
    }

    private int suggestionIndexAt(double mouseX, double mouseY) {
        if (mouseX < boxX || mouseX > boxX + boxWidth || mouseY < suggestionsY) {
            return -1;
        }

        int index = (int) ((mouseY - suggestionsY) / SUGGESTION_ROW_HEIGHT);
        return index >= 0 && index < suggestions.size() ? index : -1;
    }

    private String fitText(String text, int maxWidth) {
        return VeyraUi.fit(this.font, text, maxWidth);
    }

    private int score(String raw, Identifier id, String label) {
        String query = normalizeSearchText(raw);
        String pathQuery = normalizeIdentifierText(raw);
        if (query.isEmpty() || pathQuery.isEmpty()) {
            return NO_MATCH;
        }

        String fullId = id.toString();
        String path = id.getPath();
        String pathWords = path.replace('_', ' ');
        String labelLower = label.toLowerCase(Locale.ROOT);

        if (fullId.equals(pathQuery) || path.equals(pathQuery)) {
            return 0;
        }
        if (path.startsWith(pathQuery)) {
            return 10 + path.length();
        }
        if (labelLower.startsWith(query)) {
            return 20 + label.length();
        }
        if (pathWords.startsWith(query)) {
            return 30 + pathWords.length();
        }

        int pathIndex = path.indexOf(pathQuery);
        if (pathIndex >= 0) {
            return 50 + pathIndex + path.length();
        }

        int labelIndex = labelLower.indexOf(query);
        if (labelIndex >= 0) {
            return 70 + labelIndex + label.length();
        }

        int fuzzyLabel = fuzzyScore(query, labelLower);
        if (fuzzyLabel < NO_MATCH) {
            return 120 + fuzzyLabel + label.length();
        }

        int fuzzyPath = fuzzyScore(pathQuery, path);
        if (fuzzyPath < NO_MATCH) {
            return 160 + fuzzyPath + path.length();
        }

        return NO_MATCH;
    }

    private int fuzzyScore(String query, String target) {
        int targetIndex = 0;
        int gaps = 0;

        for (int queryIndex = 0; queryIndex < query.length(); queryIndex++) {
            char expected = query.charAt(queryIndex);
            int foundAt = target.indexOf(expected, targetIndex);
            if (foundAt < 0) {
                return NO_MATCH;
            }

            gaps += foundAt - targetIndex;
            targetIndex = foundAt + 1;
        }

        return gaps;
    }

    private static String normalizeSearchText(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String normalizeIdentifierText(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        StringBuilder builder = new StringBuilder(lower.length());
        for (int index = 0; index < lower.length(); index++) {
            char value = lower.charAt(index);
            if ((value >= 'a' && value <= 'z')
                    || (value >= '0' && value <= '9')
                    || value == '_'
                    || value == '-'
                    || value == '.'
                    || value == ':'
                    || value == '/') {
                builder.append(value);
            }
        }
        return builder.toString();
    }

    private static String labelFor(Identifier id) {
        String[] words = id.getPath().split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private record Suggestion(Identifier id, Block block, String label, int score) {
    }
}
