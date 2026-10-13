package com.selfmade;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Blocks that are only there for a while: barriers, bridges, tunnels. The original block is remembered and put back.
 * Kept in memory only; the server puts everything back when it stops (see {@link #restoreAll()}).
 */
final class TempBlocks {
	private static final class Entry {
		ServerLevel level;
		BlockPos pos;
		BlockState original;
		BlockState placed;
		long expire;
		int retries;
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();

	private TempBlocks() {
	}

	/** Puts a block into an empty space for a while. Returns false if the space was not empty. */
	static boolean fillAir(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
		BlockState current = level.getBlockState(pos);
		if (!current.isAir()) {
			return false;
		}
		level.setBlock(pos, state, 3);
		remember(level, pos, current, state, ticks);
		return true;
	}

	/** Turns a solid block into air for a while. Skips anything unbreakable, any container, and any fluid. */
	static boolean carve(ServerLevel level, BlockPos pos, int ticks) {
		BlockState current = level.getBlockState(pos);
		if (current.isAir() || !current.getFluidState().isEmpty() || level.getBlockEntity(pos) != null
				|| current.getDestroySpeed(level, pos) < 0.0f) {
			return false;
		}
		BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		level.setBlock(pos, air, 3);
		remember(level, pos, current, air, ticks);
		return true;
	}

	private static void remember(ServerLevel level, BlockPos pos, BlockState original, BlockState placed, int ticks) {
		Entry e = new Entry();
		e.level = level;
		e.pos = pos;
		e.original = original;
		e.placed = placed;
		e.expire = SelfMade.now() + ticks;
		ENTRIES.add(e);
	}

	/** Called every few ticks. */
	static void tick(long now) {
		if (ENTRIES.isEmpty()) {
			return;
		}
		Iterator<Entry> it = ENTRIES.iterator();
		while (it.hasNext()) {
			Entry e = it.next();
			if (now < e.expire) {
				continue;
			}
			if (!e.level.hasChunkAt(e.pos)) {
				if (now - e.expire > 6000L) {
					it.remove(); // gave up waiting for the chunk
				}
				continue;
			}
			if (!e.original.isAir() && e.retries < 10 && someoneInside(e)) {
				e.retries++;
				e.expire = now + 20; // never close a wall on somebody
				continue;
			}
			restore(e);
			it.remove();
		}
	}

	private static boolean someoneInside(Entry e) {
		for (ServerPlayer p : e.level.getServer().getPlayerList().getPlayers()) {
			if (p.level() == e.level && (p.blockPosition().equals(e.pos) || p.blockPosition().above().equals(e.pos))) {
				return true;
			}
		}
		return false;
	}

	private static void restore(Entry e) {
		BlockState cur = e.level.getBlockState(e.pos);
		boolean ours = e.placed.isAir() ? (cur.isAir() || !cur.getFluidState().isEmpty()) : cur.is(e.placed.getBlock());
		if (ours) {
			e.level.setBlock(e.pos, e.original, 3);
		}
	}

	/** Server is stopping: put everything back right now. */
	static void restoreAll() {
		for (Entry e : ENTRIES) {
			if (e.level.hasChunkAt(e.pos)) {
				restore(e);
			}
		}
		ENTRIES.clear();
	}
}
