/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.reflect.TypeToken
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.Entity
 *  net.minecraft.LightLayer
 *  net.minecraft.Blocks
 *  net.minecraft.BlockPos
 *  net.minecraft.BlockPos$MutableBlockPos
 *  net.minecraft.Direction
 *  net.minecraft.Position
 *  net.minecraft.Component
 *  net.minecraft.BlockEntity
 *  net.minecraft.PistonMovingBlockEntity
 *  net.minecraft.PistonHeadBlock
 *  net.minecraft.BlockState
 *  net.minecraft.PistonType
 *  net.minecraft.Property
 *  net.minecraft.Identifier
 *  net.minecraft.Minecraft
 *  net.minecraft.SectionPos
 *  net.minecraft.BuiltInRegistries
 */
package com.ruok0214.renderhide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ruok0214.renderhide.HiddenRegion;
import com.ruok0214.renderhide.RenderHideClient;
import java.io.BufferedReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;

@Environment(value=EnvType.CLIENT)
public final class RegionManager {
    public enum BlockRenderMode {
        NORMAL,
        TRANSLUCENT,
        SKIP
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type REGION_LIST = new TypeToken<List<HiddenRegion>>(){}.getType();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("renderhide-regions.json");
    private static final Path FILTER_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("renderhide-filters.json");
    private static final Path REGION_FILTER_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("renderhide-region-filters.json");
    private static final Path ENTITY_FILTER_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("renderhide-entity-filters.json");
    private static final Path REGION_ENTITY_FILTER_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("renderhide-region-entity-filters.json");
    private static final Path OPACITY_CONFIG = FabricLoader.getInstance().getConfigDir().resolve("renderhide-opacity.json");
    private static volatile List<HiddenRegion> snapshot = List.of();
    private static volatile String activeDimension = "";
    private static volatile boolean globallyEnabled = true;
    private static volatile boolean virtualLightEnabled = true;
    private static volatile float hiddenBlockOpacity = 0.0f;
    private static volatile Set<Identifier> visibleBlockFilters = Set.of();
    private static volatile Map<String, Set<Identifier>> regionVisibleBlockFilters = Map.of();
    private static volatile Set<Identifier> visibleEntityFilters = Set.of();
    private static volatile Map<String, Set<Identifier>> regionVisibleEntityFilters = Map.of();
    private static Object selectionLevel;
    private static BlockPos pos1;
    private static BlockPos pos2;

    private RegionManager() {
    }

