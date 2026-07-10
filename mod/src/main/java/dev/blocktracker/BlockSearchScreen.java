package dev.blocktracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
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
    private static final int SUGGESTION_ROW_HEIGHT = 20;
    private static final int QUEUE_ROW_HEIGHT = 46;
    private static final int NO_MATCH = 10_000;
    private static List<SearchEntry> searchIndex;
    private final Screen parent;

    private EditBox input;
    private Component status = Component.translatable("message.blocktracker.enter_block");
    private List<Suggestion> suggestions = List.of();
    private int selectedSuggestion = -1;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int searchX;
    private int searchWidth;
    private int queueX;
    private int queueWidth;
    private int queueRowsY;
    private int inputY;
    private int suggestionsY;
    private int buttonY;
    private int statusY;

    public BlockSearchScreen() {
        this(null);
    }

    public BlockSearchScreen(Screen parent) {
        super(Component.translatable("screen.blocktracker.search"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(680, this.width - 28);
        panelHeight = Math.min(330, this.height - 28);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        queueWidth = Math.min(212, Math.max(176, panelWidth / 3));
        searchX = panelX + 18;
        searchWidth = panelWidth - queueWidth - 54;
        queueX = searchX + searchWidth + 18;
        inputY = panelY + 82;
        suggestionsY = inputY + 28;
        buttonY = suggestionsY + (MAX_SUGGESTIONS * SUGGESTION_ROW_HEIGHT) + 8;
        statusY = buttonY + 30;
        queueRowsY = panelY + 82;

        input = new EditBox(this.font, searchX + 10, inputY + 2, searchWidth - 20, 18, Component.literal("Block ID"));
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
        Minecraft client = Minecraft.getInstance();
        BlockTrackerState.addTarget(id, targets, null);
        BlockScan.request(client, id, targets, BlockTrackerState.blockScanRadius());
        closeScreen();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (VeyraKeybinds.cancelBlockSearchMatches(event)) {
            BlockTrackerState.clear();
            closeScreen();
            return true;
        }

        if (VeyraKeybinds.openSearchMatches(event)) {
            closeScreen();
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
        if (VeyraKeybinds.openSearchMatches(event)) {
            closeScreen();
            return true;
        }

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int queuedIndex = queuedRemoveIndexAt(event.x(), event.y());
            if (queuedIndex >= 0) {
                Identifier removed = BlockTrackerState.blockTargets().get(queuedIndex).id();
                BlockTrackerState.removeBlockTarget(queuedIndex);
                status = Component.literal("Removed " + removed.getPath().replace('_', ' '));
                return true;
            }

            int index = suggestionIndexAt(event.x(), event.y());
            if (index >= 0) {
                selectedSuggestion = index;
                input.setValue(suggestions.get(index).id().toString());
                submit();
                return true;
            }

            if (event.x() >= searchX && event.x() <= searchX + searchWidth && event.y() >= buttonY && event.y() <= buttonY + 24) {
                submit();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        VeyraUi.screenBackground(graphics, this.width, this.height, true);
        List<BlockTrackerState.BlockTarget> queued = BlockTrackerState.blockTargets();
        VeyraUi.shell(
                graphics,
                this.font,
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                "Block scanner",
                "Queue targets, watch scan progress, and remove them instantly",
                queued.size() + " / " + BlockTrackerState.maxBlockTargets() + " ACTIVE"
        );
        VeyraUi.sectionLabel(graphics, this.font, "Search registry", searchX, panelY + 62, searchWidth);
        VeyraUi.sectionLabel(graphics, this.font, "Queued blocks", queueX, panelY + 62, queueWidth);
        VeyraUi.card(graphics, searchX, inputY, searchWidth, 22, true);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        extractSuggestions(graphics, mouseX, mouseY);
        VeyraUi.button(graphics, searchX, buttonY, searchWidth, 24, true,
                mouseX >= searchX && mouseX <= searchX + searchWidth && mouseY >= buttonY && mouseY <= buttonY + 24);
        VeyraUi.centeredText(graphics, this.font, "QUEUE BLOCK TARGET", searchX + searchWidth / 2, buttonY + 8, VeyraUi.TEXT);
        VeyraUi.centeredText(graphics, this.font, status.getString(), searchX + searchWidth / 2, statusY, VeyraUi.MUTED);
        extractQueue(graphics, queued, mouseX, mouseY);

        String hint = "ENTER queue  •  ↑↓ select  •  TAB complete  •  " + VeyraKeybinds.searchKeyName() + " close";
        VeyraUi.text(graphics, this.font, hint, panelX + 18, panelY + panelHeight - 18, VeyraUi.SUBTLE);
    }

    private void updateSuggestions(String raw) {
        String query = normalizeSearchText(raw);
        String pathQuery = normalizeIdentifierText(raw);
        if (query.isEmpty()) {
            suggestions = List.of();
            selectedSuggestion = -1;
            status = Component.translatable("message.blocktracker.enter_block");
            return;
        }

        List<Suggestion> matches = new ArrayList<>();
        for (SearchEntry entry : searchIndex()) {
            int score = score(query, pathQuery, entry);
            if (score < NO_MATCH) {
                matches.add(new Suggestion(entry.id(), entry.block(), entry.label(), score));
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
        VeyraUi.card(graphics, searchX, suggestionsY, searchWidth, height, false);

        if (suggestions.isEmpty()) {
            String empty = input == null || input.getValue().isBlank() ? "Start typing a block name or identifier" : "No matching blocks";
            VeyraUi.text(graphics, this.font, empty, searchX + 9, suggestionsY + 8, VeyraUi.MUTED);
            return;
        }

        for (int index = 0; index < suggestions.size(); index++) {
            Suggestion suggestion = suggestions.get(index);
            int rowY = suggestionsY + (index * SUGGESTION_ROW_HEIGHT);
            boolean hovered = suggestionIndexAt(mouseX, mouseY) == index;
            boolean selected = selectedSuggestion == index;

            if (selected || hovered) {
                graphics.fill(searchX + 1, rowY + 1, searchX + searchWidth - 1, rowY + SUGGESTION_ROW_HEIGHT - 1,
                        selected ? VeyraUi.withAlpha(VeyraUi.ACCENT, 0x66) : VeyraUi.SURFACE_HOVER);
            }

            String id = suggestion.id().toString();
            int idWidth = VeyraUi.width(this.font, id);
            int labelMaxWidth = idWidth < searchWidth / 2 ? searchWidth - idWidth - 22 : searchWidth - 12;
            String label = fitText(suggestion.label(), labelMaxWidth);

            VeyraUi.text(graphics, this.font, label, searchX + 8, rowY + 6, VeyraUi.TEXT);

            if (idWidth < searchWidth / 2) {
                VeyraUi.text(graphics, this.font, id, searchX + searchWidth - idWidth - 8, rowY + 6, VeyraUi.SUBTLE);
            }
        }
    }

    private int suggestionIndexAt(double mouseX, double mouseY) {
        if (mouseX < searchX || mouseX > searchX + searchWidth || mouseY < suggestionsY) {
            return -1;
        }

        int index = (int) ((mouseY - suggestionsY) / SUGGESTION_ROW_HEIGHT);
        return index >= 0 && index < suggestions.size() ? index : -1;
    }

    private void extractQueue(
            GuiGraphicsExtractor graphics,
            List<BlockTrackerState.BlockTarget> queued,
            int mouseX,
            int mouseY
    ) {
        if (queued.isEmpty()) {
            VeyraUi.card(graphics, queueX, queueRowsY, queueWidth, 76, false);
            VeyraUi.centeredText(graphics, this.font, "QUEUE EMPTY", queueX + queueWidth / 2, queueRowsY + 19, VeyraUi.MUTED);
            VeyraUi.centeredText(graphics, this.font, "Tracked blocks appear here", queueX + queueWidth / 2, queueRowsY + 40, VeyraUi.SUBTLE);
            return;
        }

        for (int index = 0; index < queued.size(); index++) {
            BlockTrackerState.BlockTarget target = queued.get(index);
            int rowY = queueRowsY + index * (QUEUE_ROW_HEIGHT + 7);
            boolean removeHover = queuedRemoveIndexAt(mouseX, mouseY) == index;
            VeyraUi.card(graphics, queueX, rowY, queueWidth, QUEUE_ROW_HEIGHT, false);

            int color = targetColor(index);
            graphics.fill(queueX, rowY + 6, queueX + 3, rowY + QUEUE_ROW_HEIGHT - 6, color);
            graphics.fill(queueX + 10, rowY + 9, queueX + 18, rowY + 17, VeyraUi.withAlpha(color, 0xCC));

            int closeX = queueX + queueWidth - 25;
            graphics.fill(closeX, rowY + 8, closeX + 17, rowY + 25, removeHover ? 0xCC7F2432 : 0x88351D25);
            graphics.outline(closeX, rowY + 8, 17, 17, removeHover ? VeyraUi.BAD : VeyraUi.withAlpha(VeyraUi.BAD, 0x66));
            VeyraUi.centeredText(graphics, this.font, "X", closeX + 8, rowY + 13, removeHover ? 0xFFFFFFFF : VeyraUi.BAD);

            String label = labelFor(target.id());
            VeyraUi.text(graphics, this.font, VeyraUi.fit(this.font, label, queueWidth - 58), queueX + 23, rowY + 8, VeyraUi.TEXT);
            VeyraUi.text(graphics, this.font, queueStatus(target), queueX + 10, rowY + 29, VeyraUi.MUTED);
        }
    }

    private int queuedRemoveIndexAt(double mouseX, double mouseY) {
        List<BlockTrackerState.BlockTarget> queued = BlockTrackerState.blockTargets();
        int closeX = queueX + queueWidth - 25;
        if (mouseX < closeX || mouseX > closeX + 17) {
            return -1;
        }

        for (int index = 0; index < queued.size(); index++) {
            int rowY = queueRowsY + index * (QUEUE_ROW_HEIGHT + 7);
            if (mouseY >= rowY + 8 && mouseY <= rowY + 25) {
                return index;
            }
        }
        return -1;
    }

    private String queueStatus(BlockTrackerState.BlockTarget target) {
        BlockScan.ScanResults results = target.results();
        if (results == null) {
            return "WAITING FOR WORLD";
        }
        if (!results.complete()) {
            return results.matchCount() + " FOUND  •  " + results.progressPercent() + "%";
        }
        return results.matchCount() + (results.matchCount() == 1 ? " MATCH" : " MATCHES");
    }

    private int targetColor(int index) {
        return switch (index) {
            case 1 -> VeyraUi.TEAL;
            case 2 -> 0xFFC084FC;
            default -> VeyraUi.ACCENT;
        };
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private void closeScreen() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private String fitText(String text, int maxWidth) {
        return VeyraUi.fit(this.font, text, maxWidth);
    }

    private int score(String query, String pathQuery, SearchEntry entry) {
        if (query.isEmpty() || pathQuery.isEmpty()) {
            return NO_MATCH;
        }

        if (entry.fullId().equals(pathQuery) || entry.path().equals(pathQuery)) {
            return 0;
        }
        if (entry.path().startsWith(pathQuery)) {
            return 10 + entry.path().length();
        }
        if (entry.labelLower().startsWith(query)) {
            return 20 + entry.label().length();
        }
        if (entry.pathWords().startsWith(query)) {
            return 30 + entry.pathWords().length();
        }

        int pathIndex = entry.path().indexOf(pathQuery);
        if (pathIndex >= 0) {
            return 50 + pathIndex + entry.path().length();
        }

        int labelIndex = entry.labelLower().indexOf(query);
        if (labelIndex >= 0) {
            return 70 + labelIndex + entry.label().length();
        }

        int fuzzyLabel = fuzzyScore(query, entry.labelLower());
        if (fuzzyLabel < NO_MATCH) {
            return 120 + fuzzyLabel + entry.label().length();
        }

        int fuzzyPath = fuzzyScore(pathQuery, entry.path());
        if (fuzzyPath < NO_MATCH) {
            return 160 + fuzzyPath + entry.path().length();
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

    private static List<SearchEntry> searchIndex() {
        if (searchIndex != null) {
            return searchIndex;
        }

        List<SearchEntry> entries = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
            if (block.isEmpty()) {
                continue;
            }

            String label = labelFor(id);
            String path = id.getPath();
            entries.add(new SearchEntry(
                    id,
                    block.get(),
                    label,
                    label.toLowerCase(Locale.ROOT),
                    id.toString(),
                    path,
                    path.replace('_', ' ')
            ));
        }

        searchIndex = List.copyOf(entries);
        return searchIndex;
    }

    private record Suggestion(Identifier id, Block block, String label, int score) {
    }

    private record SearchEntry(
            Identifier id,
            Block block,
            String label,
            String labelLower,
            String fullId,
            String path,
            String pathWords
    ) {
    }
}
