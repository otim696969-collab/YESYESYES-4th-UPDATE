package com.selfmade;

import java.util.Arrays;
import java.util.Comparator;

/**
 * The five Identity Paths. A path is not chosen, it is what two of your traits add up to.
 * The game never names a path to the player; the mirror only hints at the "future" and the "could have been".
 */
public enum IdentityPath {
	PROTECTOR(Trait.EMPATHY, Trait.RESOLVE,
			"Far ahead, something tall stands in a doorway so that other people can walk through.",
			"The one you could have been stands between you and the dark, and does not flinch."),
	WANDERER(Trait.CURIOSITY, Trait.RESOLVE,
			"Far ahead, a figure with too many eyes is already looking at the place you are going.",
			"The one you could have been never came back from the horizon, and was glad."),
	CONQUEROR(Trait.EGO, Trait.VIOLENCE,
			"Far ahead, someone wears a crown made of everything they have broken.",
			"The one you could have been is crowned in iron and no longer remembers your name."),
	SURVIVOR(Trait.FEAR, Trait.RESOLVE,
			"Far ahead, a thin thing crouches in the dark and refuses to die.",
			"The one you could have been is only bone and stubbornness, and still walking."),
	CREATOR(Trait.CURIOSITY, Trait.EMPATHY,
			"Far ahead, hands that are not quite hands are building something that breathes.",
			"The one you could have been is making a friend out of rubble, and humming.");

	public final Trait a;
	public final Trait b;
	public final String future;
	public final String could;

	IdentityPath(Trait a, Trait b, String future, String could) {
		this.a = a;
		this.b = b;
		this.future = future;
		this.could = could;
	}

	/** Needs both traits: a player who is all Empathy and no Resolve is not yet a Protector. */
	public double score(PlayerIdentity id) {
		return Math.sqrt(Math.max(0.0, id.get(a)) * Math.max(0.0, id.get(b)));
	}

	public IdentityPath opposite() {
		return switch (this) {
			case PROTECTOR, CREATOR -> CONQUEROR;
			case CONQUEROR -> PROTECTOR;
			case WANDERER -> SURVIVOR;
			case SURVIVOR -> WANDERER;
		};
	}

	/** All paths, best first. */
	public static IdentityPath[] ranked(PlayerIdentity id) {
		IdentityPath[] all = values();
		Arrays.sort(all, Comparator.comparingDouble((IdentityPath p) -> p.score(id)).reversed());
		return all;
	}
}
