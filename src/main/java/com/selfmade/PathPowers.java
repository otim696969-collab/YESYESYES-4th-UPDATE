package com.selfmade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Identity Evolution. Instead of a skill tree you lean toward one of five {@link IdentityPath}s (two traits meeting).
 * The further you lean, the more of that path's three powers you are given, and the Eidolon changes shape to match.
 * Nothing is named to the player. Passive powers just happen; active powers are reached for with the Identity Mirror:
 * sneak and right-click it (look at your own feet while doing it to choose between several).
 */
final class PathPowers {
	/** Path score (sqrt of the two traits multiplied) needed for the first, second and third power. */
	static final double[] TIERS = {6.0, 14.0, 26.0};

	enum Power {
		BARRIER(IdentityPath.PROTECTOR, 0, true, 600,
				"You hold out your hands and the air agrees to be a wall.",
				"Something in you has learned how to stand between."),
		REDIRECT(IdentityPath.PROTECTOR, 1, false, 0, "",
				"When someone near you is hurt, a little of it finds you instead."),
		REVIVE(IdentityPath.PROTECTOR, 2, false, 12000, "",
				"You have started to refuse other people's endings."),

		SENSE(IdentityPath.WANDERER, 0, true, 300,
				"You listen for the places nobody has been.",
				"You can feel where nobody has been."),
		STEP(IdentityPath.WANDERER, 1, true, 200,
				"You reach for the space ahead.",
				"The distance in front of you has become negotiable."),
		ORIENT(IdentityPath.WANDERER, 2, true, 600,
				"You remember which door you came in by.",
				"You always know which door you came in by."),

		INTIMIDATE(IdentityPath.CONQUEROR, 0, false, 0, "",
				"Small things have started to look away from you."),
		TERRITORY(IdentityPath.CONQUEROR, 1, true, 6000,
				"You decide that this ground is yours.",
				"The ground has started to want to belong to you."),
		GIANT_SLAYER(IdentityPath.CONQUEROR, 2, false, 0, "",
				"The bigger the thing in front of you, the calmer you get."),

		LAST_STAND(IdentityPath.SURVIVOR, 0, false, 0, "",
				"You are harder to kill the closer you are to dead."),
		DETECT(IdentityPath.SURVIVOR, 1, false, 0, "",
				"You can feel what is hunting you before it arrives."),
		ESCAPE(IdentityPath.SURVIVOR, 2, true, 1200,
				"You stop being something that can be caught.",
				"You have learned how to stop being there."),

		CONSTRUCT(IdentityPath.CREATOR, 0, true, 900,
				"You ask the rubble to stand up.",
				"Rubble has started to look at you hopefully."),
		RESHAPE(IdentityPath.CREATOR, 1, true, 400,
				"You tell the stone it is wrong.",
				"Stone has begun to listen."),
		LIFE(IdentityPath.CREATOR, 2, false, 0, "",
				"What you make has started to want to stay.");

		final IdentityPath path;
		final int tier;
		final boolean active;
		final int cooldown;
		/** Whispered when you reach for it (and when you select it). */
		final String reach;
		/** Whispered once, when you are first given it. */
		final String felt;

		Power(IdentityPath path, int tier, boolean active, int cooldown, String reach, String felt) {
			this.path = path;
			this.tier = tier;
			this.active = active;
			this.cooldown = cooldown;
			this.reach = reach;
			this.felt = felt;
		}
	}

	private static final Map<UUID, Float> LAST_HP = new HashMap<>();

	private PathPowers() {
	}

	// ------------------------------------------------------------------ which path, which powers

