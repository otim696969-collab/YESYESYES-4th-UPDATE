package com.selfmade;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Every potion effect this mod uses lives here, so a renamed Minecraft constant is a one-line fix. */
final class Fx {
	enum Kind {
		SPEED(MobEffects.SPEED),
		STRENGTH(MobEffects.STRENGTH),
		RESISTANCE(MobEffects.RESISTANCE),
		REGENERATION(MobEffects.REGENERATION),
		FIRE_RESISTANCE(MobEffects.FIRE_RESISTANCE),
		NIGHT_VISION(MobEffects.NIGHT_VISION),
		ABSORPTION(MobEffects.ABSORPTION),
		DARKNESS(MobEffects.DARKNESS),
		SLOWNESS(MobEffects.SLOWNESS),
		WEAKNESS(MobEffects.WEAKNESS),
		GLOWING(MobEffects.GLOWING),
		INVISIBILITY(MobEffects.INVISIBILITY);

		final Holder<MobEffect> effect;

		Kind(Holder<MobEffect> effect) {
			this.effect = effect;
		}
	}

	private Fx() {
	}

	/** Gives a quiet effect (no particles swirling around the player). */
	static void give(LivingEntity target, Kind kind, int ticks, int amplifier) {
		target.addEffect(new MobEffectInstance(kind.effect, ticks, amplifier, true, false));
	}
}
