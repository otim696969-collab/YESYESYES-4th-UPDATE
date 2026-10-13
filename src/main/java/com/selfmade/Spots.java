package com.selfmade;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/** Small helpers for finding somewhere to put things in the world. */
final class Spots {
	private Spots() {
	}

	/** The first air block with something solid under it, searching down from just above y (up to 14 blocks). */
	static BlockPos groundNear(ServerLevel level, int x, int y, int z) {
		for (int yy = y + 6; yy >= y - 14; yy--) {
			BlockPos pos = new BlockPos(x, yy, z);
			BlockPos below = pos.below();
			if (level.getBlockState(pos).isAir() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				return pos;
			}
		}
		return new BlockPos(x, y, z);
	}

	/** A random spot on the ground between min and max blocks away from the player. */
	static BlockPos ringSpot(ServerLevel level, ServerPlayer player, double min, double max, Random rnd) {
		double angle = rnd.nextDouble() * Math.PI * 2.0;
		double dist = min + rnd.nextDouble() * (max - min);
		int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
		int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
		return groundNear(level, x, player.getBlockY(), z);
	}
}