	static IdentityPath current(PlayerIdentity id) {
		if (id.path == null) {
			return null;
		}
		try {
			return IdentityPath.valueOf(id.path);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static List<Power> unlocked(PlayerIdentity id) {
		List<Power> out = new ArrayList<>();
		IdentityPath path = current(id);
		if (path == null) {
			return out;
		}
		double score = path.score(id);
		for (Power power : Power.values()) {
			if (power.path == path && score >= TIERS[power.tier]) {
				out.add(power);
			}
		}
		return out;
	}

	static boolean has(PlayerIdentity id, Power power) {
		return unlocked(id).contains(power);
	}

	private static String awakening(IdentityPath path) {
		return switch (path) {
			case PROTECTOR -> "You have started to stand in doorways.";
			case WANDERER -> "You have started to look past the edges of things.";
			case CONQUEROR -> "You have started to think of the ground as yours.";
			case SURVIVOR -> "You have started to refuse to be finished.";
			case CREATOR -> "You have started to see what things could be, instead of what they are.";
		};
	}

	/** Once a second: settle which way you lean (with some stubbornness) and announce new powers. */
	static void update(ServerPlayer p, PlayerIdentity id) {
		IdentityPath best = IdentityPath.ranked(id)[0];
		IdentityPath cur = current(id);
		if (cur == null) {
			if (best.score(id) >= TIERS[0]) {
				id.path = best.name();
				id.selected = 0;
				SelfMade.whisper(p, awakening(best));
			}
		} else if (cur.score(id) < TIERS[0] * 0.6) {
			id.path = null;
			SelfMade.whisper(p, "What you had has gone quiet. It was never as much yours as you thought.");
		} else if (best != cur && best.score(id) > cur.score(id) * 1.25) {
			id.path = best.name();
			id.selected = 0;
			SelfMade.whisper(p, "You are not who you were.");
			SelfMade.whisper(p, awakening(best));
		}
		for (Power power : unlocked(id)) {
			if (id.powersSeen.add(power.name())) {
				SelfMade.whisper(p, power.felt);
			}
		}
	}

	// ------------------------------------------------------------------ the Eidolon changes shape

	private static Trait[] formTraits(IdentityPath path) {
		return switch (path) {
			case PROTECTOR -> new Trait[] {Trait.EMPATHY, Trait.RESOLVE};                    // guardian wings and pauldrons
			case WANDERER -> new Trait[] {Trait.CURIOSITY};                                   // eyes, and more of them
			case CONQUEROR -> new Trait[] {Trait.EGO, Trait.VIOLENCE, Trait.RESOLVE};         // crown, horns, armour
			case SURVIVOR -> new Trait[] {Trait.FEAR, Trait.VIOLENCE, Trait.WILL};            // faceless, long, feral, a red core
			case CREATOR -> new Trait[] {Trait.CURIOSITY, Trait.EMPATHY, Trait.ATTACHMENT};   // eyes, wings, threads
		};
	}

	/** Raises the traits that make up the body of your path. {@code scale} is 255 for the Eidolon, 100 for the mirror. */
	static void shape(PlayerIdentity id, int[] levels, int scale) {
		IdentityPath path = current(id);
		if (path == null) {
			return;
		}
		double depth = Math.min(1.0, path.score(id) / TIERS[2]);
		int floor = (int) Math.round(scale * (0.38 + 0.52 * depth));
		for (Trait t : formTraits(path)) {
			levels[t.ordinal()] = Math.max(levels[t.ordinal()], floor);
		}
	}

	/** Which trait should decide where the Eidolon hovers, or -1 to leave it to the strongest trait. */
	static int leadOverride(PlayerIdentity id) {
		IdentityPath path = current(id);
		if (path == null) {
			return -1;
		}
		return switch (path) {
			case PROTECTOR -> Trait.EMPATHY.ordinal();
			case WANDERER, CREATOR -> Trait.CURIOSITY.ordinal();
			case CONQUEROR -> Trait.EGO.ordinal();
			case SURVIVOR -> Trait.FEAR.ordinal();
		};
	}

	// ------------------------------------------------------------------ passive powers (once a second)

	private static boolean hostile(Mob m) {
		return m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER && !(m instanceof ShadeEntity);
	}

	static void tick(ServerPlayer p, ServerLevel level, PlayerIdentity id, long now) {
		IdentityPath path = current(id);
		if (path == null) {
			return;
		}
		List<Power> has = unlocked(id);
		switch (path) {
			case CONQUEROR -> conqueror(p, level, id, has);
			case SURVIVOR -> survivor(p, level, has);
			case CREATOR -> {
				if (has.contains(Power.LIFE)) {
					for (UUID uuid : id.constructs) {
						if (level.getEntity(uuid) instanceof ShadeEntity s && !s.isRemoved()) {
							s.heal(1.0f);
						}
					}
				}
			}
			default -> {
			}
		}
	}

	private static void conqueror(ServerPlayer p, ServerLevel level, PlayerIdentity id, List<Power> has) {
		if (has.contains(Power.INTIMIDATE)) {
			List<Mob> small = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8.0),
					m -> hostile(m) && m.getMaxHealth() < 60.0f);
			for (Mob m : small) {
				if (m.getTarget() == p) {
					m.setTarget(null);
				}
				Fx.give(m, Fx.Kind.SLOWNESS, 40, 0);
			}
			if (!small.isEmpty()) {
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2.0, p.getZ(), 2, 0.4, 0.1, 0.4, 0.0);
			}
		}
		if (has.contains(Power.GIANT_SLAYER)) {
			List<Mob> big = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12.0),
					m -> hostile(m) && m.getMaxHealth() >= 40.0f);
			float biggest = 0.0f;
			for (Mob m : big) {
				biggest = Math.max(biggest, m.getMaxHealth());
			}
			if (biggest >= 40.0f) {
				Fx.give(p, Fx.Kind.STRENGTH, 60, biggest >= 100.0f ? 2 : 1);
				if (biggest >= 100.0f) {
					Fx.give(p, Fx.Kind.RESISTANCE, 60, 0);
				}
			}
		}
		if (has.contains(Power.TERRITORY)) {
			territories(p, level, id);
		}
	}

	private static final double TERRITORY_RADIUS = 12.0;

	private static void territories(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		String dim = SelfMade.dimensionOf(p);
		for (String entry : id.territories) {
			String[] a = entry.split(",");
			if (a.length != 4 || !a[0].equals(dim)) {
				continue;
			}
			double cx;
			double cy;
			double cz;
			try {
				cx = Integer.parseInt(a[1]) + 0.5;
				cy = Integer.parseInt(a[2]);
				cz = Integer.parseInt(a[3]) + 0.5;
			} catch (NumberFormatException e) {
				continue;
			}
			double dx = p.getX() - cx;
			double dz = p.getZ() - cz;
			double distSq = dx * dx + dz * dz;
			if (distSq > 40.0 * 40.0) {
				continue;
			}
			if (distSq <= TERRITORY_RADIUS * TERRITORY_RADIUS) {
				Fx.give(p, Fx.Kind.STRENGTH, 60, 0);
				Fx.give(p, Fx.Kind.RESISTANCE, 60, 0);
				List<Mob> intruders = level.getEntitiesOfClass(Mob.class,
						new AABB(cx - TERRITORY_RADIUS, cy - 8, cz - TERRITORY_RADIUS, cx + TERRITORY_RADIUS, cy + 8, cz + TERRITORY_RADIUS),
						PathPowers::hostile);
				for (Mob m : intruders) {
					Fx.give(m, Fx.Kind.WEAKNESS, 40, 0);
				}
			}
			for (int i = 0; i < 16; i++) {
				double ang = i * (Math.PI * 2.0 / 16.0);
				level.sendParticles(ParticleTypes.FLAME, cx + Math.cos(ang) * TERRITORY_RADIUS, cy + 0.3,
						cz + Math.sin(ang) * TERRITORY_RADIUS, 1, 0.0, 0.2, 0.0, 0.0);
			}
		}
	}

	private static void survivor(ServerPlayer p, ServerLevel level, List<Power> has) {
		if (has.contains(Power.LAST_STAND)) {
			float hp = p.getHealth();
			float max = p.getMaxHealth();
			if (hp <= max * 0.4f) {
				Fx.give(p, Fx.Kind.STRENGTH, 60, 0);
				Fx.give(p, Fx.Kind.RESISTANCE, 60, 0);
			}
			if (hp <= max * 0.2f) {
				Fx.give(p, Fx.Kind.SPEED, 60, 1);
				Fx.give(p, Fx.Kind.REGENERATION, 60, 0);
			}
		}
		if (has.contains(Power.DETECT)) {
			List<Mob> hunters = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(20.0), PathPowers::hostile);
			int shown = 0;
			for (Mob m : hunters) {
				if (shown++ >= 8) {
					break;
				}
				Fx.give(m, Fx.Kind.GLOWING, 30, 0);
			}
		}
	}

	// ------------------------------------------------------------------ passive powers that react to events

	/** Once a second, before anyone else is looked at: Protectors take a share of the damage their friends just took. */
	static void healthPass(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer prot : players) {
			if (!prot.isAlive() || !has(SelfMade.STORE.get(prot.getUUID()), Power.REDIRECT)) {
				continue;
			}
			for (ServerPlayer ally : players) {
				if (ally == prot || !ally.isAlive() || ally.level() != prot.level() || ally.distanceToSqr(prot) > 12.0 * 12.0) {
					continue;
				}
				Float last = LAST_HP.get(ally.getUUID());
				if (last == null) {
					continue;
				}
				float drop = last - ally.getHealth();
				if (drop < 1.0f) {
					continue;
				}
				float shift = Math.min(drop * 0.4f, Math.max(0.0f, prot.getHealth() - 4.0f));
				if (shift <= 0.0f) {
					continue;
				}
				ally.heal(shift);
				prot.setHealth(prot.getHealth() - shift);
				if (prot.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.ENCHANT, ally.getX(), ally.getY() + 1.2, ally.getZ(), 12, 0.3, 0.5, 0.3, 0.3);
					level.sendParticles(ParticleTypes.ENCHANT, prot.getX(), prot.getY() + 1.2, prot.getZ(), 12, 0.3, 0.5, 0.3, 0.3);
				}
			}
		}
		for (ServerPlayer pl : players) {
			LAST_HP.put(pl.getUUID(), pl.getHealth());
		}
	}

	/** Called just before a player would die: a nearby Protector may refuse it, at a price. */
	static boolean tryRevive(ServerPlayer victim, long now) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return false;
		}
		for (ServerPlayer r : level.getServer().getPlayerList().getPlayers()) {
			if (r == victim || !r.isAlive() || r.level() != victim.level() || r.distanceToSqr(victim) > 16.0 * 16.0) {
				continue;
			}
			PlayerIdentity rid = SelfMade.STORE.get(r.getUUID());
			if (!has(rid, Power.REVIVE) || now < rid.cooldowns.getOrDefault(Power.REVIVE.name(), 0L)) {
				continue;
			}
			rid.cooldowns.put(Power.REVIVE.name(), now + Power.REVIVE.cooldown);
			victim.setHealth(8.0f);
			victim.clearFire();
			Fx.give(victim, Fx.Kind.RESISTANCE, 100, 1);
			Fx.give(victim, Fx.Kind.REGENERATION, 100, 1);
			r.setHealth(Math.max(2.0f, r.getHealth() * 0.5f));
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.getX(), victim.getY() + 1.0, victim.getZ(), 60, 0.5, 0.8, 0.5, 0.5);
			SelfMade.whisper(victim, "Someone stood in the way of it.");
			SelfMade.whisper(r, "You paid for that. You would again.");
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ reaching for an active power

	/** Sneak + right-click the Identity Mirror. */
	static void invoke(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		PlayerIdentity id = SelfMade.STORE.get(p.getUUID());
		long now = SelfMade.now();
		List<Power> all = unlocked(id);
		if (all.isEmpty()) {
			SelfMade.whisper(p, "You reach for something. Nothing answers. Not yet.");
			return;
		}
		List<Power> actives = new ArrayList<>();
		for (Power power : all) {
			if (power.active) {
				actives.add(power);
			}
		}
		if (actives.isEmpty()) {
			SelfMade.whisper(p, "What you have does not need to be asked for.");
			return;
		}
		if (id.selected >= actives.size()) {
			id.selected = 0;
		}
		if (actives.size() > 1 && p.getXRot() > 60.0f) {
			id.selected = (id.selected + 1) % actives.size();
			SelfMade.whisper(p, actives.get(id.selected).reach);
			return;
		}
		Power power = actives.get(id.selected);
		if (now < id.cooldowns.getOrDefault(power.name(), 0L)) {
			SelfMade.whisper(p, "You are still empty from the last time.");
			return;
		}
		boolean ok = switch (power) {
			case BARRIER -> barrier(p, level, id);
			case SENSE -> sense(p, level, id);
			case STEP -> step(p, level);
			case ORIENT -> orient(p, level, id);
			case TERRITORY -> territory(p, level, id);
			case ESCAPE -> escape(p, level);
			case CONSTRUCT -> construct(p, level, id);
			case RESHAPE -> reshape(p, level);
			default -> false;
		};
		if (ok) {
			SelfMade.whisper(p, power.reach);
			id.cooldowns.put(power.name(), now + power.cooldown);
		}
	}

	private static String compass(double dx, double dz) {
		String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
		int index = (int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4.0));
		return names[((index % 8) + 8) % 8];
	}

	private static String distance(double blocks) {
		if (blocks < 48.0) {
			return "very near";
		}
		if (blocks < 128.0) {
			return "near";
		}
		return blocks < 256.0 ? "far" : "very far";
	}

	private static void trail(ServerLevel level, ServerPlayer p, double dx, double dz) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.001) {
			return;
		}
		for (int i = 1; i <= 16; i++) {
			level.sendParticles(ParticleTypes.END_ROD, p.getX() + dx / len * i, p.getY() + 1.0, p.getZ() + dz / len * i,
					1, 0.0, 0.05, 0.0, 0.0);
		}
	}

	// ---- Protector

	private static boolean barrier(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		BlockPos c = p.blockPosition();
		int ticks = has(id, Power.REVIVE) ? 400 : 220; // the third power makes it last
		BlockState glass = BuiltInRegistries.BLOCK
        .get(Identifier.withDefaultNamespace("light_blue_stained_glass"))
        .orElseThrow().value()
        .defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) != 2 && Math.abs(dz) != 2) {
					continue;
				}
				for (int h = 0; h < 3; h++) {
					TempBlocks.fillAir(level, c.offset(dx, h, dz), glass, ticks);
				}
			}
		}
		for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
			if (other.isAlive() && other.level() == p.level() && other.distanceToSqr(p) <= 3.0 * 3.0) {
				Fx.give(other, Fx.Kind.REGENERATION, 100, 0);
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 30, 2.0, 1.0, 2.0, 0.02);
		return true;
	}

	// ---- Wanderer

	private static boolean sense(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		String dim = SelfMade.dimensionOf(p);
		int cx = p.getBlockX() >> 6;
		int cz = p.getBlockZ() >> 6;
		int bestD = Integer.MAX_VALUE;
		int bx = 0;
		int bz = 0;
		boolean found = false;
		for (int r = 1; r <= 5 && !found; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r || id.visitedCells.contains(SelfMade.cellKey(dim, cx + dx, cz + dz))) {
						continue;
					}
					int d = dx * dx + dz * dz;
					if (d < bestD) {
						bestD = d;
						bx = cx + dx;
						bz = cz + dz;
						found = true;
					}
				}
			}
		}
		if (!found) {
			SelfMade.whisper(p, "Everything near you has been walked on. You will have to go further.");
			return false;
		}
		double dx = bx * 64.0 + 32.0 - p.getX();
		double dz = bz * 64.0 + 32.0 - p.getZ();
		trail(level, p, dx, dz);
		SelfMade.whisper(p, "Something unwalked lies to the " + compass(dx, dz) + ". " + capital(distance(Math.sqrt(dx * dx + dz * dz))) + ".");
		return true;
	}

	private static String capital(String s) {
		return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
			return false;
		}
		for (int k = 1; k <= 3; k++) {
			BlockPos below = feet.below(k);
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static boolean step(ServerPlayer p, ServerLevel level) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (double d = 10.0; d >= 2.0; d -= 1.0) {
			BlockPos feet = new BlockPos((int) Math.floor(eye.x + look.x * d), (int) Math.floor(eye.y + look.y * d - 1.62),
					(int) Math.floor(eye.z + look.z * d));
			if (standable(level, feet)) {
				level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.3, 0.6, 0.3, 0.5);
				p.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, p.getYRot(), p.getXRot());
				p.fallDistance = 0.0;
				level.sendParticles(ParticleTypes.PORTAL, feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.5);
				return true;
			}
		}
		SelfMade.whisper(p, "There is nowhere ahead to stand.");
		return false;
	}

	private static boolean orient(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		String door = id.arrivals.get(SelfMade.dimensionOf(p));
		String[] a = door == null ? new String[0] : door.split(",");
		if (a.length != 3) {
			SelfMade.whisper(p, "There is no door behind you.");
			return false;
		}
		try {
			double dx = Integer.parseInt(a[0]) + 0.5 - p.getX();
			double dz = Integer.parseInt(a[2]) + 0.5 - p.getZ();
			trail(level, p, dx, dz);
			SelfMade.whisper(p, "The way you came in lies to the " + compass(dx, dz) + ". " + capital(distance(Math.sqrt(dx * dx + dz * dz))) + ".");
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	// ---- Conqueror

	private static boolean territory(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		BlockPos c = p.blockPosition();
		String dim = SelfMade.dimensionOf(p);
		while (id.territories.size() >= 3) {
			id.territories.remove(0);
		}
		id.territories.add(dim + "," + c.getX() + "," + c.getY() + "," + c.getZ());
		Graves.put(level, c.offset(1, 0, 0), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		Graves.put(level, c.offset(1, 1, 0), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		Graves.put(level, c.offset(1, 2, 0), Blocks.GOLD_BLOCK.defaultBlockState());
		Fixtures.add(id, new PlayerIdentity.Fixture(dim, c.getX(), c.getY() + 2, c.getZ(), "territory",
				p.getName().getString() + "'s ground"), level);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.5, p.getZ(), 60, 3.0, 0.3, 3.0, 0.05);
		return true;
	}

	// ---- Survivor

	private static boolean escape(ServerPlayer p, ServerLevel level) {
		Fx.give(p, Fx.Kind.SPEED, 100, 3);
		Fx.give(p, Fx.Kind.RESISTANCE, 100, 1);
		Fx.give(p, Fx.Kind.INVISIBILITY, 100, 0);
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), PathPowers::hostile)) {
			if (m.getTarget() == p) {
				m.setTarget(null);
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
		return true;
	}

	// ---- Creator

	private static boolean construct(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		int cap = has(id, Power.LIFE) ? 2 : 1;
		Iterator<UUID> it = id.constructs.iterator();
		while (it.hasNext()) {
			UUID uuid = it.next();
			if (!(level.getEntity(uuid) instanceof ShadeEntity s) || s.isRemoved()) {
				it.remove();
			}
		}
		while (id.constructs.size() >= cap) {
			Entity old = level.getEntity(id.constructs.remove(0));
			if (old != null) {
				old.discard();
			}
		}
		Vec3 look = p.getLookAngle();
		BlockPos at = Spots.groundNear(level, (int) Math.floor(p.getX() + look.x * 2.0), p.getBlockY(),
				(int) Math.floor(p.getZ() + look.z * 2.0));
		ShadeEntity c = new ShadeEntity(ModEntities.SHADE, level);
		c.setup(ShadeEntity.Kind.CONSTRUCT, p.getUUID(), id, "Construct");
		c.setLife(has(id, Power.LIFE) ? 3600 : 1200);
		c.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.addFreshEntity(c);
		id.constructs.add(c.getUUID());
		level.sendParticles(ParticleTypes.ENCHANT, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.5);
		return true;
	}

	private static boolean solid(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** A wall in front of you: a tunnel through it, for a while. Open air: a bridge, for a while. */
	private static boolean reshape(ServerPlayer p, ServerLevel level) {
		Direction dir = p.getDirection();
		BlockPos feet = p.blockPosition();
		int start = -1;
		for (int i = 1; i <= 4; i++) {
			BlockPos a = feet.relative(dir, i);
			if (solid(level, a) || solid(level, a.above())) {
				start = i;
				break;
			}
		}
		int changed = 0;
		if (start > 0) {
			for (int i = start; i < start + 6; i++) {
				BlockPos a = feet.relative(dir, i);
				changed += TempBlocks.carve(level, a, 160) ? 1 : 0;
				changed += TempBlocks.carve(level, a.above(), 160) ? 1 : 0;
			}
		} else {
			Direction side = dir.getClockWise();
			BlockState glass = Blocks.GLASS.defaultBlockState();
			for (int i = 1; i <= 8; i++) {
				for (int w = -1; w <= 1; w++) {
					changed += TempBlocks.fillAir(level, feet.below().relative(dir, i).relative(side, w), glass, 200) ? 1 : 0;
				}
			}
		}
		if (changed == 0) {
			SelfMade.whisper(p, "The stone does not agree.");
			return false;
		}
		level.sendParticles(ParticleTypes.ENCHANT, p.getX() + dir.getStepX() * 2.0, p.getY() + 1.0, p.getZ() + dir.getStepZ() * 2.0,
				30, 0.5, 0.6, 0.5, 0.4);
		return true;
	}
}
