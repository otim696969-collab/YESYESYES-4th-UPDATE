package com.selfmade;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Iterator;

/**
 * Things your identity left standing in the world: statues of you, claim markers where you conquered something,
 * and the name over a Grave of You. They are saved as plain data in your identity and their entities are simply
 * recreated whenever you are near (entities of this mod are never written to the world save).
 */
final class Fixtures {
	private static final int MAX_STATUES = 6;
	private static final int MAX_CLAIMS = 8;

	private Fixtures() {
	}

	static void add(PlayerIdentity id, PlayerIdentity.Fixture fixture, ServerLevel level) {
		id.fixtures.add(fixture);
		int cap = switch (fixture.kind) {
			case "statue" -> MAX_STATUES;
			case "claim" -> MAX_CLAIMS;
			case "territory" -> 3;
			default -> Integer.MAX_VALUE;
		};
		int count = 0;
		for (PlayerIdentity.Fixture f : id.fixtures) {
			if (f.kind.equals(fixture.kind)) {
				count++;
			}
		}
		Iterator<PlayerIdentity.Fixture> it = id.fixtures.iterator();
		while (count > cap && it.hasNext()) {
			PlayerIdentity.Fixture old = it.next();
			if (old.kind.equals(fixture.kind)) {
				if (old.entity != null) {
					Entity e = level.getEntity(old.entity);
					if (e != null) {
						e.discard();
					}
				}
				it.remove();
				count--;
			}
		}
	}

	/** Every couple of seconds: make sure every fixture near the player has its entity. */
	static void tick(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		String dim = SelfMade.dimensionOf(p);
		for (PlayerIdentity.Fixture f : id.fixtures) {
			if (!f.dim.equals(dim)) {
				continue;
			}
			double dx = f.x + 0.5 - p.getX();
			double dz = f.z + 0.5 - p.getZ();
			if (dx * dx + dz * dz > 48.0 * 48.0) {
				continue;
			}
			ShadeEntity existing = null;
			if (f.entity != null && level.getEntity(f.entity) instanceof ShadeEntity found && !found.isRemoved()) {
				existing = found;
			}
			if (existing == null) {
				ShadeEntity fresh = new ShadeEntity(ModEntities.SHADE, level);
				ShadeEntity.Kind kind = f.kind.equals("statue") ? ShadeEntity.Kind.STATUE : ShadeEntity.Kind.MARKER;
				fresh.setup(kind, p.getUUID(), id, f.text);
				fresh.setPos(f.x + 0.5, f.y, f.z + 0.5);
				f.entity = fresh.getUUID();
				level.addFreshEntity(fresh);
			} else if (!f.kind.equals("statue")) {
				existing.setCustomName(Component.literal(f.text));
			}
		}
	}
}
