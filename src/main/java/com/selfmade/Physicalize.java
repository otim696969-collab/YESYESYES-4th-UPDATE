package com.selfmade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * "Identity should affect the actual world."
 *
 * Fear: a shadow that moves by itself, darkness that follows you, hostile mobs that come out the wrong size,
 * the Eidolon's reflection stepping the wrong way (see EidolonEntity), and watchers: small dark effigies that only
 * appear while you are afraid and are taken down again when the fear fades.
 *
 * Ego: statues of you, your name on ground you conquered, villagers who cannot stop looking at you, and past a
 * certain point, The Other You.
 */
final class Physicalize {
	/** Mobs already made the wrong size (kept in memory only; a restart may warp a mob a second time). */
	private static final java.util.Set<java.util.UUID> WARPED = new java.util.HashSet<>();
	private static final Random RANDOM = new Random();

	private Physicalize() {
	}

	// ------------------------------------------------------------------ every sample (twice a second)

	/** A shadow that wanders on its own. It only moves while you stand still, and it keeps its distance. */
	static void visual(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		double fear = id.get(Trait.FEAR);
		double mx = p.getX() - id.lastPosX;
		double mz = p.getZ() - id.lastPosZ;
		id.lastPosX = p.getX();
		id.lastPosZ = p.getZ();
		if (fear < 20.0) {
			return;
		}
		if (!id.shadowInit) {
			double a = RANDOM.nextDouble() * Math.PI * 2.0;
			id.shadowX = p.getX() + Math.cos(a) * 7.0;
			id.shadowZ = p.getZ() + Math.sin(a) * 7.0;
			id.shadowHead = RANDOM.nextDouble() * Math.PI * 2.0;
			id.shadowInit = true;
		}
		boolean moving = mx * mx + mz * mz > 0.02;
		id.shadowHead += (RANDOM.nextDouble() - 0.5) * 0.9;
		double step = moving ? 0.0 : 0.4;
		id.shadowX += Math.cos(id.shadowHead) * step;
		id.shadowZ += Math.sin(id.shadowHead) * step;

		double dx = id.shadowX - p.getX();
		double dz = id.shadowZ - p.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > 14.0) {
			id.shadowHead = Math.atan2(-dz, -dx); // drift back toward you
		} else if (dist < 3.5) {
			id.shadowHead = Math.atan2(dz, dx);   // and never come too close
		}

