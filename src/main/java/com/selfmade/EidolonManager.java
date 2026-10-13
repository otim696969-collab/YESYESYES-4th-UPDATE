package com.selfmade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Makes sure every living, online player has exactly one Eidolon, in the dimension they are standing in. */
public final class EidolonManager {
	/** owner uuid -> the uuid of that owner's current Eidolon. Anything else claiming to be theirs removes itself. */
	private static final Map<UUID, UUID> ACTIVE = new HashMap<>();

	private EidolonManager() {
	}

	/** Called when a world starts. */
	static void reset() {
		ACTIVE.clear();
	}

	static boolean isCurrent(UUID owner, UUID eidolon) {
		return eidolon.equals(ACTIVE.get(owner));
	}

	/** Spawns the player's Eidolon if it is missing (first join, after death, new dimension) and refreshes its looks. */
	static void sync(ServerPlayer player, PlayerIdentity id) {
		if (!player.isAlive() || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		EidolonEntity eidolon = null;
		UUID known = ACTIVE.get(player.getUUID());
		if (known != null) {
			Entity found = level.getEntity(known);
			if (found instanceof EidolonEntity existing && !existing.isRemoved()) {
				eidolon = existing;
			}
		}

		if (eidolon == null) {
			eidolon = new EidolonEntity(ModEntities.EIDOLON, level);
			eidolon.setOwner(player.getUUID());
			eidolon.setPos(player.getX(), player.getY() + 1.0, player.getZ());
			ACTIVE.put(player.getUUID(), eidolon.getUUID());
			level.addFreshEntity(eidolon);
		}
		eidolon.applyIdentity(id);
	}

	/** Dev tool: forget the current one so the next sync makes a new Eidolon. */
	static void forget(ServerPlayer player) {
		UUID known = ACTIVE.remove(player.getUUID());
		if (known != null && player.level() instanceof ServerLevel level) {
			Entity found = level.getEntity(known);
			if (found != null) {
				found.discard();
			}
		}
	}
}
