package com.selfmade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Death does not reset you; it accumulates.
 * <ul>
 *   <li>3 deaths within 12 blocks: the place is haunted (soul particles, and sometimes an Echo of you walks out).</li>
 *   <li>5 deaths within 12 blocks: <b>The Grave of You</b> forms: a small stone structure with your name over it.</li>
 *   <li>Walk up to an unfinished Grave and it wakes: a Death-Eidolon and echoes of how you died.</li>
 *   <li>Defeat the Death-Eidolon and you reclaim part of yourself (see {@link #reclaim}).</li>
 * </ul>
 */
final class Graves {
	static final int RADIUS = 12;
	private static final int DEATHS_TO_FORM = 5;
	private static final Random RANDOM = new Random();

	private Graves() {
	}

	static int clusterSize(PlayerIdentity id, PlayerIdentity.DeathEcho at) {
		int n = 0;
		for (PlayerIdentity.DeathEcho e : id.echoes) {
			if (e.dim.equals(at.dim) && Math.abs(e.x - at.x) <= RADIUS && Math.abs(e.z - at.z) <= RADIUS) {
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ forming

	/** Called right after a death is recorded. */
	static void onDeath(ServerPlayer p, PlayerIdentity id, PlayerIdentity.DeathEcho echo) {
		if (!(p.level() instanceof ServerLevel level) || clusterSize(id, echo) < DEATHS_TO_FORM) {
			return;
		}
		for (PlayerIdentity.Grave g : id.graves) {
			if (g.dim.equals(echo.dim) && Math.abs(g.x - echo.x) <= 24 && Math.abs(g.z - echo.z) <= 24) {
				return; // this place already has its grave
			}
		}
		form(p, level, id, echo);
	}

	/** Builds a Grave of You at a death site. Also used by the dev command. */
	static void form(ServerPlayer p, ServerLevel level, PlayerIdentity id, PlayerIdentity.DeathEcho echo) {
		BlockPos g = Spots.groundNear(level, echo.x, echo.y, echo.z);
		build(level, g);

		PlayerIdentity.Grave grave = new PlayerIdentity.Grave();
		grave.dim = echo.dim;
		grave.x = g.getX();
		grave.y = g.getY();
		grave.z = g.getZ();
		for (int i = id.echoes.size() - 1; i >= 0 && grave.names.size() < 6; i--) {
			PlayerIdentity.DeathEcho e = id.echoes.get(i);
			if (!e.dim.equals(echo.dim) || Math.abs(e.x - echo.x) > RADIUS || Math.abs(e.z - echo.z) > RADIUS) {
				continue;
			}
			if (e.by != null) {
				grave.names.add(e.by);
			}
			for (String item : e.gear) {
				if (!grave.gear.contains(item) && grave.gear.size() < 8) {
					grave.gear.add(item);
				}
			}
		}
		id.graves.add(grave);

		String who = p.getName().getString();
		Fixtures.add(id, new PlayerIdentity.Fixture(echo.dim, g.getX(), g.getY() + 2, g.getZ(), "grave",
				"Here lies " + who + " (x" + Math.max(1, clusterSize(id, echo)) + ")"), level);
		SelfMade.later(id, "The ground has stopped waiting. Somewhere you keep dying, something has put up a stone for you.");
	}

	private static void build(ServerLevel level, BlockPos g) {
		BlockState brick = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		for (int sx = -2; sx <= 2; sx += 4) {
			for (int sz = -2; sz <= 2; sz += 4) {
				for (int h = 0; h < 3; h++) {
					put(level, g.offset(sx, h, sz), brick);
				}
				put(level, g.offset(sx, 3, sz), Blocks.SOUL_LANTERN.defaultBlockState());
			}
		}
		BlockState stone = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		put(level, g, stone);
		put(level, g.above(), stone);
		put(level, g.above(2), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
	}

	/** Only ever fills empty air, so a grave never eats somebody's build. */
	static void put(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, 3);
		}
	}

	// ------------------------------------------------------------------ haunting and waking

	/** Called for each haunted death site near the player: sometimes an Echo of you walks out of it. */
	static void maybeGhost(ServerPlayer p, ServerLevel level, PlayerIdentity id, PlayerIdentity.DeathEcho echo) {
		long now = SelfMade.now();
		if (now - id.lastGhostTick < 900L || RANDOM.nextInt(6) != 0) {
			return;
		}
		int active = 0;
		Iterator<UUID> it = id.ghosts.iterator();
		while (it.hasNext()) {
			UUID uuid = it.next();
			if (level.getEntity(uuid) instanceof ShadeEntity s && !s.isRemoved()) {
				active++;
			} else {
				it.remove();
			}
		}
		if (active >= 2) {
			return;
		}
		BlockPos at = Spots.groundNear(level, echo.x + RANDOM.nextInt(5) - 2, echo.y, echo.z + RANDOM.nextInt(5) - 2);
		ShadeEntity ghost = new ShadeEntity(ModEntities.SHADE, level);
		ghost.setup(ShadeEntity.Kind.GHOST, p.getUUID(), id, "Echo: " + nice(echo.by));
		ghost.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.addFreshEntity(ghost);
		id.ghosts.add(ghost.getUUID());
		id.lastGhostTick = now;
		if (id.omensSeen.add("first-ghost")) {
			SelfMade.whisper(p, "Someone who stands like you is standing where you fell.");
		}
	}

	/** Once a second: walking up to an unfinished grave wakes what is in it. */
	static void tick(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		String dim = SelfMade.dimensionOf(p);
		for (PlayerIdentity.Grave g : id.graves) {
			if (g.defeated || !g.dim.equals(dim)) {
				continue;
			}
			double dx = g.x + 0.5 - p.getX();
			double dz = g.z + 0.5 - p.getZ();
			if (dx * dx + dz * dz > 14.0 * 14.0 || Math.abs(g.y - p.getY()) > 12.0) {
				continue;
			}
			if (g.boss != null && level.getEntity(g.boss) instanceof ShadeEntity boss && !boss.isRemoved() && boss.isAlive()) {
				continue;
			}
			wake(p, level, id, g);
		}
	}

	private static void wake(ServerPlayer p, ServerLevel level, PlayerIdentity id, PlayerIdentity.Grave g) {
		ShadeEntity boss = new ShadeEntity(ModEntities.SHADE, level);
		boss.setup(ShadeEntity.Kind.DEATH, p.getUUID(), id, "Death-Eidolon");
		boss.setPos(g.x + 0.5, g.y + 0.1, g.z + 3.5);
		level.addFreshEntity(boss);
		g.boss = boss.getUUID();

		int echoes = Math.min(3, Math.max(1, g.names.size()));
		for (int i = 0; i < echoes; i++) {
			double angle = (Math.PI * 2.0 / echoes) * i + 0.7;
			BlockPos at = Spots.groundNear(level, g.x + (int) Math.round(Math.cos(angle) * 5.0), g.y,
					g.z + (int) Math.round(Math.sin(angle) * 5.0));
			ShadeEntity ghost = new ShadeEntity(ModEntities.SHADE, level);
			String by = g.names.isEmpty() ? null : g.names.get(i % g.names.size());
			ghost.setup(ShadeEntity.Kind.GHOST, p.getUUID(), id, "Echo: " + nice(by));
			ghost.setLife(3600);
			ghost.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.addFreshEntity(ghost);
		}
		level.sendParticles(ParticleTypes.SCULK_SOUL, g.x + 0.5, g.y + 1.5, g.z + 0.5, 40, 1.5, 1.0, 1.5, 0.05);
		SelfMade.whisper(p, "Something here has been waiting for you to come back.");
	}

	// ------------------------------------------------------------------ winning

	/** Called when any ShadeEntity dies. */
	static void onShadeDied(ShadeEntity shade, MinecraftServer server) {
		UUID ownerId = shade.ownerId();
		if (ownerId == null || server == null) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		if (owner == null) {
			return;
		}
		PlayerIdentity id = SelfMade.STORE.get(ownerId);
		switch (shade.kind()) {
			case DEATH -> reclaim(owner, id, shade);
			case OTHER -> Physicalize.otherYouSlain(owner, id);
			default -> {
			}
		}
	}

	/**
	 * Defeating the Death-Eidolon gives part of you back: half your fear, some Will and Resolve, experience,
	 * a new Fragment of Mortality, up to two pieces of your old gear (worn almost to nothing), and the haunting ends.
	 */
	private static void reclaim(ServerPlayer owner, PlayerIdentity id, ShadeEntity shade) {
		PlayerIdentity.Grave grave = null;
		for (PlayerIdentity.Grave g : id.graves) {
			if (g.boss != null && g.boss.equals(shade.getUUID())) {
				grave = g;
			}
		}
		if (grave == null || !(shade.level() instanceof ServerLevel level)) {
			return;
		}
		long now = SelfMade.now();
		grave.defeated = true;
		grave.boss = null;

		id.traits.put(Trait.FEAR.name(), id.get(Trait.FEAR) * 0.5);
		id.gain(Trait.WILL, 8.0, null, now);
		id.gain(Trait.RESOLVE, 5.0, null, now);
		owner.giveExperiencePoints(300);
		Memories.queue(id, Memory.MORTALITY, true);

		BlockPos at = new BlockPos(grave.x, grave.y + 1, grave.z);
		int given = 0;
		for (String itemId : grave.gear) {
			if (given >= 2) {
				break;
			}
			ItemStack worn = worn(itemId);
			if (worn != null) {
				Block.popResource(level, at, worn);
				given++;
			}
		}

		final PlayerIdentity.Grave done = grave;
		id.echoes.removeIf(e -> e.dim.equals(done.dim) && Math.abs(e.x - done.x) <= RADIUS && Math.abs(e.z - done.z) <= RADIUS);
		for (PlayerIdentity.Fixture f : id.fixtures) {
			if (f.kind.equals("grave") && f.dim.equals(done.dim) && Math.abs(f.x - done.x) <= 1 && Math.abs(f.z - done.z) <= 1) {
				f.text = "Here you were laid to rest. You got up.";
			}
		}
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, grave.x + 0.5, grave.y + 1.5, grave.z + 0.5, 60, 1.0, 1.0, 1.0, 0.4);
		SelfMade.whisper(owner, "It was only ever you. Something that was yours comes back to you.");
	}

	/** A piece of old gear that wears out, at a quarter of its life. Null for anything else. */
	private static ItemStack worn(String itemId) {
		Identifier key = Identifier.tryParse(itemId);
		if (key == null) {
			return null;
		}
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(key);
		if (item.isEmpty()) {
			return null;
		}
		ItemStack stack = new ItemStack(item.get());
		if (!stack.isDamageableItem()) {
			return null;
		}
		stack.setDamageValue(stack.getMaxDamage() * 3 / 4);
		return stack;
	}

	/** "fall" -> "fall", "inFire" -> "in fire"; a mob's name stays as it is. */
	static String nice(String by) {
		if (by == null || by.isEmpty()) {
			return "something";
		}
		return by.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
	}

	static List<String> copy(List<String> in) {
		return new ArrayList<>(in);
	}
}