    public static void load() {
        BufferedReader reader;
        if (Files.exists(CONFIG, new LinkOption[0])) {
            try {
                reader = Files.newBufferedReader(CONFIG);
                try {
                    List<HiddenRegion> loaded = GSON.fromJson(reader, REGION_LIST);
                    LinkedHashMap<String, HiddenRegion> valid = new LinkedHashMap<>();
                    if (loaded != null) {
                        for (HiddenRegion region : loaded) {
                            if (region == null || region.name() == null || region.name().isBlank()
                                    || region.dimension() == null || Identifier.tryParse(region.dimension()) == null
                                    || region.minX() > region.maxX() || region.minY() > region.maxY()
                                    || region.minZ() > region.maxZ()) {
                                RenderHideClient.LOGGER.warn("Ignoring invalid hidden region in configuration");
                                continue;
                            }
                            valid.putIfAbsent(region.name().toLowerCase(Locale.ROOT), region);
                        }
                    }
                    snapshot = List.copyOf(valid.values());
                }
                finally {
                    if (reader != null) {
                        ((Reader)reader).close();
                    }
                }
            }
            catch (Exception e) {
                RenderHideClient.LOGGER.error("Could not load hidden regions", (Throwable)e);
            }
        }
        if (Files.exists(FILTER_CONFIG, new LinkOption[0])) {
            try {
                reader = Files.newBufferedReader(FILTER_CONFIG);
                try {
                    List<String> loaded = GSON.fromJson(reader, new TypeToken<List<String>>(){}.getType());
                    LinkedHashSet<Identifier> filters = new LinkedHashSet<>();
                    if (loaded != null) {
                        for (String value : loaded) {
                            Identifier id = value == null ? null : Identifier.tryParse(value);
                            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) continue;
                            filters.add(id);
                        }
                    }
                    visibleBlockFilters = Set.copyOf(filters);
                }
                finally {
                    if (reader != null) {
                        ((Reader)reader).close();
                    }
                }
            }
            catch (Exception e) {
                RenderHideClient.LOGGER.error("Could not load visible block filters", (Throwable)e);
            }
        }
        if (Files.exists(REGION_FILTER_CONFIG, new LinkOption[0])) {
            try {
                reader = Files.newBufferedReader(REGION_FILTER_CONFIG);
                try {
                    Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
                    Map<String, List<String>> loaded2 = GSON.fromJson(reader, type);
                    LinkedHashMap<String, Set<Identifier>> result = new LinkedHashMap<>();
                    if (loaded2 != null) {
                        loaded2.forEach((name, values) -> {
                            LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>();
                            if (values != null) {
                                for (String value : values) {
                                    Identifier id = value == null ? null : Identifier.tryParse(value);
                                    if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) continue;
                                    filters.add(id);
                                }
                            }
                            if (!filters.isEmpty()) {
                                result.put(name.toLowerCase(Locale.ROOT), Set.copyOf(filters));
                            }
                        });
                    }
                    regionVisibleBlockFilters = Map.copyOf(result);
                }
                finally {
                    if (reader != null) {
                        ((Reader)reader).close();
                    }
                }
            }
            catch (Exception e) {
                RenderHideClient.LOGGER.error("Could not load region visible block filters", (Throwable)e);
            }
        }
        visibleEntityFilters = RegionManager.loadIdList(ENTITY_FILTER_CONFIG, true);
        regionVisibleEntityFilters = RegionManager.loadRegionIdMap(REGION_ENTITY_FILTER_CONFIG, true);
        if (Files.exists(OPACITY_CONFIG, new LinkOption[0])) {
            try {
                reader = Files.newBufferedReader(OPACITY_CONFIG);
                try {
                    Float loaded = GSON.fromJson(reader, Float.class);
                    if (loaded != null && Float.isFinite(loaded)) {
                        hiddenBlockOpacity = Math.max(0.0f, Math.min(1.0f, loaded));
                    }
                }
                finally {
                    if (reader != null) {
                        ((Reader)reader).close();
                    }
                }
            }
            catch (Exception e) {
                RenderHideClient.LOGGER.error("Could not load hidden block opacity", (Throwable)e);
            }
        }
    }

    public static void updateDimension(Minecraft client) {
        String dimension = client.level == null ? "" : client.level.dimension().identifier().toString();
        if (selectionLevel != client.level || !dimension.equals(activeDimension)) {
            MovingPistonStates.useLevel(client.level);
            ChangedBlockReveal.reset(client.level);
            selectionLevel = client.level;
            clearSelection();
            activeDimension = dimension;
        }
    }

    public static boolean isHidden(BlockPos pos, BlockState state) {
        if (!globallyEnabled) {
            return false;
        }
        if (RegionManager.isVisibleAt(pos, state)) {
            return false;
        }
        String dimension = activeDimension;
        boolean inside = false;
        for (HiddenRegion region : snapshot) {
            if (!region.contains(pos, dimension)) continue;
            inside = true;
        }
        return inside;
    }

    public static boolean shouldImproveVisibleBlockLighting(BlockPos pos) {
        if (!globallyEnabled || hiddenBlockOpacity >= 1.0f) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return false;
        }
        BlockState state = client.level.getBlockState(pos);
        String dimension = activeDimension;
        for (HiddenRegion region : snapshot) {
            if (!region.contains(pos, dimension) || !RegionManager.isVisibleFilterState(state, visibleBlockFilters) && !RegionManager.isVisibleFilterState(state, RegionManager.regionFilters(region.name()))) continue;
            return true;
        }
        return false;
    }

    public static boolean isInsideActiveRegion(BlockPos pos) {
        if (!globallyEnabled) {
            return false;
        }
        String dimension = activeDimension;
        for (HiddenRegion region : snapshot) {
            if (!region.contains(pos, dimension)) continue;
            return true;
        }
        return false;
    }

    public static boolean isVirtualLightAffected(BlockPos origin) {
        if (!virtualLightEnabled || !globallyEnabled || hiddenBlockOpacity >= 1.0f) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return false;
        }
        if (RegionManager.isInsideActiveRegion(origin)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = origin.relative(direction);
            if (!RegionManager.isHidden(neighbor, client.level.getBlockState(neighbor))) continue;
            return true;
        }
        return false;
    }

    public static boolean isVirtualLightAffected(BlockPos origin, Direction face) {
        if (!virtualLightEnabled || !globallyEnabled || hiddenBlockOpacity >= 1.0f) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return false;
        }
        if (RegionManager.isInsideActiveRegion(origin)) {
            return true;
        }
        if (face == null) {
            return RegionManager.isVirtualLightAffected(origin);
        }
        BlockPos neighbor = origin.relative(face);
        return RegionManager.isHidden(neighbor, client.level.getBlockState(neighbor));
    }

    public static int virtualLightLevel(LightLayer type, BlockPos origin, int original) {
        if (!RegionManager.isVirtualLightAffected(origin)) {
            return original;
        }
        return RegionManager.calculateVirtualLightLevel(type, origin, null, original);
    }

    public static int virtualLightLevel(LightLayer type, BlockPos origin, Direction face, int original) {
        if (!RegionManager.isVirtualLightAffected(origin, face)) {
            return original;
        }
        return RegionManager.calculateVirtualLightLevel(type, origin, face, original);
    }

    private static int calculateVirtualLightLevel(LightLayer type, BlockPos origin, Direction face, int original) {
        BlockState entryState;
        BlockPos entry;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return original;
        }
        int best = original;
        if (type == LightLayer.SKY && face != null && RegionManager.isHidden(entry = origin.relative(face), entryState = client.level.getBlockState(entry))) {
            BlockPos.MutableBlockPos skyCursor = entry.mutable();
            for (int distance = 0; distance <= 512; ++distance) {
                BlockState state = client.level.getBlockState((BlockPos)skyCursor);
                if (!RegionManager.isInsideActiveRegion((BlockPos)skyCursor)) {
                    if (!state.isAir() && state.isSolidRender() && state.getLightDampening() > 0) break;
                    int boundary = client.level.getBrightness(LightLayer.SKY, (BlockPos)skyCursor);
                    int turnDecay = face == Direction.UP ? 0 : 1;
                    best = Math.max(best, boundary - turnDecay);
                    break;
                }
                if (RegionManager.isVisibleAt((BlockPos)skyCursor, state) && !state.isAir()) break;
                skyCursor.move(Direction.UP);
            }
        }
        block1: for (Direction direction : Direction.values()) {
            BlockPos.MutableBlockPos cursor = origin.mutable();
            int searchDistance = type == LightLayer.SKY && direction == Direction.UP ? 512 : 15;
            for (int distance = 1; distance <= searchDistance; ++distance) {
                cursor.move(direction);
                BlockState state = client.level.getBlockState((BlockPos)cursor);
                boolean inRegion = RegionManager.isInsideActiveRegion((BlockPos)cursor);
                boolean filterVisible = RegionManager.isVisibleAt((BlockPos)cursor, state);
                if (type == LightLayer.BLOCK) {
                    best = Math.max(best, state.getLightEmission() - distance + 1);
                }
                if (!inRegion) {
                    int boundary = client.level.getBrightness(type, (BlockPos)cursor);
                    int decay = type == LightLayer.SKY && direction == Direction.UP ? 0 : distance - 1;
                    best = Math.max(best, boundary - decay);
                    continue block1;
                }
                if (filterVisible && !state.isAir()) continue block1;
            }
        }
        return Math.max(0, Math.min(15, best));
    }

    public static BlockPos getPos1() {
        return pos1;
    }

    public static BlockPos getPos2() {
        return pos2;
    }

    public static List<HiddenRegion> regions() {
        return snapshot;
    }

    public static boolean isGloballyEnabled() {
        return globallyEnabled;
    }

    public static boolean isVirtualLightEnabled() {
        return virtualLightEnabled;
    }

    public static float hiddenBlockOpacity() {
        return hiddenBlockOpacity;
    }

    public static BlockRenderMode blockRenderMode(BlockPos pos, BlockState state) {
        if (!RegionManager.isHidden(pos, state) || hiddenBlockOpacity >= 1.0f) {
            return BlockRenderMode.NORMAL;
        }
        return hiddenBlockOpacity <= 0.0f
                ? BlockRenderMode.SKIP
                : BlockRenderMode.TRANSLUCENT;
    }

    public static BlockRenderMode blockRenderMode(float opacity) {
        if (Float.isNaN(opacity) || opacity >= 1.0f) {
            return BlockRenderMode.NORMAL;
        }
        return opacity <= 0.0f ? BlockRenderMode.SKIP : BlockRenderMode.TRANSLUCENT;
    }

    /**
     * Returns true when vanilla occlusion must not discard the current block's
     * face.  Only one side of a NORMAL/TRANSLUCENT boundary is exposed, which
     * avoids two coplanar blended faces while keeping the filtered block closed.
     */
    public static boolean shouldExposeFace(BlockRenderMode current,
            BlockRenderMode neighbour) {
        return current == BlockRenderMode.NORMAL && neighbour != BlockRenderMode.NORMAL
                || current == BlockRenderMode.TRANSLUCENT
                        && neighbour == BlockRenderMode.SKIP;
    }

    public static float blockRenderOpacity(BlockPos pos, BlockState state) {
        return switch (RegionManager.blockRenderMode(pos, state)) {
            case NORMAL -> 1.0f;
            case TRANSLUCENT -> hiddenBlockOpacity;
            case SKIP -> 0.0f;
        };
    }

    public static float movingBlockRenderOpacity(BlockState state, BlockPos... positions) {
        if (!globallyEnabled || hiddenBlockOpacity >= 1.0f
                || RegionManager.isVisibleFilterState(state, visibleBlockFilters)) {
            return 1.0f;
        }

        boolean inside = false;
        String dimension = activeDimension;
        for (BlockPos pos : positions) {
            if (pos != null && ChangedBlockReveal.isRevealed(pos)) return 1.0f;
            if (pos == null) {
                continue;
            }
            for (HiddenRegion region : snapshot) {
                if (!region.contains(pos, dimension)) {
                    continue;
                }
                inside = true;
                if (RegionManager.isVisibleFilterState(state,
                        RegionManager.regionFilters(region.name()))) {
                    return 1.0f;
                }
            }
        }
        return inside ? hiddenBlockOpacity : 1.0f;
    }

    public static boolean affectsOcclusion(BlockPos pos, BlockState state) {
        return RegionManager.blockRenderMode(pos, state) != BlockRenderMode.NORMAL;
    }

    public static boolean isGhostRendered(BlockPos pos, BlockState state) {
        return RegionManager.blockRenderMode(pos, state) == BlockRenderMode.TRANSLUCENT;
    }

    public static boolean isFullyHidden(BlockPos pos, BlockState state) {
        return RegionManager.blockRenderMode(pos, state) == BlockRenderMode.SKIP;
    }

    public static Set<Identifier> visibleBlockFilters() {
        return visibleBlockFilters;
    }

    public static Set<Identifier> regionFilters(String regionName) {
        return regionVisibleBlockFilters.getOrDefault(regionName.toLowerCase(Locale.ROOT), Set.of());
    }

    public static Set<Identifier> visibleEntityFilters() {
        return visibleEntityFilters;
    }

    public static Set<Identifier> regionEntityFilters(String regionName) {
        return regionVisibleEntityFilters.getOrDefault(regionName.toLowerCase(Locale.ROOT), Set.of());
    }

    public static float entityRenderOpacity(Entity entity) {
        if (!globallyEnabled) {
            return 1.0f;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (visibleEntityFilters.contains(id)) {
            return 1.0f;
        }
        BlockPos pos = BlockPos.containing(entity.getBoundingBox().getCenter());
        boolean inside = false;
        for (HiddenRegion region : snapshot) {
            if (!region.contains(pos, activeDimension)) continue;
            inside = true;
            if (!RegionManager.regionEntityFilters(region.name()).contains(id)) continue;
            return 1.0f;
        }
        return inside ? hiddenBlockOpacity : 1.0f;
    }

    public static boolean isEntityHidden(Entity entity) {
        return RegionManager.entityRenderOpacity(entity) <= 0.0f;
    }

    public static boolean addVisibleEntityFilter(Identifier id) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return false;
        }
        LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>(visibleEntityFilters);
        if (!filters.add(id)) {
            return false;
        }
        visibleEntityFilters = Set.copyOf(filters);
        RegionManager.saveIdList(ENTITY_FILTER_CONFIG, visibleEntityFilters, "entity filters");
        RegionManager.message("Visible entity filter added: " + String.valueOf(id));
        return true;
    }

    public static boolean removeVisibleEntityFilter(Identifier id) {
        LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>(visibleEntityFilters);
        if (!filters.remove(id)) {
            return false;
        }
        visibleEntityFilters = Set.copyOf(filters);
        RegionManager.saveIdList(ENTITY_FILTER_CONFIG, visibleEntityFilters, "entity filters");
        RegionManager.message("Visible entity filter removed: " + String.valueOf(id));
        return true;
    }

    public static void clearVisibleEntityFilters() {
        int count = visibleEntityFilters.size();
        visibleEntityFilters = Set.of();
        RegionManager.saveIdList(ENTITY_FILTER_CONFIG, visibleEntityFilters, "entity filters");
        RegionManager.message("Visible entity filters cleared (" + count + ").");
    }

    public static int addVisibleFiltersBatch(List<Identifier> ids, boolean entities, String regionName) {
        boolean regional;
        boolean bl = regional = regionName != null;
        if (regional && RegionManager.find(regionName) == null) {
            return 0;
        }
        Set<Identifier> current = entities ? (regional ? RegionManager.regionEntityFilters(regionName) : visibleEntityFilters) : (regional ? RegionManager.regionFilters(regionName) : visibleBlockFilters);
        LinkedHashSet<Identifier> merged = new LinkedHashSet<Identifier>(current);
        for (Identifier id : ids) {
            boolean valid = entities ? BuiltInRegistries.ENTITY_TYPE.containsKey(id) : BuiltInRegistries.BLOCK.containsKey(id);
            if (!valid) continue;
            merged.add(id);
        }
        int added = merged.size() - current.size();
        if (added == 0) {
            return 0;
        }
        Set<Identifier> result = Set.copyOf(merged);
        if (entities && regional) {
            LinkedHashMap<String, Set<Identifier>> all = new LinkedHashMap<String, Set<Identifier>>(regionVisibleEntityFilters);
            all.put(regionName.toLowerCase(Locale.ROOT), result);
            regionVisibleEntityFilters = Map.copyOf(all);
            RegionManager.saveRegionIdMap(REGION_ENTITY_FILTER_CONFIG, regionVisibleEntityFilters, "region entity filters");
        } else if (entities) {
            visibleEntityFilters = result;
            RegionManager.saveIdList(ENTITY_FILTER_CONFIG, visibleEntityFilters, "entity filters");
        } else if (regional) {
            LinkedHashMap<String, Set<Identifier>> all = new LinkedHashMap<String, Set<Identifier>>(regionVisibleBlockFilters);
            all.put(regionName.toLowerCase(Locale.ROOT), result);
            regionVisibleBlockFilters = Map.copyOf(all);
            RegionManager.saveRegionFilters();
            RegionManager.refreshAll();
        } else {
            visibleBlockFilters = result;
            RegionManager.saveFilters();
            RegionManager.refreshAll();
        }
        return added;
    }

    public static boolean addRegionVisibleEntityFilter(String regionName, Identifier id) {
        if (RegionManager.find(regionName) == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return false;
        }
        String key = regionName.toLowerCase(Locale.ROOT);
        LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>(RegionManager.regionEntityFilters(key));
        if (!filters.add(id)) {
            return false;
        }
        LinkedHashMap<String, Set<Identifier>> all = new LinkedHashMap<String, Set<Identifier>>(regionVisibleEntityFilters);
        all.put(key, Set.copyOf(filters));
        regionVisibleEntityFilters = Map.copyOf(all);
        RegionManager.saveRegionIdMap(REGION_ENTITY_FILTER_CONFIG, regionVisibleEntityFilters, "region entity filters");
        RegionManager.message("Region entity filter added to " + regionName + ": " + String.valueOf(id));
        return true;
    }

    public static boolean removeRegionVisibleEntityFilter(String regionName, Identifier id) {
        String key = regionName.toLowerCase(Locale.ROOT);
        LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>(RegionManager.regionEntityFilters(key));
        if (!filters.remove(id)) {
            return false;
        }
        LinkedHashMap<String, Set<Identifier>> all = new LinkedHashMap<String, Set<Identifier>>(regionVisibleEntityFilters);
        if (filters.isEmpty()) {
            all.remove(key);
        } else {
            all.put(key, Set.copyOf(filters));
        }
        regionVisibleEntityFilters = Map.copyOf(all);
        RegionManager.saveRegionIdMap(REGION_ENTITY_FILTER_CONFIG, regionVisibleEntityFilters, "region entity filters");
        RegionManager.message("Region entity filter removed from " + regionName + ": " + String.valueOf(id));
        return true;
    }

    public static void clearRegionVisibleEntityFilters(String regionName) {
        String key = regionName.toLowerCase(Locale.ROOT);
        int count = RegionManager.regionEntityFilters(key).size();
        LinkedHashMap<String, Set<Identifier>> all = new LinkedHashMap<String, Set<Identifier>>(regionVisibleEntityFilters);
        all.remove(key);
        regionVisibleEntityFilters = Map.copyOf(all);
        RegionManager.saveRegionIdMap(REGION_ENTITY_FILTER_CONFIG, regionVisibleEntityFilters, "region entity filters");
        RegionManager.message("Region entity filters cleared for " + regionName + " (" + count + ").");
    }

    private static boolean isVisibleAt(BlockPos pos, BlockState state) {
        if (ChangedBlockReveal.isRevealed(pos)) return true;
        if (state.is(Blocks.MOVING_PISTON) && RegionManager.isMovingPushedBlockVisible(pos)) {
            return true;
        }
        if (RegionManager.isVisibleFilterState(state, visibleBlockFilters)) {
            return true;
        }
        String dimension = activeDimension;
        for (HiddenRegion region : snapshot) {
            if (!region.contains(pos, dimension) || !RegionManager.isVisibleFilterState(state, RegionManager.regionFilters(region.name()))) continue;
            return true;
        }
        return false;
    }

    private static boolean isMovingPushedBlockVisible(BlockPos pos) {
        // Chunk meshing also calls this method on Sodium/vanilla worker threads.
        // Never access the live LevelChunk block-entity map here.
        BlockState pushed = MovingPistonStates.get(Minecraft.getInstance().level, pos);
        if (pushed == null) {
            return false;
        }
        if (RegionManager.isVisibleFilterState(pushed, visibleBlockFilters)) {
            return true;
        }
        String dimension = activeDimension;
        for (HiddenRegion region : snapshot) {
            if (!region.contains(pos, dimension) || !RegionManager.isVisibleFilterState(pushed, RegionManager.regionFilters(region.name()))) continue;
            return true;
        }
        return false;
    }

    private static boolean isVisibleFilterState(BlockState state, Set<Identifier> filters) {
        Identifier stateId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (filters.contains(stateId)) {
            return true;
        }
        if (state.is(Blocks.PISTON_HEAD)) {
            PistonType type = state.getValue(PistonHeadBlock.TYPE);
            return type == PistonType.STICKY ? filters.contains(BuiltInRegistries.BLOCK.getKey(Blocks.STICKY_PISTON)) : filters.contains(BuiltInRegistries.BLOCK.getKey(Blocks.PISTON));
        }
        if (state.is(Blocks.MOVING_PISTON)) {
            return filters.contains(BuiltInRegistries.BLOCK.getKey(Blocks.PISTON)) || filters.contains(BuiltInRegistries.BLOCK.getKey(Blocks.STICKY_PISTON));
        }
        if (!stateId.getNamespace().equals("minecraft")) {
            return false;
        }
        String family = RegionManager.linkedFilterFamily(stateId.getPath());
        if (family == null) {
            return false;
        }
        for (Identifier filter : filters) {
            if (!filter.getNamespace().equals("minecraft") || !family.equals(RegionManager.linkedFilterFamily(filter.getPath()))) continue;
            return true;
        }
        return false;
    }

    private static String linkedFilterFamily(String path) {
        if (path.equals("wall_torch") || path.equals("torch")) {
            return "torch:normal";
        }
        if (path.endsWith("_wall_hanging_sign")) {
            return "hanging_sign:" + path.substring(0, path.length() - "_wall_hanging_sign".length());
        }
        if (path.endsWith("_hanging_sign")) {
            return "hanging_sign:" + path.substring(0, path.length() - "_hanging_sign".length());
        }
        if (path.endsWith("_wall_sign")) {
            return "sign:" + path.substring(0, path.length() - "_wall_sign".length());
        }
        if (path.endsWith("_sign")) {
            return "sign:" + path.substring(0, path.length() - "_sign".length());
        }
        if (path.endsWith("_wall_torch")) {
            return "torch:" + path.substring(0, path.length() - "_wall_torch".length());
        }
        if (path.endsWith("_torch")) {
            return "torch:" + path.substring(0, path.length() - "_torch".length());
        }
        if (path.endsWith("_wall_banner")) {
            return "banner:" + path.substring(0, path.length() - "_wall_banner".length());
        }
        if (path.endsWith("_banner")) {
            return "banner:" + path.substring(0, path.length() - "_banner".length());
        }
        if (path.endsWith("_wall_skull")) {
            return "skull:" + path.substring(0, path.length() - "_wall_skull".length());
        }
        if (path.endsWith("_skull")) {
            return "skull:" + path.substring(0, path.length() - "_skull".length());
        }
        if (path.endsWith("_wall_head")) {
            return "head:" + path.substring(0, path.length() - "_wall_head".length());
        }
        if (path.endsWith("_head")) {
            return "head:" + path.substring(0, path.length() - "_head".length());
        }
        if (path.equals("kelp") || path.equals("kelp_plant")) {
            return "plant:kelp";
        }
        if (path.equals("cave_vines") || path.equals("cave_vines_plant")) {
            return "plant:cave_vines";
        }
        if (path.equals("twisting_vines") || path.equals("twisting_vines_plant")) {
            return "plant:twisting_vines";
        }
        if (path.equals("weeping_vines") || path.equals("weeping_vines_plant")) {
            return "plant:weeping_vines";
        }
        if (path.equals("cauldron") || path.equals("water_cauldron") || path.equals("lava_cauldron") || path.equals("powder_snow_cauldron")) {
            return "content:cauldron";
        }
        if (path.equals("water") || path.equals("bubble_column")) {
            return "content:water";
        }
        if (path.equals("cake") || path.equals("candle_cake") || path.endsWith("_candle_cake")) {
            return "content:cake";
        }
        return null;
    }

    public static boolean addVisibleBlockFilter(Identifier id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            return false;
        }
        LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>(visibleBlockFilters);
        if (!filters.add(id)) {
            return false;
        }
        visibleBlockFilters = Set.copyOf(filters);
        RegionManager.saveFilters();
        RegionManager.refreshAll();
        RegionManager.message("Visible block filter added: " + String.valueOf(id));
        return true;
    }

    public static boolean addRegionVisibleBlockFilter(String regionName, Identifier id) {
        if (RegionManager.find(regionName) == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return false;
        }
        String key = regionName.toLowerCase(Locale.ROOT);
        LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>(RegionManager.regionFilters(key));
        if (!filters.add(id)) {
            return false;
        }
        LinkedHashMap<String, Set<Identifier>> all = new LinkedHashMap<String, Set<Identifier>>(regionVisibleBlockFilters);
        all.put(key, Set.copyOf(filters));
        regionVisibleBlockFilters = Map.copyOf(all);
        RegionManager.saveRegionFilters();
        RegionManager.refreshAll();
        RegionManager.message("Region filter added to " + regionName + ": " + String.valueOf(id));
        return true;
    }

    public static boolean removeRegionVisibleBlockFilter(String regionName, Identifier id) {
        String key = regionName.toLowerCase(Locale.ROOT);
        LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>(RegionManager.regionFilters(key));
        if (!filters.remove(id)) {
            return false;
        }
        LinkedHashMap<String, Set<Identifier>> all = new LinkedHashMap<String, Set<Identifier>>(regionVisibleBlockFilters);
        if (filters.isEmpty()) {
            all.remove(key);
        } else {
            all.put(key, Set.copyOf(filters));
        }
        regionVisibleBlockFilters = Map.copyOf(all);
        RegionManager.saveRegionFilters();
        RegionManager.refreshAll();
        RegionManager.message("Region filter removed from " + regionName + ": " + String.valueOf(id));
        return true;
    }

    public static void clearRegionVisibleBlockFilters(String regionName) {
        String key = regionName.toLowerCase(Locale.ROOT);
        LinkedHashMap<String, Set<Identifier>> all = new LinkedHashMap<String, Set<Identifier>>(regionVisibleBlockFilters);
        int count = RegionManager.regionFilters(key).size();
        all.remove(key);
        regionVisibleBlockFilters = Map.copyOf(all);
        RegionManager.saveRegionFilters();
        RegionManager.refreshAll();
        RegionManager.message("Region filters cleared for " + regionName + " (" + count + ").");
    }

    public static boolean removeVisibleBlockFilter(Identifier id) {
        LinkedHashSet<Identifier> filters = new LinkedHashSet<Identifier>(visibleBlockFilters);
        if (!filters.remove(id)) {
            return false;
        }
        visibleBlockFilters = Set.copyOf(filters);
        RegionManager.saveFilters();
        RegionManager.refreshAll();
        RegionManager.message("Visible block filter removed: " + String.valueOf(id));
        return true;
    }

    public static void clearVisibleBlockFilters() {
        int count = visibleBlockFilters.size();
        visibleBlockFilters = Set.of();
        RegionManager.saveFilters();
        RegionManager.refreshAll();
        RegionManager.message("Visible block filters cleared (" + count + ").");
    }

    public static void setPos1(BlockPos pos) {
        pos1 = pos.immutable();
        RegionManager.message("Position 1: " + RegionManager.format(pos1));
    }

    public static void setPos2(BlockPos pos) {
        pos2 = pos.immutable();
        RegionManager.message("Position 2: " + RegionManager.format(pos2));
    }

    public static boolean add(String requestedName) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || pos1 == null || pos2 == null) {
            RegionManager.message("Set both positions first.");
            return false;
        }
        String base = RegionManager.sanitizeName(requestedName == null || requestedName.isBlank() ? "region" : requestedName);
        String name = RegionManager.uniqueName(base);
        ArrayList<HiddenRegion> regions = new ArrayList<HiddenRegion>(snapshot);
        HiddenRegion region = HiddenRegion.create(name, client.level.dimension().identifier().toString(), pos1, pos2);
        regions.add(region);
        RegionManager.publish(regions, region);
        RegionManager.clearSelection();
        RegionManager.message("Hidden region added: " + name + " (" + region.blockCount() + " blocks)");
        return true;
    }

    public static boolean remove(String name) {
        ArrayList<HiddenRegion> regions = new ArrayList<HiddenRegion>(snapshot);
        HiddenRegion found = RegionManager.find(name);
        if (found == null) {
            return false;
        }
        regions.remove(found);
        LinkedHashMap<String, Set<Identifier>> filters = new LinkedHashMap<String, Set<Identifier>>(regionVisibleBlockFilters);
        filters.remove(found.name().toLowerCase(Locale.ROOT));
        regionVisibleBlockFilters = Map.copyOf(filters);
        RegionManager.saveRegionFilters();
        LinkedHashMap<String, Set<Identifier>> entityFilters = new LinkedHashMap<String, Set<Identifier>>(regionVisibleEntityFilters);
        entityFilters.remove(found.name().toLowerCase(Locale.ROOT));
        regionVisibleEntityFilters = Map.copyOf(entityFilters);
        RegionManager.saveRegionIdMap(REGION_ENTITY_FILTER_CONFIG, regionVisibleEntityFilters, "region entity filters");
        RegionManager.publish(regions, found);
        RegionManager.message("Removed region: " + found.name());
        return true;
    }

    public static boolean rename(String oldName, String requestedName) {
        HiddenRegion current = find(oldName);
        String name = requestedName == null ? "" : requestedName.trim();
        if (current == null || name.isEmpty() || name.length() > 64
                || name.chars().anyMatch(Character::isISOControl)) {
            message("Enter a region name (1-64 characters).");
            return false;
        }
        HiddenRegion duplicate = find(name);
        if (duplicate != null && duplicate != current) {
            message("A region with that name already exists.");
            return false;
        }
        String oldKey = current.name().toLowerCase(Locale.ROOT);
        String newKey = name.toLowerCase(Locale.ROOT);
        ArrayList<HiddenRegion> regions = new ArrayList<>(snapshot);
        regions.set(regions.indexOf(current), new HiddenRegion(name, current.dimension(),
                current.minX(), current.minY(), current.minZ(),
                current.maxX(), current.maxY(), current.maxZ(), current.enabled()));
        LinkedHashMap<String, Set<Identifier>> blocks = new LinkedHashMap<>(regionVisibleBlockFilters);
        LinkedHashMap<String, Set<Identifier>> entities = new LinkedHashMap<>(regionVisibleEntityFilters);
        Set<Identifier> blockIds = blocks.remove(oldKey);
        Set<Identifier> entityIds = entities.remove(oldKey);
        if (blockIds != null) blocks.put(newKey, blockIds);
        if (entityIds != null) entities.put(newKey, entityIds);
        regionVisibleBlockFilters = Map.copyOf(blocks);
        regionVisibleEntityFilters = Map.copyOf(entities);
        snapshot = List.copyOf(regions);
        save();
        saveRegionFilters();
        saveRegionIdMap(REGION_ENTITY_FILTER_CONFIG, regionVisibleEntityFilters, "region entity filters");
        refreshAll();
        message("Region renamed: " + current.name() + " -> " + name);
        return true;
    }

    public static boolean toggle(String name) {
        ArrayList<HiddenRegion> regions = new ArrayList<HiddenRegion>(snapshot);
        for (int i = 0; i < regions.size(); ++i) {
            HiddenRegion current = regions.get(i);
            if (!current.name().equalsIgnoreCase(name)) continue;
            HiddenRegion changed = current.withEnabled(!current.enabled());
            regions.set(i, changed);
            RegionManager.publish(regions, current);
            RegionManager.message(changed.name() + ": " + (changed.enabled() ? "hidden" : "visible"));
            return true;
        }
        return false;
    }

    public static boolean updateCoordinates(String name, BlockPos first, BlockPos second) {
        ArrayList<HiddenRegion> regions = new ArrayList<HiddenRegion>(snapshot);
        for (int i = 0; i < regions.size(); ++i) {
            HiddenRegion current = regions.get(i);
            if (!current.name().equalsIgnoreCase(name)) continue;
            HiddenRegion changed = new HiddenRegion(current.name(), current.dimension(), Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()), Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()), current.enabled());
            regions.set(i, changed);
            snapshot = List.copyOf(regions);
            RegionManager.save();
            RegionManager.refreshAll();
            RegionManager.message("Region coordinates updated: " + current.name());
            return true;
        }
        return false;
    }

    public static void clear() {
        List<HiddenRegion> old = snapshot;
        snapshot = List.of();
        regionVisibleBlockFilters = Map.of();
        regionVisibleEntityFilters = Map.of();
        RegionManager.save();
        RegionManager.saveRegionFilters();
        RegionManager.saveRegionIdMap(REGION_ENTITY_FILTER_CONFIG, regionVisibleEntityFilters, "region entity filters");
        RegionManager.refreshAll();
        RegionManager.message("All hidden regions cleared (" + old.size() + ").");
    }

    public static void toggleGlobal() {
        globallyEnabled = !globallyEnabled;
        RegionManager.refreshAll();
        RegionManager.message("Render hiding: " + (globallyEnabled ? "ON" : "OFF"));
    }

    public static void toggleVirtualLight() {
        virtualLightEnabled = !virtualLightEnabled;
        RegionManager.refreshAll();
        RegionManager.message("Virtual light: " + (virtualLightEnabled ? "ON" : "OFF"));
    }

    public static void setHiddenBlockOpacity(double opacity) {
        if (!Double.isFinite(opacity)) return;
        float changed = (float)Math.round((float)Math.max(0.0, Math.min(1.0, opacity)) * 20.0f) / 20.0f;
        if (Math.abs(changed - hiddenBlockOpacity) < 1.0E-4f) {
            return;
        }
        hiddenBlockOpacity = changed;
        try {
            Files.createDirectories(OPACITY_CONFIG.getParent(), new FileAttribute[0]);
            writeConfig(OPACITY_CONFIG, GSON.toJson((Object)Float.valueOf(hiddenBlockOpacity)));
        }
        catch (Exception e) {
            RenderHideClient.LOGGER.error("Could not save hidden block opacity", (Throwable)e);
        }
        RegionManager.refreshAll();
    }

    public static void clearSelection() {
        pos1 = null;
        pos2 = null;
    }

    public static HiddenRegion find(String name) {
        for (HiddenRegion region : snapshot) {
            if (!region.name().equalsIgnoreCase(name)) continue;
            return region;
        }
        return null;
    }

    private static String uniqueName(String base) {
        if (RegionManager.find(base) == null) {
            return base;
        }
        int index = 2;
        while (RegionManager.find(base + "-" + index) != null) {
            ++index;
        }
        return base + "-" + index;
    }

    private static String sanitizeName(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return cleaned.isBlank() ? "region" : cleaned;
    }

    private static void publish(List<HiddenRegion> regions, HiddenRegion changed) {
        snapshot = List.copyOf(regions);
        RegionManager.save();
        RegionManager.refresh(changed);
    }

    private static void writeConfig(Path path, String json) throws java.io.IOException {
        Path temporary = Files.createTempFile(path.getParent(), "renderhide-", ".tmp");
        try {
            Files.writeString(temporary, json);
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG.getParent(), new FileAttribute[0]);
            writeConfig(CONFIG, GSON.toJson(snapshot, REGION_LIST));
        }
        catch (Exception e) {
            RenderHideClient.LOGGER.error("Could not save hidden regions", (Throwable)e);
        }
    }

    private static void saveFilters() {
        try {
            Files.createDirectories(FILTER_CONFIG.getParent(), new FileAttribute[0]);
            writeConfig(FILTER_CONFIG, GSON.toJson(visibleBlockFilters.stream().map(Identifier::toString).sorted().toList()));
        }
        catch (Exception e) {
            RenderHideClient.LOGGER.error("Could not save visible block filters", (Throwable)e);
        }
    }

    private static void saveRegionFilters() {
        try {
            Files.createDirectories(REGION_FILTER_CONFIG.getParent(), new FileAttribute[0]);
            LinkedHashMap<String, List<String>> serialized = new LinkedHashMap<>();
            regionVisibleBlockFilters.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> serialized.put(entry.getKey(), entry.getValue().stream().map(Identifier::toString).sorted().toList()));
            writeConfig(REGION_FILTER_CONFIG, GSON.toJson(serialized));
        }
        catch (Exception e) {
            RenderHideClient.LOGGER.error("Could not save region visible block filters", (Throwable)e);
        }
    }

    private static Set<Identifier> loadIdList(Path path, boolean entityIds) {
        if (!Files.exists(path)) {
            return Set.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
                List<String> loaded = GSON.fromJson(reader, new TypeToken<List<String>>(){}.getType());
                LinkedHashSet<Identifier> result = new LinkedHashSet<>();
                if (loaded != null) {
                    for (String value : loaded) {
                        Identifier id = value == null ? null : Identifier.tryParse(value);
                        if (id == null || !(entityIds ? BuiltInRegistries.ENTITY_TYPE.containsKey(id) : BuiltInRegistries.BLOCK.containsKey(id))) continue;
                        result.add(id);
                    }
                }
                return Set.copyOf(result);
        } catch (Exception e) {
            RenderHideClient.LOGGER.error("Could not load filters from " + path, e);
            return Set.of();
        }
    }

    private static Map<String, Set<Identifier>> loadRegionIdMap(Path path, boolean entityIds) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
                Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
                Map<String, List<String>> loaded = GSON.fromJson(reader, type);
                LinkedHashMap<String, Set<Identifier>> result = new LinkedHashMap<>();
                if (loaded != null) {
                    loaded.forEach((name, values) -> {
                        LinkedHashSet<Identifier> ids = new LinkedHashSet<Identifier>();
                        if (values != null) {
                            for (String value : values) {
                                Identifier id = value == null ? null : Identifier.tryParse(value);
                                if (id == null || !(entityIds ? BuiltInRegistries.ENTITY_TYPE.containsKey(id) : BuiltInRegistries.BLOCK.containsKey(id))) continue;
                                ids.add(id);
                            }
                        }
                        if (!ids.isEmpty()) {
                            result.put(name.toLowerCase(Locale.ROOT), Set.copyOf(ids));
                        }
                    });
                }
                return Map.copyOf(result);
        } catch (Exception e) {
            RenderHideClient.LOGGER.error("Could not load region filters from " + path, e);
            return Map.of();
        }
    }

    private static void saveIdList(Path path, Set<Identifier> ids, String description) {
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            writeConfig(path, GSON.toJson(ids.stream().map(Identifier::toString).sorted().toList()));
        }
        catch (Exception e) {
            RenderHideClient.LOGGER.error("Could not save " + description, (Throwable)e);
        }
    }

    private static void saveRegionIdMap(Path path, Map<String, Set<Identifier>> values, String description) {
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            LinkedHashMap<String, List<String>> serialized = new LinkedHashMap<>();
            values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> serialized.put(entry.getKey(), entry.getValue().stream().map(Identifier::toString).sorted().toList()));
            writeConfig(path, GSON.toJson(serialized));
        }
        catch (Exception e) {
            RenderHideClient.LOGGER.error("Could not save " + description, (Throwable)e);
        }
    }

    private static void refresh(HiddenRegion region) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.levelExtractor == null) {
            return;
        }
        if (!region.dimension().equals(client.level.dimension().identifier().toString())) {
            return;
        }
        client.levelExtractor.setBlocksDirty(region.minX() - 1, region.minY() - 1, region.minZ() - 1,
                region.maxX() + 1, region.maxY() + 1, region.maxZ() + 1);
    }

    public static void refreshAll() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.levelExtractor != null) {
            // In 26.2 this is the public entry point for a full renderer reload.
            // Calling LevelRenderer#invalidateCompiledGeometry directly rebuilds
            // renderer objects, but skips LevelExtractor's section update tracker.
            // That leaves already compiled hidden/transparent meshes cached after
            // filters change or render hiding is disabled.
            client.levelExtractor.allChanged();
        }
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public static void message(String value) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Render Hide] " + value));
        }
    }
}
