package com.tombstoneall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GraveEvents {
    /** Experience saved before the drops event (if the XP event fires first). */
    private static final Map<UUID, Integer> PENDING_XP = new HashMap<>();
    /** Players whose XP is already in a grave (if the drops event fires first). */
    private static final Set<UUID> XP_ALREADY_SAVED = new HashSet<>();
    /** Graves waiting to give a key when the player respawns. */
    private static final Map<UUID, GraveInfo> PENDING_KEYS = new HashMap<>();

    private record GraveInfo(ServerLevel level, BlockPos pos) {}

    private GraveEvents() {}

    private static boolean keepInventory(ServerLevel level) {
        return Boolean.TRUE.equals(level.getGameRules().get(GameRules.KEEP_INVENTORY));
    }

    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        if (keepInventory(player.level()) || player.isSpectator()) {
            return;
        }
        UUID id = player.getUUID();
        if (XP_ALREADY_SAVED.remove(id)) {
            event.setCanceled(true);
            return;
        }
        PENDING_XP.put(id, player.totalExperience);
        event.setCanceled(true);
    }

    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        ServerLevel level = player.level();
        if (keepInventory(level) || player.isSpectator()) {
            return;
        }
        UUID id = player.getUUID();

        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }

        boolean xpEventAlreadyFired = PENDING_XP.containsKey(id);
        int xp = xpEventAlreadyFired ? PENDING_XP.remove(id) : player.totalExperience;

        if (stacks.isEmpty() && xp <= 0) {
            return;
        }

        BlockPos gravePos = findGravePos(level, player.blockPosition());
        if (gravePos == null) {
            // No room for a grave: let the items drop normally and give back the XP as orbs.
            if (xpEventAlreadyFired && xp > 0) {
                net.minecraft.world.entity.ExperienceOrb orb =
                        new net.minecraft.world.entity.ExperienceOrb(level, player.getX(), player.getY(), player.getZ(), xp);
                level.addFreshEntity(orb);
            }
            return;
        }

        BlockState state = TombStoneAll.GRAVE.get().defaultBlockState()
                .setValue(GraveBlock.FACING, player.getDirection().getOpposite());
        level.setBlockAndUpdate(gravePos, state);
        if (!(level.getBlockEntity(gravePos) instanceof GraveBlockEntity grave)) {
            return;
        }
        grave.fill(id, player.getName().getString(), stacks, xp, level.getGameTime());

        event.getDrops().clear();
        if (!xpEventAlreadyFired) {
            XP_ALREADY_SAVED.add(id);
        }

        PENDING_KEYS.put(id, new GraveInfo(level, gravePos));
        player.sendSystemMessage(Component.translatable("message.tombstoneall.grave_created",
                gravePos.getX(), gravePos.getY(), gravePos.getZ(),
                level.dimension().identifier().toString()).withStyle(ChatFormatting.GOLD));
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        XP_ALREADY_SAVED.remove(id);
        PENDING_XP.remove(id);
        GraveInfo info = PENDING_KEYS.remove(id);
        if (info == null) {
            return;
        }
        ItemStack key = GraveKeyItem.createKey(info.level(), info.pos());
        if (!player.getInventory().add(key)) {
            net.minecraft.world.Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), key);
        }
        player.sendSystemMessage(Component.translatable("message.tombstoneall.grave_reminder",
                info.pos().getX(), info.pos().getY(), info.pos().getZ()).withStyle(ChatFormatting.GOLD));
    }

    /**
     * Graves cannot be broken (not even in creative mode).
     * If the owner tries to break it, it just opens.
     */
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getState().getBlock() instanceof GraveBlock)) {
            return;
        }
        event.setCanceled(true);
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            GraveBlock.openGrave(serverPlayer.level(), event.getPos(), serverPlayer);
        }
    }

    /** Looks for a free spot near the place of death. */
    private static BlockPos findGravePos(ServerLevel level, BlockPos deathPos) {
        int minY = level.getMinY();
        int maxY = level.getMinY() + level.getHeight() - 1;
        int y = Math.max(minY + 1, Math.min(maxY - 1, deathPos.getY()));
        BlockPos start = new BlockPos(deathPos.getX(), y, deathPos.getZ());

        for (int radius = 0; radius <= 6; radius++) {
            for (int dy = 0; dy <= 12; dy++) {
                for (int sign : new int[]{1, -1}) {
                    if (dy == 0 && sign == -1) {
                        continue;
                    }
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                                continue;
                            }
                            BlockPos pos = start.offset(dx, dy * sign, dz);
                            if (level.isOutsideBuildHeight(pos)) {
                                continue;
                            }
                            BlockState here = level.getBlockState(pos);
                            if (here.canBeReplaced() && !(here.getBlock() instanceof GraveBlock)) {
                                return pos;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
