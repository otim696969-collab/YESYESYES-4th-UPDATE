package com.selfmade;

/**
 * The text of the starting book, "Field Notes". Each page is a title, a newline, then the body.
 * It explains how the mod works, but it still never says what any particular player is.
 */
public final class BookText {
	public static final String[] PAGES = {
			"FIELD NOTES\n"
					+ "Whoever left these wanted you to know three things.\n\n"
					+ "You are being followed. It is not hostile. It is you.\n\n"
					+ "This world will never tell you what you are. It will only show you what you have done, and let you read the rest in the glass.\n\n"
					+ "Turn the page.",

			"THE SHADOW\n"
					+ "A small dark thing floats near you. It cannot be touched, only watched.\n\n"
					+ "At first it is barely there. Every kind of thing you do gives it a part: claws for violence, wings for care, no face at all for fear. It also decides where it hovers: ahead of you, behind you, above you.\n\n"
					+ "Learn to read it. Other players can read theirs, and yours.",

			"THE MIRROR\n"
					+ "Right-click the Identity Mirror. It shows the shadow as the glass sees it.\n\n"
					+ "It lies sometimes, more when you are proud or afraid. A smaller, fainter figure is who you could have been. A line about \"far ahead\" hints where you are going. Sometimes something tall stands behind you.\n\n"
					+ "If the picture and the words disagree, trust neither.",

			"WHAT IT WATCHES\n"
					+ "Not what you say. What you do.\n\n"
					+ "Who you kill, and what for. Whether you run or stand after a near thing. Whether you go back for the place you died. Whether you walk where you have never walked. Whether someone lives because you were there. What you do when something that trusted you is lost.\n\n"
					+ "Nothing is counted forever. Old habits fade. People change.",

			"MEMORIES\n"
					+ "Some things that happen to you leave a shard: your first end, your first time in the red country, a giant that fell, a stranger you saved, a loved thing lost.\n\n"
					+ "Build an altar: a lectern standing on crying obsidian. Right-click it holding a shard to set it in (three at most). Sneak with an empty hand to take one back.\n\n"
					+ "Two memories side by side make something new. Which two is up to you.",

			"DEATH\n"
					+ "Dying here does not reset you. It accumulates.\n\n"
					+ "Die three times in one place and it starts to remember you. Sometimes a version of you walks out of it. Die five times and something puts up a stone with your name on it.\n\n"
					+ "Go back to the stone and what sleeps there will wake. If you win, part of you comes back with you.",

			"THE WORLD ANSWERS\n"
					+ "Fear and pride do not stay inside you.\n\n"
					+ "Afraid, you will find the shadows wander, the monsters come out wrong, and small dark figures standing where nothing stood.\n\n"
					+ "Proud, you will find statues, your name on the ground, villagers who cannot stop looking. Too proud, and someone better than you turns up. They are not better. They are only certain.",

			"WAYS OF BECOMING\n"
					+ "There are five ways a person can lean, and each is two parts of you meeting. The mirror hints at them. It never names them.\n\n"
					+ "Lean far enough and you are given gifts. Some simply happen. Others you must reach for: sneak and right-click the mirror. If you have more than one, look down at your own feet while you do it, to choose.\n\n"
					+ "Gifts need rest after use. Lean another way and the old gifts go quiet.",

			"THE FIVE\n"
					+ "The one who stands in the doorway.\n"
					+ "The one who walks off the map.\n"
					+ "The one who takes the ground.\n"
					+ "The one who is still here.\n"
					+ "The one who builds things that breathe.\n\n"
					+ "You will know which, because the shadow will have changed shape, and because the things you can do will have started to feel like yours.",

			"THE LAST PAGE\n"
					+ "Late, if you get that far, you will learn the shadow was never separate.\n\n"
					+ "Until then, look at what you have done. That is who you are.\n\n"
					+ "(For whoever is testing: /eidolon set <trait> <amount>, reset, debug, path, rest, book, memory <name>, grave, other, statue. Lock these down before anyone else plays.)"
	};

	private BookText() {
	}
}
