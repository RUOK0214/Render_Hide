/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.KeyEvent
 *  net.minecraft.BlockPos
 *  net.minecraft.Component
 *  net.minecraft.Identifier
 *  net.minecraft.GuiGraphics
 *  net.minecraft.EditBox
 *  net.minecraft.AbstractSliderButton
 *  net.minecraft.GuiEventListener
 *  net.minecraft.Button
 *  net.minecraft.Screen
 *  net.minecraft.BuiltInRegistries
 */
package com.ruok0214.renderhide;

import com.ruok0214.renderhide.HiddenRegion;
import com.ruok0214.renderhide.RegionManager;
import com.ruok0214.renderhide.SelectionOverlayRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;

@Environment(value=EnvType.CLIENT)
public final class RenderHideScreen
extends Screen {
    private static final int ROWS = 6;
    private int regionPage;
    private int filterPage;
    private EditBox regionName;
    private EditBox filterId;
    private String rememberedRegionName = "region";
    private String rememberedFilterId = "";
    private Identifier filterSuggestion;
    private boolean editingSelection;
    private String editingRegion;
    private boolean editingRegionFilters;
    private boolean editingGlobalBlockFilters;
    private boolean editingEntityFilters;
    private boolean entityFiltersForRegion;
    private int regionFilterPage;
    private final EditBox[] coordinates = new EditBox[6];

    public RenderHideScreen() {
        super((Component)Component.literal((String)"Render Hide"));
    }

    protected void init() {
        this.filterId = null;
        this.filterSuggestion = null;
        this.addRenderableWidget(Button.builder(Component.literal("나가기"), b ->
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(null))
                .bounds(Math.max(0, this.width - 78), 4, 70, 20).build());
        if (this.editingEntityFilters) {
            this.initEntityFilterEditor();
            return;
        }
        if (this.editingGlobalBlockFilters || this.editingRegionFilters && this.editingRegion != null) {
            this.initRegionFilterEditor();
            return;
        }
        if (this.editingSelection || this.editingRegion != null) {
            this.initCoordinateEditor();
            return;
        }
        int panelWidth = Math.min(470, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        int gap = 8;
        int columnWidth = (panelWidth - gap) / 2;
        this.addRenderableWidget(Button.builder((Component)RenderHideScreen.toggleText("Hiding", RegionManager.isGloballyEnabled()), b -> {
            RegionManager.toggleGlobal();
            this.rebuild();
        }).bounds(left, 28, (panelWidth - 2 * gap) / 3, 20).build());
        this.addRenderableWidget(Button.builder((Component)RenderHideScreen.toggleText("Virtual light", RegionManager.isVirtualLightEnabled()), b -> {
            RegionManager.toggleVirtualLight();
            this.rebuild();
        }).bounds(left + (panelWidth + gap) / 3, 28, (panelWidth - 2 * gap) / 3, 20).build());
        this.addRenderableWidget(Button.builder((Component)RenderHideScreen.toggleText("Outlines", SelectionOverlayRenderer.areSavedRegionsShown()), b -> {
            SelectionOverlayRenderer.toggleSavedRegions();
            this.rebuild();
        }).bounds(left + 2 * (panelWidth + gap) / 3, 28, (panelWidth - 2 * gap) / 3, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)("Global block filters (" + RegionManager.visibleBlockFilters().size() + ")")), b -> {
            this.editingGlobalBlockFilters = true;
            this.rememberedFilterId = "";
            this.regionFilterPage = 0;
            this.rebuild();
        }).bounds(left, 55, columnWidth, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)("Global entity filters (" + RegionManager.visibleEntityFilters().size() + ")")), b -> {
            this.editingEntityFilters = true;
            this.entityFiltersForRegion = false;
            this.rememberedFilterId = "";
            this.regionFilterPage = 0;
            this.rebuild();
        }).bounds(left + columnWidth + gap, 55, columnWidth, 20).build());
        this.addRenderableWidget(new OpacitySlider(left, 81, panelWidth, 20));
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Edit coords"), b -> {
            this.editingSelection = true;
            this.rebuild();
        }).bounds(left, 107, columnWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("renderhide.changed.title"), b ->
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new ChangedBlockRevealScreen(this)))
                .bounds(left + columnWidth + gap, 107, columnWidth, 20).build());
        this.regionName = new EditBox(this.font, left, 143, panelWidth - 64, 20, (Component)Component.literal((String)"Region name"));
        this.regionName.setMaxLength(32);
        this.regionName.setHint((Component)Component.literal((String)"region name"));
        this.regionName.setValue(this.rememberedRegionName);
        this.regionName.setResponder(value -> {
            this.rememberedRegionName = value;
        });
        this.addRenderableWidget(this.regionName);
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Add"), b -> {
            if (RegionManager.add(this.regionName.getValue())) {
                this.rebuild();
            }
        }).bounds(left + panelWidth - 60, 143, 60, 20).build());
        List<HiddenRegion> regions = RegionManager.regions();
        this.regionPage = RenderHideScreen.clampPage(this.regionPage, regions.size());
        int regionStart = this.regionPage * 6;
        for (int row = 0; row < 6 && regionStart + row < regions.size(); ++row) {
            HiddenRegion region = regions.get(regionStart + row);
            int y = 170 + row * 23;
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)(region.name())), b -> {
                this.editingRegion = region.name();
                this.rebuild();
            }).bounds(left, y, panelWidth - 64, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal(region.enabled() ? "ON" : "OFF"), b -> {
                RegionManager.toggle(region.name());
                this.rebuild();
            }).bounds(left + panelWidth - 60, y, 60, 20).build());
        }
        int navY = 311;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u2039"), b -> {
            if (this.regionPage > 0) {
                --this.regionPage;
                this.rebuild();
            }
        }).bounds(left, navY, 28, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u203a"), b -> {
            if ((this.regionPage + 1) * 6 < regions.size()) {
                ++this.regionPage;
                this.rebuild();
            }
        }).bounds(left + panelWidth - 28, navY, 28, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Done"), b -> this.onClose()).bounds((this.width - 120) / 2, 338, 120, 20).build());
    }

    private void updateFilterSuggestion(String value) {
        this.rememberedFilterId = value;
        String query = value.trim().toLowerCase(Locale.ROOT);
        this.filterSuggestion = null;
        this.filterId.setSuggestion(null);
        if (query.isEmpty()) {
            return;
        }
        boolean qualified = query.indexOf(58) >= 0;
        Set<Identifier> ids = this.editingEntityFilters ? BuiltInRegistries.ENTITY_TYPE.keySet() : BuiltInRegistries.BLOCK.keySet();
        for (Identifier id : ids.stream().sorted().toList()) {
            String candidate = qualified ? id.toString() : id.getPath();
            if (!candidate.startsWith(query) || candidate.equals(query)) continue;
            this.filterSuggestion = id;
            this.filterId.setSuggestion(candidate.substring(query.length()));
            return;
        }
    }

    public boolean keyPressed(KeyEvent input) {
        if (!this.editingSelection && this.filterId != null && this.filterId.isFocused()) {
            if (input.key() == 258 && this.filterSuggestion != null) {
                this.filterId.setValue(this.filterSuggestion.toString());
                return true;
            }
            if (input.key() == 257 || input.key() == 335) {
                this.addFilter();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    private void initCoordinateEditor() {
        BlockPos second;
        BlockPos first;
        HiddenRegion region;
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        HiddenRegion hiddenRegion = region = this.editingRegion == null ? null : RegionManager.find(this.editingRegion);
        if (this.editingSelection) {
            first = RegionManager.getPos1();
            second = RegionManager.getPos2();
        } else if (region != null) {
            first = new BlockPos(region.minX(), region.minY(), region.minZ());
            second = new BlockPos(region.maxX(), region.maxY(), region.maxZ());
        } else {
            this.editingRegion = null;
            this.rebuild();
            return;
        }
        int[] values = new int[]{first == null ? 0 : first.getX(), first == null ? 0 : first.getY(), first == null ? 0 : first.getZ(), second == null ? 0 : second.getX(), second == null ? 0 : second.getY(), second == null ? 0 : second.getZ()};
        String[] labels = new String[]{"X1", "Y1", "Z1", "X2", "Y2", "Z2"};
        for (int i = 0; i < 6; ++i) {
            int column = i % 3;
            int row = i / 3;
            int x = left + column * 130;
            int y = 82 + row * 48;
            this.coordinates[i] = new EditBox(this.font, x, y, 120, 20, (Component)Component.literal((String)labels[i]));
            this.coordinates[i].setMaxLength(12);
            this.coordinates[i].setValue(Integer.toString(values[i]));
            this.addRenderableWidget(this.coordinates[i]);
        }
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Save coordinates"), b -> this.saveCoordinates()).bounds(left, 187, panelWidth, 20).build());
        if (region != null) {
            EditBox renameBox = new EditBox(this.font, left, 160, panelWidth - 84, 20, Component.literal("Region name"));
            renameBox.setMaxLength(64);
            renameBox.setValue(region.name());
            this.addRenderableWidget(renameBox);
            this.addRenderableWidget(Button.builder(Component.literal("Rename"), b -> {
                if (RegionManager.rename(this.editingRegion, renameBox.getValue())) {
                    this.editingRegion = renameBox.getValue().trim();
                    this.rebuild();
                }
            }).bounds(left + panelWidth - 80, 160, 80, 20).build());
            this.addRenderableWidget(Button.builder((Component)RenderHideScreen.toggleText("Region", region.enabled()), b -> {
                RegionManager.toggle(region.name());
                this.rebuild();
            }).bounds(left, 214, 188, 20).build());
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Delete region"), b -> {
                RegionManager.remove(region.name());
                this.editingRegion = null;
                this.rebuild();
            }).bounds(left + panelWidth - 188, 214, 188, 20).build());
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)("Region filters (" + RegionManager.regionFilters(region.name()).size() + ")")), b -> {
                this.editingRegionFilters = true;
                this.rememberedFilterId = "";
                this.rebuild();
            }).bounds(left, 241, panelWidth, 20).build());
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)("Region entity filters (" + RegionManager.regionEntityFilters(region.name()).size() + ")")), b -> {
                this.editingEntityFilters = true;
                this.entityFiltersForRegion = true;
                this.rememberedFilterId = "";
                this.rebuild();
            }).bounds(left, 268, panelWidth, 20).build());
        } else {
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Clear selection"), b -> {
                RegionManager.clearSelection();
                this.rebuild();
            }).bounds(left, 214, panelWidth, 20).build());
        }
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Back"), b -> {
            this.editingSelection = false;
            this.editingRegion = null;
            this.rebuild();
        }).bounds((this.width - 120) / 2, region == null ? 251 : 295, 120, 20).build());
    }

    private void initEntityFilterEditor() {
        HiddenRegion region;
        HiddenRegion hiddenRegion = region = this.entityFiltersForRegion && this.editingRegion != null ? RegionManager.find(this.editingRegion) : null;
        if (this.entityFiltersForRegion && region == null) {
            this.editingEntityFilters = false;
            this.entityFiltersForRegion = false;
            this.editingRegion = null;
            this.rebuild();
            return;
        }
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        this.filterId = new EditBox(this.font, left, 68, panelWidth - 64, 20, (Component)Component.literal((String)"Entity ID"));
        this.filterId.setMaxLength(128);
        this.filterId.setHint((Component)Component.literal((String)"minecraft:minecart"));
        this.filterId.setValue(this.rememberedFilterId);
        this.filterId.setResponder(this::updateFilterSuggestion);
        this.addRenderableWidget(this.filterId);
        this.updateFilterSuggestion(this.rememberedFilterId);
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Add"), b -> this.addFilter()).bounds(left + panelWidth - 60, 68, 60, 20).build());
        ArrayList<Identifier> filters = new ArrayList<Identifier>(this.entityFiltersForRegion ? RegionManager.regionEntityFilters(this.editingRegion) : RegionManager.visibleEntityFilters());
        filters.sort(Comparator.comparing(Identifier::toString));
        this.regionFilterPage = RenderHideScreen.clampPage(this.regionFilterPage, filters.size());
        int start = this.regionFilterPage * 6;
        for (int row = 0; row < 6 && start + row < filters.size(); ++row) {
            Identifier id = (Identifier)filters.get(start + row);
            int y = 98 + row * 23;
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)id.toString()), b -> {}).bounds(left, y, panelWidth - 27, 20).build());
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00d7"), b -> {
                if (this.entityFiltersForRegion) {
                    RegionManager.removeRegionVisibleEntityFilter(this.editingRegion, id);
                } else {
                    RegionManager.removeVisibleEntityFilter(id);
                }
                this.rebuild();
            }).bounds(left + panelWidth - 24, y, 24, 20).build());
        }
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u2039"), b -> {
            if (this.regionFilterPage > 0) {
                --this.regionFilterPage;
                this.rebuild();
            }
        }).bounds(left, 239, 28, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Clear entity filters"), b -> {
            if (this.entityFiltersForRegion) {
                RegionManager.clearRegionVisibleEntityFilters(this.editingRegion);
            } else {
                RegionManager.clearVisibleEntityFilters();
            }
            this.rebuild();
        }).bounds(left + 32, 239, panelWidth - 64, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u203a"), b -> {
            if ((this.regionFilterPage + 1) * 6 < filters.size()) {
                ++this.regionFilterPage;
                this.rebuild();
            }
        }).bounds(left + panelWidth - 28, 239, 28, 20).build());
        this.addCopyPasteButtons(left, 266, panelWidth);
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)(this.entityFiltersForRegion ? "Back to region" : "Back")), b -> {
            this.editingEntityFilters = false;
            this.rememberedFilterId = "";
            if (!this.entityFiltersForRegion) {
                this.editingRegion = null;
            }
            this.entityFiltersForRegion = false;
            this.rebuild();
        }).bounds((this.width - 140) / 2, 293, 140, 20).build());
    }

    private void initRegionFilterEditor() {
        HiddenRegion region;
        HiddenRegion hiddenRegion = region = this.editingRegionFilters ? RegionManager.find(this.editingRegion) : null;
        if (this.editingRegionFilters && region == null) {
            this.editingRegionFilters = false;
            this.editingRegion = null;
            this.rebuild();
            return;
        }
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        this.filterId = new EditBox(this.font, left, 68, panelWidth - 64, 20, (Component)Component.literal((String)"Block ID"));
        this.filterId.setMaxLength(128);
        this.filterId.setHint((Component)Component.literal((String)"minecraft:stone"));
        this.filterId.setValue(this.rememberedFilterId);
        this.filterId.setResponder(this::updateFilterSuggestion);
        this.addRenderableWidget(this.filterId);
        this.updateFilterSuggestion(this.rememberedFilterId);
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Add"), b -> this.addFilter()).bounds(left + panelWidth - 60, 68, 60, 20).build());
        ArrayList<Identifier> filters = new ArrayList<Identifier>(this.editingRegionFilters ? RegionManager.regionFilters(region.name()) : RegionManager.visibleBlockFilters());
        filters.sort(Comparator.comparing(Identifier::toString));
        this.regionFilterPage = RenderHideScreen.clampPage(this.regionFilterPage, filters.size());
        int start = this.regionFilterPage * 6;
        for (int row = 0; row < 6 && start + row < filters.size(); ++row) {
            Identifier id = (Identifier)filters.get(start + row);
            int y = 98 + row * 23;
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)id.toString()), b -> {}).bounds(left, y, panelWidth - 27, 20).build());
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00d7"), b -> {
                if (this.editingRegionFilters) {
                    RegionManager.removeRegionVisibleBlockFilter(region.name(), id);
                } else {
                    RegionManager.removeVisibleBlockFilter(id);
                }
                this.rebuild();
            }).bounds(left + panelWidth - 24, y, 24, 20).build());
        }
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u2039"), b -> {
            if (this.regionFilterPage > 0) {
                --this.regionFilterPage;
                this.rebuild();
            }
        }).bounds(left, 239, 28, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)(this.editingRegionFilters ? "Clear region filters" : "Clear global filters")), b -> {
            if (this.editingRegionFilters) {
                RegionManager.clearRegionVisibleBlockFilters(region.name());
            } else {
                RegionManager.clearVisibleBlockFilters();
            }
            this.rebuild();
        }).bounds(left + 32, 239, panelWidth - 64, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u203a"), b -> {
            if ((this.regionFilterPage + 1) * 6 < filters.size()) {
                ++this.regionFilterPage;
                this.rebuild();
            }
        }).bounds(left + panelWidth - 28, 239, 28, 20).build());
        this.addCopyPasteButtons(left, 266, panelWidth);
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)(this.editingRegionFilters ? "Back to region" : "Back")), b -> {
            this.editingRegionFilters = false;
            this.editingGlobalBlockFilters = false;
            this.rememberedFilterId = "";
            this.rebuild();
        }).bounds((this.width - 140) / 2, 293, 140, 20).build());
    }

    private void addCopyPasteButtons(int left, int y, int width) {
        int gap = 6;
        int buttonWidth = (width - gap) / 2;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Copy filters"), b -> this.copyCurrentFilters()).bounds(left, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Paste filters"), b -> this.pasteCurrentFilters()).bounds(left + buttonWidth + gap, y, buttonWidth, 20).build());
    }

    private Set<Identifier> currentFilters() {
        if (this.editingEntityFilters) {
            return this.entityFiltersForRegion ? RegionManager.regionEntityFilters(this.editingRegion) : RegionManager.visibleEntityFilters();
        }
        if (this.editingRegionFilters && this.editingRegion != null) {
            return RegionManager.regionFilters(this.editingRegion);
        }
        return RegionManager.visibleBlockFilters();
    }

    private void copyCurrentFilters() {
        boolean entities = this.editingEntityFilters;
        StringBuilder text = new StringBuilder("# Render Hide ").append(entities ? "entity" : "block").append(" filters\n");
        this.currentFilters().stream().sorted(Comparator.comparing(Identifier::toString)).forEach(id -> text.append(id).append('\n'));
        this.minecraft.keyboardHandler.setClipboard(text.toString());
        RegionManager.message("Copied " + this.currentFilters().size() + " " + (entities ? "entity" : "block") + " filters.");
    }

    private void pasteCurrentFilters() {
        boolean entities = this.editingEntityFilters;
        String clipboard = this.minecraft.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            RegionManager.message("Clipboard is empty.");
            return;
        }
        String lower = clipboard.toLowerCase(Locale.ROOT);
        if (lower.contains("# render hide entity filters") && !entities || lower.contains("# render hide block filters") && entities) {
            RegionManager.message("Clipboard contains the wrong filter type.");
            return;
        }
        Set<Identifier> before = this.currentFilters();
        ArrayList<Identifier> valid = new ArrayList<Identifier>();
        int invalid = 0;
        int duplicates = 0;
        LinkedHashSet<Identifier> seen = new LinkedHashSet<Identifier>();
        for (String token : clipboard.split("[\\s,;]+")) {
            String value = token.trim();
            if (value.isEmpty() || value.startsWith("#") || value.equalsIgnoreCase("Render") || value.equalsIgnoreCase("Hide") || value.equalsIgnoreCase("block") || value.equalsIgnoreCase("entity") || value.equalsIgnoreCase("filters")) continue;
            Identifier id = Identifier.tryParse((String)value);
            boolean exists = id != null && (entities ? BuiltInRegistries.ENTITY_TYPE.containsKey(id) : BuiltInRegistries.BLOCK.containsKey(id));
            if (!exists) {
                ++invalid;
                continue;
            }
            if (before.contains(id) || !seen.add(id)) {
                ++duplicates;
                continue;
            }
            valid.add(id);
        }
        String region = this.editingEntityFilters ? (this.entityFiltersForRegion ? this.editingRegion : null) : (this.editingRegionFilters ? this.editingRegion : null);
        int added = RegionManager.addVisibleFiltersBatch(valid, entities, region);
        RegionManager.message("Pasted filters: " + added + " added, " + duplicates + " duplicates, " + invalid + " invalid.");
        if (added > 0) {
            this.rebuild();
        }
    }

    private void saveCoordinates() {
        try {
            int[] value = new int[6];
            for (int i = 0; i < 6; ++i) {
                value[i] = Integer.parseInt(this.coordinates[i].getValue());
            }
            BlockPos first = new BlockPos(value[0], value[1], value[2]);
            BlockPos second = new BlockPos(value[3], value[4], value[5]);
            if (this.editingSelection) {
                RegionManager.setPos1(first);
                RegionManager.setPos2(second);
            } else if (this.editingRegion != null) {
                RegionManager.updateCoordinates(this.editingRegion, first, second);
            }
            this.rebuild();
        }
        catch (NumberFormatException e) {
            RegionManager.message("Enter all six coordinates as whole numbers.");
        }
    }

    private void addFilter() {
        boolean added;
        String value = this.filterId.getValue().trim();
        Identifier id = Identifier.tryParse((String)value);
        boolean valid = id != null && (this.editingEntityFilters ? BuiltInRegistries.ENTITY_TYPE.containsKey(id) : BuiltInRegistries.BLOCK.containsKey(id));
        if (!valid) {
            RegionManager.message("Unknown " + (this.editingEntityFilters ? "entity" : "block") + ": " + value);
            return;
        }
        if (this.editingEntityFilters) {
            added = this.entityFiltersForRegion ? RegionManager.addRegionVisibleEntityFilter(this.editingRegion, id) : RegionManager.addVisibleEntityFilter(id);
        } else {
            boolean bl2 = added = this.editingRegionFilters && this.editingRegion != null ? RegionManager.addRegionVisibleBlockFilter(this.editingRegion, id) : RegionManager.addVisibleBlockFilter(id);
        }
        if (added) {
            this.rememberedFilterId = "";
            this.rebuild();
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private static int clampPage(int page, int size) {
        return Math.max(0, Math.min(page, Math.max(0, (size - 1) / 6)));
    }

    private static Component toggleText(String name, boolean enabled) {
        return Component.literal((String)(name + ": " + (enabled ? "ON" : "OFF")));
    }

    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        if (this.editingEntityFilters) {
            this.renderEntityFilterEditor(context, mouseX, mouseY, deltaTicks);
            return;
        }
        if (this.editingGlobalBlockFilters || this.editingRegionFilters && this.editingRegion != null) {
            this.renderRegionFilterEditor(context, mouseX, mouseY, deltaTicks);
            return;
        }
        if (this.editingSelection || this.editingRegion != null) {
            this.renderCoordinateEditor(context, mouseX, mouseY, deltaTicks);
            return;
        }
        int panelWidth = Math.min(470, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        int gap = 8;
        int columnWidth = (panelWidth - gap) / 2;
        context.fill(left - 6, 8, left + panelWidth + 6, 367, -1341124592);
        context.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        context.text(this.font, RenderHideScreen.positionText("Position 1", RegionManager.getPos1()), left, 80, 0xD0D0D0);
        context.text(this.font, RenderHideScreen.positionText("Position 2", RegionManager.getPos2()), left, 92, 0xD0D0D0);
        context.text(this.font, "Saved regions", left, 132, 0xFFFFFF);
        context.centeredText(this.font, this.regionPage + 1 + "/" + Math.max(1, (RegionManager.regions().size() + 6 - 1) / 6), this.width / 2, 317, 0xA0A0A0);
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    private void renderCoordinateEditor(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        context.fill(left - 8, 18, left + panelWidth + 8, this.editingRegion == null ? 282 : 327, -1341124592);
        String heading = this.editingSelection ? "Current selection" : "Region: " + this.editingRegion;
        context.centeredText(this.font, heading, this.width / 2, 29, 0xFFFFFF);
        context.centeredText(this.font, this.editingSelection ? "Edit both corners of the red selection" : "Edit the saved region bounds", this.width / 2, 47, 0xB0B0B0);
        context.text(this.font, "Position 1", left, 67, 0xFFFFFF);
        context.text(this.font, "Position 2", left, 115, 0xFFFFFF);
        String[] labels = new String[]{"X", "Y", "Z", "X", "Y", "Z"};
        for (int i = 0; i < 6; ++i) {
            int x = left + i % 3 * 130;
            int y = i < 3 ? 72 : 120;
            context.text(this.font, labels[i], x + 2, y, 0xA0A0A0);
        }
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    private void renderRegionFilterEditor(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        int count = this.editingRegionFilters ? RegionManager.regionFilters(this.editingRegion).size() : RegionManager.visibleBlockFilters().size();
        context.fill(left - 8, 18, left + panelWidth + 8, 324, -1341124592);
        context.centeredText(this.font, this.editingRegionFilters ? "Region block filters: " + this.editingRegion : "Global block filters", this.width / 2, 29, 0xFFFFFF);
        context.centeredText(this.font, this.editingRegionFilters ? "Added to global filters \u2022 Tab to complete" : "Visible exceptions \u2022 Tab to complete", this.width / 2, 47, 0xB0B0B0);
        context.centeredText(this.font, this.regionFilterPage + 1 + "/" + Math.max(1, (count + 6 - 1) / 6), this.width / 2, 245, 0xA0A0A0);
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    private void renderEntityFilterEditor(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int panelWidth = Math.min(390, this.width - 20);
        int left = (this.width - panelWidth) / 2;
        int count = this.entityFiltersForRegion ? RegionManager.regionEntityFilters(this.editingRegion).size() : RegionManager.visibleEntityFilters().size();
        context.fill(left - 8, 18, left + panelWidth + 8, 324, -1341124592);
        String heading = this.entityFiltersForRegion ? "Region entity filters: " + this.editingRegion : "Global entity filters";
        context.centeredText(this.font, heading, this.width / 2, 29, 0xFFFFFF);
        context.centeredText(this.font, "Visible exceptions \u2022 Tab to complete", this.width / 2, 47, 0xB0B0B0);
        context.centeredText(this.font, this.regionFilterPage + 1 + "/" + Math.max(1, (count + 6 - 1) / 6), this.width / 2, 245, 0xA0A0A0);
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    private Component selectionText() {
        BlockPos a = RegionManager.getPos1();
        BlockPos b = RegionManager.getPos2();
        return Component.literal((String)("Selection: " + (a == null ? "pos1 unset" : RenderHideScreen.shortPos(a)) + " / " + (b == null ? "pos2 unset" : RenderHideScreen.shortPos(b))));
    }

    private static Component positionText(String label, BlockPos pos) {
        return Component.literal((String)(label + ": " + (pos == null ? "unset" : RenderHideScreen.shortPos(pos))));
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Environment(value=EnvType.CLIENT)
    private static final class OpacitySlider
    extends AbstractSliderButton {
        private OpacitySlider(int x, int y, int width, int height) {
            super(x, y, width, height, (Component)Component.empty(), (double)RegionManager.hiddenBlockOpacity());
            this.updateMessage();
        }

        protected void updateMessage() {
            this.setMessage((Component)Component.literal((String)("Hidden block opacity: " + Math.round(RegionManager.hiddenBlockOpacity() * 100.0) + "%")));
        }

        protected void applyValue() {
            RegionManager.setHiddenBlockOpacity(this.value);
            this.updateMessage();
        }
    }
}
