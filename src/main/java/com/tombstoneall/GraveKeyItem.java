package com.tombstoneall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A key given on respawn; its name shows where the grave is. */
public class GraveKeyItem extends Item {

    public GraveKeyItem(Properties properties) {
        super(properties);
    }

    /** Text like "[120, 64, -30 overworld]" that identifies one grave. */
    public static String locationTag(ServerLevel level, BlockPos pos) {
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " " + level.dimension().identifier().getPath() + "]";
    }

    public static ItemStack createKey(ServerLevel level, BlockPos pos) {
        ItemStack key = new ItemStack(TombStoneAll.GRAVE_KEY.get());
        key.set(DataComponents.CUSTOM_NAME, Component.translatable("item.tombstoneall.grave_key")
                .append(" " + locationTag(level, pos))
                .withStyle(ChatFormatting.GOLD));
        return key;
    }

    /** Removes the keys that belong to the grave at this position. */
    public static void removeKeysFor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        String tag = locationTag(level, pos);
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(TombStoneAll.GRAVE_KEY.get()) && stack.getHoverName().getString().contains(tag)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            ItemStack stack = player.getItemInHand(hand);
            serverPlayer.sendSystemMessage(Component.translatable("message.tombstoneall.key_location")
                    .append(" ")
                    .append(stack.getHoverName())
                    .withStyle(ChatFormatting.GOLD));
        }
        return InteractionResult.SUCCESS;
    }
}
