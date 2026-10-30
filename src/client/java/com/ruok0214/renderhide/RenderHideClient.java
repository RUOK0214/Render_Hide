/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.brigadier.suggestion.Suggestions
 *  com.mojang.brigadier.suggestion.SuggestionsBuilder
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
 *  net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
 *  net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
 *  net.minecraft.HitResult
 *  net.minecraft.HitResult$Type
 *  net.minecraft.Component
 *  net.minecraft.Identifier
 *  net.minecraft.KeyMapping
 *  net.minecraft.KeyMapping$Category
 *  net.minecraft.Minecraft
 *  net.minecraft.InputConstants$Type
 *  net.minecraft.BlockHitResult
 *  net.minecraft.Screen
 *  net.minecraft.BuiltInRegistries
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.ruok0214.renderhide;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.ruok0214.renderhide.HiddenRegion;
import com.ruok0214.renderhide.RegionManager;
import com.ruok0214.renderhide.RenderHideScreen;
import com.ruok0214.renderhide.SelectionOverlayRenderer;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(value=EnvType.CLIENT)
public final class RenderHideClient
implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"renderhide");
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.MISC;
    private static KeyMapping toggleKey;
    private static KeyMapping pos1Key;
    private static KeyMapping pos2Key;
    private static KeyMapping addKey;
    private static KeyMapping savedOutlinesKey;
    private static KeyMapping virtualLightKey;
    private static KeyMapping guiKey;

    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.register(
                (level, chunk) -> {
                    MovingPistonStates.removeChunk(level, chunk.getPos());
                    ChangedBlockReveal.removeChunk(level, chunk.getPos());
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> { MovingPistonStates.useLevel(null); ChangedBlockReveal.reset(null); });
        RegionManager.load();
        ChangedBlockReveal.load();
        SelectionOverlayRenderer.register();
        this.registerKeys();
        this.registerCommands();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        LOGGER.info("Render Hide {} initialized by RUOK0214",
                net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("renderhide")
                        .orElseThrow().getMetadata().getVersion().getFriendlyString());
    }

    private void registerKeys() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.renderhide.toggle", 297, CATEGORY));
        pos1Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.renderhide.pos1", 298, CATEGORY));
        pos2Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.renderhide.pos2", 299, CATEGORY));
        addKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.renderhide.add", 296, CATEGORY));
        savedOutlinesKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.renderhide.saved_outlines", 295, CATEGORY));
        virtualLightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.renderhide.virtual_light", -1, CATEGORY));
        guiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.renderhide.gui", 72, CATEGORY));
    }

    private void onTick(Minecraft client) {
        RegionManager.updateDimension(client);
        ChangedBlockReveal.tick(client);
        while (toggleKey.consumeClick()) {
            RegionManager.toggleGlobal();
        }
        while (pos1Key.consumeClick()) {
            RenderHideClient.setLookedAt(true);
        }
        while (pos2Key.consumeClick()) {
            RenderHideClient.setLookedAt(false);
        }
        while (addKey.consumeClick()) {
            RegionManager.add("region");
        }
        while (savedOutlinesKey.consumeClick()) {
            SelectionOverlayRenderer.toggleSavedRegions();
        }
        while (virtualLightKey.consumeClick()) {
            RegionManager.toggleVirtualLight();
        }
        while (guiKey.consumeClick()) {
            if (client.gui.screen() instanceof RenderHideScreen) {
                client.setScreenAndShow(null);
                continue;
            }
            client.setScreenAndShow(new RenderHideScreen());
        }
    }

    private static boolean setLookedAt(boolean first) {
        BlockHitResult hit;
        Minecraft client = Minecraft.getInstance();
        HitResult result = client.hitResult;
        if (!(result instanceof BlockHitResult) || (hit = (BlockHitResult)result).getType() != HitResult.Type.BLOCK) {
            RegionManager.message("Look at a block first.");
            return false;
        }
        if (first) {
            RegionManager.setPos1(hit.getBlockPos());
        } else {
            RegionManager.setPos2(hit.getBlockPos());
        }
        return true;
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal("renderhide")
                .then(ClientCommands.literal("e_filter")
                    .then(ClientCommands.literal("add")
                        .executes(ctx -> lookedAtEntityFilter())
                        .then(ClientCommands.argument("entity", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestEntities(builder, false))
                            .executes(ctx -> entityFilter(StringArgumentType.getString(ctx, "entity"), true))))
                    .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("entity", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestEntities(builder, true))
                            .executes(ctx -> entityFilter(StringArgumentType.getString(ctx, "entity"), false))))
                    .then(ClientCommands.literal("list").executes(ctx -> {
                        RegionManager.message("Visible entity filters: " + RegionManager.visibleEntityFilters());
                        return 1;
                    }))
                    .then(ClientCommands.literal("clear").executes(ctx -> {
                        RegionManager.clearVisibleEntityFilters();
                        return 1;
                    })))));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal((String)"renderhide").executes(ctx -> RenderHideClient.help((FabricClientCommandSource)ctx.getSource()))).then(ClientCommands.literal((String)"pos1").executes(ctx -> RenderHideClient.setLookedAt(true) ? 1 : 0))).then(ClientCommands.literal((String)"pos2").executes(ctx -> RenderHideClient.setLookedAt(false) ? 1 : 0))).then(((LiteralArgumentBuilder)ClientCommands.literal((String)"add").executes(ctx -> RegionManager.add("region") ? 1 : 0)).then(ClientCommands.argument((String)"name", (ArgumentType)StringArgumentType.string()).executes(ctx -> RegionManager.add(StringArgumentType.getString((CommandContext)ctx, (String)"name")) ? 1 : 0)))).then(ClientCommands.literal((String)"remove").then(ClientCommands.argument((String)"name", (ArgumentType)StringArgumentType.string()).suggests((ctx, builder) -> RenderHideClient.suggestNames(builder)).executes(ctx -> RenderHideClient.result(RegionManager.remove(StringArgumentType.getString((CommandContext)ctx, (String)"name")), "Region not found."))))).then(ClientCommands.literal((String)"toggle").then(ClientCommands.argument((String)"name", (ArgumentType)StringArgumentType.string()).suggests((ctx, builder) -> RenderHideClient.suggestNames(builder)).executes(ctx -> RenderHideClient.result(RegionManager.toggle(StringArgumentType.getString((CommandContext)ctx, (String)"name")), "Region not found."))))).then(ClientCommands.literal((String)"global").executes(ctx -> {
            RegionManager.toggleGlobal();
            return 1;
        }))).then(ClientCommands.literal((String)"outlines").executes(ctx -> {
            SelectionOverlayRenderer.toggleSavedRegions();
            return 1;
        }))).then(ClientCommands.literal((String)"light").executes(ctx -> {
            RegionManager.toggleVirtualLight();
            return 1;
        }))).then(ClientCommands.literal((String)"gui").executes(ctx -> {
            Minecraft.getInstance().setScreenAndShow(new RenderHideScreen());
            return 1;
        }))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal((String)"filter").then(ClientCommands.literal((String)"add").then(ClientCommands.argument((String)"block", (ArgumentType)StringArgumentType.greedyString()).suggests((ctx, builder) -> RenderHideClient.suggestBlocks(builder, false)).executes(ctx -> RenderHideClient.addFilter(StringArgumentType.getString((CommandContext)ctx, (String)"block")))))).then(ClientCommands.literal((String)"remove").then(ClientCommands.argument((String)"block", (ArgumentType)StringArgumentType.greedyString()).suggests((ctx, builder) -> RenderHideClient.suggestBlocks(builder, true)).executes(ctx -> RenderHideClient.removeFilter(StringArgumentType.getString((CommandContext)ctx, (String)"block")))))).then(ClientCommands.literal((String)"list").executes(ctx -> RenderHideClient.listFilters((FabricClientCommandSource)ctx.getSource())))).then(ClientCommands.literal((String)"clear").executes(ctx -> {
            RegionManager.clearVisibleBlockFilters();
            return 1;
        })))).then(ClientCommands.literal((String)"clear").executes(ctx -> {
            RegionManager.clear();
            return 1;
        }))).then(ClientCommands.literal((String)"list").executes(ctx -> RenderHideClient.list((FabricClientCommandSource)ctx.getSource())))).then(ClientCommands.literal((String)"reload").executes(ctx -> {
            RegionManager.refreshAll();
            return 1;
        }))));
    }

    private static CompletableFuture<Suggestions> suggestEntities(SuggestionsBuilder builder, boolean filtersOnly) {
        String query = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        var ids = filtersOnly ? RegionManager.visibleEntityFilters() : BuiltInRegistries.ENTITY_TYPE.keySet();
        ids.stream().sorted().filter(id -> id.toString().startsWith(query) || id.getPath().startsWith(query))
                .forEach(id -> builder.suggest(id.toString()));
        return builder.buildFuture();
    }

    private static int lookedAtEntityFilter() {
        if (Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
            return entityFilter(BuiltInRegistries.ENTITY_TYPE.getKey(hit.getEntity().getType()).toString(), true);
        }
        RegionManager.message("Look at an entity, or use /renderhide e_filter add <entity_id>.");
        return 0;
    }

    private static int entityFilter(String value, boolean add) {
        value = value.trim();
        Identifier id = Identifier.tryParse(value);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            RegionManager.message("Unknown entity: " + value);
            return 0;
        }
        return result(add ? RegionManager.addVisibleEntityFilter(id) : RegionManager.removeVisibleEntityFilter(id),
                add ? "Entity is already in the visible filter." : "Entity is not in the visible filter.");
    }

    private static CompletableFuture<Suggestions> suggestNames(SuggestionsBuilder builder) {
        for (HiddenRegion region : RegionManager.regions()) {
            builder.suggest(StringArgumentType.escapeIfRequired(region.name()));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestBlocks(SuggestionsBuilder builder, boolean filtersOnly) {
        if (filtersOnly) {
            RegionManager.visibleBlockFilters().forEach(id -> builder.suggest(id.toString()));
        } else {
            BuiltInRegistries.BLOCK.keySet().forEach(id -> builder.suggest(id.toString()));
        }
        return builder.buildFuture();
    }

    private static int addFilter(String value) {
        Identifier id = Identifier.tryParse(value = value.trim());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            RegionManager.message("Unknown block: " + value);
            return 0;
        }
        return RenderHideClient.result(RegionManager.addVisibleBlockFilter(id), "Block is already in the visible filter.");
    }

    private static int removeFilter(String value) {
        Identifier id = Identifier.tryParse(value = value.trim());
        return RenderHideClient.result(id != null && RegionManager.removeVisibleBlockFilter(id), "Block is not in the visible filter.");
    }

    private static int listFilters(FabricClientCommandSource source) {
        if (RegionManager.visibleBlockFilters().isEmpty()) {
            source.sendFeedback((Component)Component.literal((String)"[Render Hide] Visible block filters: none (all blocks are hidden)."));
            return 1;
        }
        source.sendFeedback((Component)Component.literal((String)"[Render Hide] Blocks kept visible inside hidden regions:"));
        RegionManager.visibleBlockFilters().stream().sorted().forEach(id -> source.sendFeedback((Component)Component.literal((String)("- " + String.valueOf(id)))));
        return RegionManager.visibleBlockFilters().size();
    }

    private static int result(boolean success, String failure) {
        if (!success) {
            RegionManager.message(failure);
        }
        return success ? 1 : 0;
    }

    private static int list(FabricClientCommandSource source) {
        if (RegionManager.regions().isEmpty()) {
            source.sendFeedback((Component)Component.literal((String)"[Render Hide] No regions saved."));
            return 1;
        }
        source.sendFeedback((Component)Component.literal((String)"[Render Hide] Saved regions:"));
        for (HiddenRegion region : RegionManager.regions()) {
            source.sendFeedback((Component)Component.literal((String)("- " + region.name() + " | " + (region.enabled() ? "hidden" : "visible") + " | " + region.blockCount() + " blocks | " + region.dimension())));
        }
        return RegionManager.regions().size();
    }

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback((Component)Component.literal((String)"[Render Hide] H=GUI, F9=pos1, F10=pos2, F7=add, F8=global toggle, F6=saved outlines"));
        source.sendFeedback((Component)Component.literal((String)"/renderhide add [name] | remove <name> | toggle <name> | list | clear"));
        source.sendFeedback((Component)Component.literal((String)"/renderhide filter add|remove <block> | filter list|clear"));
        source.sendFeedback((Component)Component.literal((String)"/renderhide light = virtual light toggle"));
        source.sendFeedback(Component.literal("/renderhide e_filter add [entity_id] | remove <entity_id> | list | clear"));
        return 1;
    }
}
