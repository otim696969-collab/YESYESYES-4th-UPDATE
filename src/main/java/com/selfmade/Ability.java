package com.selfmade;

/** What two memories become when they share an altar. Every pair of the five memories makes exactly one. */
public enum Ability {
	ASHEN_RETURN(Memory.MORTALITY, Memory.INFERNO, "Ashen Return",
			"Death will let go of you once, and leave ash on your hands."),
	DEATHBED_FURY(Memory.MORTALITY, Memory.TRIUMPH, "Deathbed Fury",
			"The closer the end comes, the less it frightens you."),
	HOSPICE(Memory.MORTALITY, Memory.COMPASSION, "Hospice",
			"Those near you stop dying quite so quickly."),
	WAKE(Memory.MORTALITY, Memory.GRIEF, "Wake",
			"The places you fell have started to keep watch over you."),
	TRIUMPHANT_FLAME(Memory.INFERNO, Memory.TRIUMPH, "Triumphant Flame",
			"Fire has learned your name, and bows."),
	EMBER_HEART(Memory.INFERNO, Memory.COMPASSION, "Ember Heart",
			"Something warm has settled behind your ribs."),
	CINDER_SIGHT(Memory.INFERNO, Memory.GRIEF, "Cinder Sight",
			"The dark has stopped hiding things from you."),
	SHEPHERD(Memory.TRIUMPH, Memory.COMPASSION, "Shepherd",
			"Others walk faster when you are near."),
	HOLLOW_CROWN(Memory.TRIUMPH, Memory.GRIEF, "Hollow Crown",
			"You are stronger. You are also a little more sure of it."),
	MOURNING_VEIL(Memory.COMPASSION, Memory.GRIEF, "Mourning Veil",
			"Grief has made a cloak of you, and it fits.");

	public final Memory a;
	public final Memory b;
	public final String label;
	public final String line;

	Ability(Memory a, Memory b, String label, String line) {
		this.a = a;
		this.b = b;
		this.label = label;
		this.line = line;
	}

	/** The ability two memories make, in either order, or null if they are the same memory. */
	public static Ability of(Memory x, Memory y) {
		for (Ability ability : values()) {
			if ((ability.a == x && ability.b == y) || (ability.a == y && ability.b == x)) {
				return ability;
			}
		}
		return null;
	}
}
