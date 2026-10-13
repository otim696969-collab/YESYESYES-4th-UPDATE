package com.selfmade;

import java.util.Locale;

/** The five Memory Fragments. Each is earned once per player, by something that actually happened to them. */
public enum Memory {
	MORTALITY("fragment_mortality", "Fragment of Mortality",
			"You ended, once. Something of it stayed behind in your hand.",
			"You remember the first time it ended."),
	INFERNO("fragment_inferno", "Fragment of the Inferno",
			"The red country remembered you leaving. It gave you something to carry.",
			"You remember a sky made of fire."),
	TRIUMPH("fragment_triumph", "Fragment of Triumph",
			"Something very large stopped moving. What it left behind is warm.",
			"You remember what it felt like to win."),
	COMPASSION("fragment_compassion", "Fragment of Compassion",
			"Someone is alive because you were there. It left a mark, and the mark is soft.",
			"You remember a hand held out."),
	GRIEF("fragment_grief", "Fragment of Grief",
			"Something that trusted you is gone. The weight of it has a shape now.",
			"You remember something that followed you home, once.");

	public final String itemName;
	public final String display;
	/** Whispered when the fragment forms. */
	public final String born;
	/** Shown by the mirror once you own it. */
	public final String line;

	Memory(String itemName, String display, String born, String line) {
		this.itemName = itemName;
		this.display = display;
		this.born = born;
		this.line = line;
	}

	public static Memory byName(String name) {
		if (name == null) {
			return null;
		}
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
