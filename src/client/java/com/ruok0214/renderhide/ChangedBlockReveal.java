package com.ruok0214.renderhide;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Main-thread writes, immutable keys and safe render-worker lookups. */
public final class ChangedBlockReveal {
    private record Settings(boolean enabled, double seconds) {}
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("renderhide-changed-blocks.json");
    private static volatile Settings settings = new Settings(false, 2.0);
    private static volatile ClientLevel level;
    private static final ConcurrentHashMap<BlockPos, Long> until = new ConcurrentHashMap<>();
    private static final Set<BlockPos> dirtySections = new HashSet<>();
    private static long tick;
    private ChangedBlockReveal() {}

    public static void load() {
        if (!Files.exists(CONFIG)) return;
        try {
            Settings loaded = new Gson().fromJson(Files.readString(CONFIG), Settings.class);
            if (loaded != null && Double.isFinite(loaded.seconds()) && loaded.seconds() >= 0.1 && loaded.seconds() <= 60)
                settings = loaded;
        } catch (Exception e) { RenderHideClient.LOGGER.error("Could not load changed-block reveal settings", e); }
    }
    public static boolean enabled() { return settings.enabled(); }
    public static double seconds() { return settings.seconds(); }
    public static boolean isRevealed(BlockPos pos) { return settings.enabled() && until.containsKey(pos); }

    public static void configure(boolean enabled, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > 60) return;
        until.keySet().forEach(ChangedBlockReveal::dirty);
        until.clear();
        settings = new Settings(enabled, seconds);
        try {
            Files.createDirectories(CONFIG.getParent());
            Path temp = Files.createTempFile(CONFIG.getParent(), "renderhide-change-", ".tmp");
            try {
                Files.writeString(temp, new Gson().toJson(settings));
                try { Files.move(temp, CONFIG, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, CONFIG, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception e) { RenderHideClient.LOGGER.error("Could not save changed-block reveal settings", e); }
        flush(Minecraft.getInstance());
    }
    public static void reset(ClientLevel next) {
        until.clear(); dirtySections.clear(); tick = 0; level = next;
    }
    public static void changed(Level world, BlockPos pos, BlockState before, BlockState after) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || world != client.level || !(world instanceof ClientLevel next)
                || before == null || before == after || !enabled()
                || !RegionManager.isInsideActiveRegion(pos) || RegionManager.hiddenBlockOpacity() >= 1) return;
        if (level != next) reset(next);
        if (after.isAir()) {
            if (until.remove(pos) != null) dirty(pos);
            return;
        }
        Long previous = until.put(pos.immutable(), tick + Math.max(1, (long)Math.ceil(seconds() * 20)));
        if (previous == null) dirty(pos);
    }
    public static void removeChunk(ClientLevel world, ChunkPos chunk) {
        if (level == world) until.keySet().removeIf(p -> (p.getX() >> 4) == chunk.x() && (p.getZ() >> 4) == chunk.z());
    }
    public static void tick(Minecraft client) {
        if (level != client.level) reset(client.level);
        if (client.level == null || client.isPaused()) return;
        ++tick;
        until.forEach((pos, expiry) -> {
            if ((!RegionManager.isGloballyEnabled() || expiry <= tick) && until.remove(pos, expiry)) dirty(pos);
        });
        flush(client);
    }
    private static void dirty(BlockPos pos) {
        dirtySections.add(new BlockPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
    }
    private static void flush(Minecraft client) {
        if (client.level == level && client.levelExtractor != null) {
            for (BlockPos section : dirtySections) {
                int x = section.getX() << 4, y = section.getY() << 4, z = section.getZ() << 4;
                client.levelExtractor.setBlocksDirty(x - 1, y - 1, z - 1, x + 16, y + 16, z + 16);
            }
        }
        dirtySections.clear();
    }
}
