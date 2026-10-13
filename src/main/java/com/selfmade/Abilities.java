package com.selfmade;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/** What the memories on your altars do for you. Run once a second per player. */
final class Abilities {
	private static final long CHEAT_DEATH_COOLDOWN = 20L * 60 * 10; // ten minutes

	private Abilities() {
	}

	static void tick(ServerPlayer p, ServerLevel level, PlayerIdentity id, long now) {
		Set<Ability> has = Memories.unlocked(id);
		if (has.isEmpty()) {
			return;
		}
		boolean nether = SelfMade.dimensionOf(p).equals("nether");
		float hp = p.getHealth();
		float max = p.getMaxHealth();

		if (has.contains(Ability.DEATHBED_FURY) && hp <= max * 0.35f) {
			Fx.give(p, Fx.Kind.STRENGTH, 60, 1);
			Fx.give(p, Fx.Kind.RESISTANCE, 60, 0);
		}
		if (has.contains(Ability.MOURNING_VEIL) && hp <= max * 0.5f) {
			Fx.give(p, Fx.Kind.RESISTANCE, 80, 0);
		}
		if (has.contains(Ability.WAKE)) {
			String dim = SelfMade.dimensionOf(p);
			for (PlayerIdentity.DeathEcho echo : id.echoes) {
				if (echo.dim.equals(dim) && p.distanceToSqr(echo.x + 0.5, echo.y, echo.z + 0.5) < 8.0 * 8.0) {
					Fx.give(p, Fx.Kind.REGENERATION, 60, 0);
					Fx.give(p, Fx.Kind.RESISTANCE, 60, 0);
					break;
				}
			}
		}
		if (has.contains(Ability.TRIUMPHANT_FLAME)) {
			Fx.give(p, Fx.Kind.FIRE_RESISTANCE, 80, 0);
			if (nether) {
				Fx.give(p, Fx.Kind.STRENGTH, 80, 0);
			}
		}
		if (has.contains(Ability.EMBER_HEART) && now % 1200 == 0) {
			Fx.give(p, Fx.Kind.ABSORPTION, 2400, 0);
		}
		if (has.contains(Ability.CINDER_SIGHT) && (nether || p.getY() < 40.0)) {
			Fx.give(p, Fx.Kind.NIGHT_VISION, 400, 0);
		}
		if (has.contains(Ability.HOLLOW_CROWN)) {
			Fx.give(p, Fx.Kind.STRENGTH, 60, 0);
			id.gain(Trait.EGO, 0.005, null, now); // it is not free
		}

		boolean hospice = has.contains(Ability.HOSPICE);
		boolean shepherd = has.contains(Ability.SHEPHERD);
		if (hospice || shepherd) {
			MinecraftServer server = level.getServer();
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (!other.isAlive() || other.level() != p.level()) {
					continue;
				}
				double d = other.distanceToSqr(p);
				if (hospice && d < 10.0 * 10.0) {
					Fx.give(other, Fx.Kind.REGENERATION, 60, 0);
				}
				if (shepherd && d < 16.0 * 16.0) {
					Fx.give(other, Fx.Kind.SPEED, 60, 0);
				}
			}
		}
	}

	/** Called just before a player would die. Returns true if Ashen Return caught them. */
	static boolean tryCheatDeath(ServerPlayer p, PlayerIdentity id, long now) {
		if (now < id.cheatReadyTick || !Memories.unlocked(id).contains(Ability.ASHEN_RETURN)) {
			return false;
		}
		id.cheatReadyTick = now + CHEAT_DEATH_COOLDOWN;
		p.setHealth(6.0f);
		p.clearFire();
		Fx.give(p, Fx.Kind.FIRE_RESISTANCE, 200, 0);
		Fx.give(p, Fx.Kind.RESISTANCE, 60, 2);
		if (p.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.5, 0.8, 0.5, 0.5);
		}
		SelfMade.whisper(p, "It let go. This time.");
		return true;
	}
}
