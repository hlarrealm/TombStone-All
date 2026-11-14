package com.tombstoneall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stores the owner, the items and the experience of a dead player. */
public class GraveBlockEntity extends BlockEntity {
    private UUID owner;
    private String ownerName = "";
    private List<ItemStack> items = new ArrayList<>();
    private int experience;
    private long deathTime;

    public GraveBlockEntity(BlockPos pos, BlockState state) {
        super(TombStoneAll.GRAVE_BE.get(), pos, state);
    }

    public void fill(UUID owner, String ownerName, List<ItemStack> stacks, int experience, long deathTime) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.items = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                this.items.add(stack.copy());
            }
        }
        this.experience = experience;
        this.deathTime = deathTime;
        setChanged();
    }

    public boolean isOwner(UUID uuid) {
        return owner == null || owner.equals(uuid);
    }

    public String getOwnerName() {
        return ownerName;
    }

    /** Takes everything out of the grave (the grave becomes empty). */
    public List<ItemStack> takeItems() {
        List<ItemStack> result = items;
        items = new ArrayList<>();
        setChanged();
        return result;
    }

    public int takeExperience() {
        int xp = experience;
        experience = 0;
        setChanged();
        return xp;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("owner", UUIDUtil.CODEC, owner);
        output.putString("owner_name", ownerName);
        output.store("items", ItemStack.CODEC.listOf(), items);
        output.putInt("experience", experience);
        output.putLong("death_time", deathTime);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getString("owner_name").orElse("");
        items = new ArrayList<>(input.read("items", ItemStack.CODEC.listOf()).orElse(List.of()));
        experience = input.getIntOr("experience", 0);
        deathTime = input.getLongOr("death_time", 0L);
    }
}
