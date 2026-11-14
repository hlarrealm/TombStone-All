package com.tombstoneall;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * TombStone All - when a player dies, their items and experience are stored
 * in a grave at the place of death. Only the owner can open the grave.
 */
@Mod(TombStoneAll.MOD_ID)
public class TombStoneAll {
    public static final String MOD_ID = "tombstoneall";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredHolder<Block, GraveBlock> GRAVE = BLOCKS.register("grave", id ->
            new GraveBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion()
                    .noLootTable()
                    .lightLevel(state -> 4)));

    public static final DeferredHolder<Item, GraveKeyItem> GRAVE_KEY = ITEMS.register("grave_key", id ->
            new GraveKeyItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GraveBlockEntity>> GRAVE_BE =
            BLOCK_ENTITIES.register("grave", () -> new BlockEntityType<>(GraveBlockEntity::new, GRAVE.get()));

    public TombStoneAll(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);

        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, GraveEvents::onExperienceDrop);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, GraveEvents::onPlayerDrops);
        NeoForge.EVENT_BUS.addListener(GraveEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(GraveEvents::onBreak);
    }
}