		BlockPos ground = Spots.groundNear(level, (int) Math.floor(id.shadowX), p.getBlockY(), (int) Math.floor(id.shadowZ));
		int count = 2 + (int) (fear / 25.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, id.shadowX, ground.getY() + 1.0, id.shadowZ, count, 0.18, 0.9, 0.18, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, id.shadowX, ground.getY() + 0.6, id.shadowZ, 1, 0.15, 0.6, 0.15, 0.0);
	}

	// ------------------------------------------------------------------ every second

	static void tick(ServerPlayer p, ServerLevel level, PlayerIdentity id, long now) {
		double fear = id.get(Trait.FEAR);
		double ego = id.get(Trait.EGO);

		if (fear >= 50.0) {
			// the dark does not stay where it was
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 3, 1.2, 0.7, 1.2, 0.0);
			if (RANDOM.nextInt(45) == 0) {
				Fx.give(p, Fx.Kind.DARKNESS, 160, 0);
			}
		}
		if (fear >= 35.0 && now % 60 == 0) {
			distort(p, level, fear);
		}
		if (fear >= 45.0) {
			watchers(p, level, id, now);
		} else if (fear < 25.0 && !id.watchers.isEmpty()) {
			clearWatchers(p, level, id);
		}

		if (ego >= 25.0 && now % 100 == 0) {
			villagers(p, level, id);
		}
		if (ego >= 30.0) {
			maybeStatue(p, level, id, now);
		}
		if (ego >= 70.0) {
			maybeOtherYou(p, level, id, now);
		}
		if (p.getArmorValue() >= 18 && now % 1200 == 0) {
			id.gain(Trait.EGO, 0.4, "preen", now); // admiring yourself in good armour
		}
	}

	// ------------------------------------------------------------------ fear

	/** Hostile mobs near a frightened player come out the wrong size (once each). Everyone sees it. */
	private static void distort(ServerPlayer p, ServerLevel level, double fear) {
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24.0),
				m -> m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER
						&& m.getMaxHealth() < 100.0f && !WARPED.contains(m.getUUID()));
		double spread = Math.min(0.55, 0.15 + fear / 160.0);
		int done = 0;
		for (Mob m : mobs) {
			if (done++ >= 4) {
				break;
			}
			if (WARPED.size() > 5000) {
				WARPED.clear();
			}
			WARPED.add(m.getUUID());
			AttributeInstance scale = m.getAttribute(Attributes.SCALE);
			if (scale != null) {
				double sign = RANDOM.nextBoolean() ? 1.0 : -1.0;
				scale.setBaseValue(Math.max(0.6, 1.0 + sign * (0.2 + RANDOM.nextDouble() * spread)));
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(),
					6, 0.3, 0.4, 0.3, 0.0);
		}
	}

	private static Direction facing(BlockPos from, ServerPlayer toward) {
		double dx = toward.getX() - (from.getX() + 0.5);
		double dz = toward.getZ() - (from.getZ() + 0.5);
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** A watcher: two blocks of dark brick and a carved face turned toward you. Never more than three. */
	private static void watchers(ServerPlayer p, ServerLevel level, PlayerIdentity id, long now) {
		if (id.watchers.size() >= 3 || now - id.lastWatcherTick < 20L * 60 * 4 || RANDOM.nextInt(15) != 0) {
			return;
		}
		BlockPos at = Spots.ringSpot(level, p, 18.0, 28.0, RANDOM);
		if (!level.hasChunkAt(at)) {
			return;
		}
		for (int h = 0; h < 3; h++) {
			if (!level.getBlockState(at.above(h)).isAir()) {
				return;
			}
		}
		BlockState brick = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		level.setBlock(at, brick, 3);
		level.setBlock(at.above(), brick, 3);
		level.setBlock(at.above(2), Blocks.CARVED_PUMPKIN.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, facing(at, p)), 3);
		id.watchers.add(SelfMade.dimensionOf(p) + "," + at.getX() + "," + at.getY() + "," + at.getZ());
		id.lastWatcherTick = now;
		if (id.omensSeen.add("first-watcher")) {
			SelfMade.whisper(p, "There was nothing standing there a moment ago.");
		}
	}

	/** The fear has passed, so the watchers go. (Only ones in loaded ground in this dimension; the rest wait for you.) */
	private static void clearWatchers(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		String dim = SelfMade.dimensionOf(p);
		Iterator<String> it = id.watchers.iterator();
		while (it.hasNext()) {
			String[] t = it.next().split(",");
			if (t.length != 4 || !t[0].equals(dim)) {
				continue;
			}
			BlockPos base;
			try {
				base = new BlockPos(Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]));
			} catch (NumberFormatException e) {
				it.remove();
				continue;
			}
			if (!level.hasChunkAt(base)) {
				continue;
			}
			for (int h = 0; h < 3; h++) {
				BlockPos pos = base.above(h);
				BlockState state = level.getBlockState(pos);
				if (state.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || state.is(Blocks.CARVED_PUMPKIN)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				}
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5,
					20, 0.3, 0.8, 0.3, 0.01);
			it.remove();
		}
	}

	// ------------------------------------------------------------------ ego

	/** Villagers near you turn to look. Angry ones if you are a brute, adoring ones if you are kind. */
	private static void villagers(ServerPlayer p, ServerLevel level, PlayerIdentity id) {
		List<Mob> folk = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(10.0),
				m -> m.isAlive() && (m.getType() == EntityTypes.VILLAGER || m.getType() == EntityTypes.WANDERING_TRADER));
		boolean cruel = id.get(Trait.VIOLENCE) > id.get(Trait.EMPATHY);
		int done = 0;
		for (Mob v : folk) {
			if (done++ >= 4) {
				break;
			}
			v.getLookControl().setLookAt(p, 30.0f, 30.0f);
			level.sendParticles(cruel ? ParticleTypes.ANGRY_VILLAGER : ParticleTypes.HAPPY_VILLAGER,
					v.getX(), v.getY() + v.getBbHeight() + 0.2, v.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
		}
	}

	private static void maybeStatue(ServerPlayer p, ServerLevel level, PlayerIdentity id, long now) {
		if (now - id.lastStatueTick < 20L * 60 * 12 || RANDOM.nextInt(30) != 0) {
			return;
		}
		spawnStatue(p, level, id, now);
	}

	/** A statue of you, a little way off, facing nothing in particular. Also used by the dev command. */
	static void spawnStatue(ServerPlayer p, ServerLevel level, PlayerIdentity id, long now) {
		BlockPos at = Spots.ringSpot(level, p, 9.0, 18.0, RANDOM);
		if (!level.hasChunkAt(at)) {
			return;
		}
		id.lastStatueTick = now;
		Fixtures.add(id, new PlayerIdentity.Fixture(SelfMade.dimensionOf(p), at.getX(), at.getY(), at.getZ(),
				"statue", p.getName().getString()), level);
		if (id.omensSeen.add("first-statue")) {
			SelfMade.whisper(p, "Someone has made a likeness of you. It is very flattering.");
		}
	}

	/**
	 * Your name on ground you conquered: a block of gold and a floating name. Placed where a boss fell,
	 * and after every 25th hostile kill once your Ego is up.
	 */
	static void claim(ServerPlayer p, ServerLevel level, PlayerIdentity id, LivingEntity victim) {
		BlockPos at = Spots.groundNear(level, victim.getBlockX(), victim.getBlockY(), victim.getBlockZ());
		if (level.getBlockState(at).isAir()) {
			level.setBlock(at, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
		}
		Fixtures.add(id, new PlayerIdentity.Fixture(SelfMade.dimensionOf(p), at.getX(), at.getY(), at.getZ(),
				"claim", "Conquered by " + p.getName().getString()), level);
		if (id.omensSeen.add("first-claim")) {
			SelfMade.whisper(p, "You have put your name on the ground. The ground has noticed.");
		}
	}

	private static void maybeOtherYou(ServerPlayer p, ServerLevel level, PlayerIdentity id, long now) {
		if (id.otherYou != null && level.getEntity(id.otherYou) instanceof ShadeEntity existing && !existing.isRemoved()) {
			return;
		}
		if (now - id.lastOtherTick < 20L * 60 * 15 || RANDOM.nextInt(40) != 0) {
			return;
		}
		spawnOtherYou(p, level, id, now);
	}

	/** The hostile manifestation of your idealised self. Also used by the dev command. */
	static void spawnOtherYou(ServerPlayer p, ServerLevel level, PlayerIdentity id, long now) {
		BlockPos at = Spots.ringSpot(level, p, 7.0, 11.0, RANDOM);
		ShadeEntity other = new ShadeEntity(ModEntities.SHADE, level);
		other.setup(ShadeEntity.Kind.OTHER, p.getUUID(), id, "The Other You");
		other.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.addFreshEntity(other);
		id.otherYou = other.getUUID();
		id.lastOtherTick = now;
		SelfMade.whisper(p, "You were so sure of yourself that someone else turned up.");
	}

	/** You beat the better version of you. It costs your pride half its weight. */
	static void otherYouSlain(ServerPlayer p, PlayerIdentity id) {
		id.traits.put(Trait.EGO.name(), id.get(Trait.EGO) * 0.5);
		id.gain(Trait.RESOLVE, 4.0, null, SelfMade.now());
		Memories.queue(id, Memory.TRIUMPH, false);
		SelfMade.whisper(p, "It was only the best of you. It was never all of you.");
	}
}
