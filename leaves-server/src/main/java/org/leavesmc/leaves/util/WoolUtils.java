package org.leavesmc.leaves.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class WoolUtils {
    public static DyeColor getWoolColorAtPosition(Level worldIn, BlockPos pos) {
        BlockState state = worldIn.getBlockState(pos);
        Block block = state.getBlock();
        for (DyeColor color : DyeColor.values()) {
            if (Blocks.WOOL.pick(color) == block) {
                return color;
            }
        }
        return null;
    }
}
