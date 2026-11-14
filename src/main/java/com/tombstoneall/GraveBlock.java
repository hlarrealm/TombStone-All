package com.tombstoneall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class GraveBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Base slab (whole footprint) + upright stone at the back, one shape per facing.
    private static final VoxelShape BASE = Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.125, 0.9375);
    private static final VoxelShape NORTH = Shapes.or(BASE, Shapes.box(0.125, 0.125, 0.6875, 0.875, 0.9375, 0.875));
    private static final VoxelShape SOUTH = Shapes.or(BASE, Shapes.box(0.125, 0.125, 0.125, 0.875, 0.9375, 0.3125));
    private static final VoxelShape WEST = Shapes.or(BASE, Shapes.box(0.6875, 0.125, 0.125, 0.875, 0.9375, 0.875));
    private static final VoxelShape EAST = Shapes.or(BASE, Shapes.box(0.125, 0.125, 0.125, 0.3125, 0.9375, 0.875));

    public GraveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GraveBlockEntity(pos, state);
    }

    /** Withers, ender dragons, etc. cannot destroy a grave. */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        openGrave(serverLevel, pos, serverPlayer);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Gives the content of the grave back to its owner, then removes the grave. */
    public static void openGrave(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof GraveBlockEntity grave)) {
            return;
        }
        if (!grave.isOwner(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.tombstoneall.not_owner", grave.getOwnerName())
                    .withStyle(ChatFormatting.RED));
            return;
        }

        List<ItemStack> stacks = grave.takeItems();
        int xp = grave.takeExperience();

        for (ItemStack stack : stacks) {
            giveBack(player, stack);
        }
        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }

        GraveKeyItem.removeKeysFor(player, level, pos);
        level.removeBlock(pos, false);
        level.playSound((Entity) null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.6F, 1.2F);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
        player.sendSystemMessage(Component.translatable("message.tombstoneall.recovered").withStyle(ChatFormatting.GREEN));
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        // Put armor back on if the slot is free.
        EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && player.getItemBySlot(slot).isEmpty()) {
            player.setItemSlot(slot, stack);
            return;
        }
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            }
        }
    }

    /** Floating soul particles, like a haunted grave. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SOUL,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6,
                    pos.getY() + 0.9,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0.0, 0.02, 0.0);
        }
    }
}
