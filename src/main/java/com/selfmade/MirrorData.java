package com.selfmade;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the Identity Mirror shows. The server decides this (including when the mirror lies) and sends it as one string,
 * so the client never sees the real trait scores. Levels are 0-100 and indexed by {@link Trait#ordinal()}.
 */
public final class MirrorData {
	public final int[] levels = new int[8];
	public int glitch;        // 0-100: how badly the reflection misbehaves
	public boolean behind;    // something is standing behind you
	public int lead = -1;     // trait ordinal the mirror believes is strongest, or -1
	public int future = -1;   // IdentityPath ordinal, or -1
	public int could = -1;    // IdentityPath ordinal, or -1
	public final List<String> lines = new ArrayList<>();

	/** Squashes a raw trait score into 0-100: slow at first, flattening out so nobody ever "maxes out". */
	public static int level100(double score) {
		return (int) Math.round(100.0 * (1.0 - Math.exp(-Math.max(0.0, score) / 45.0)));
	}

	public String encode() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < levels.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(levels[i]);
		}
		sb.append('|').append(glitch).append('|').append(behind ? 1 : 0).append('|').append(lead)
				.append('|').append(future).append('|').append(could).append('|').append(String.join("\n", lines));
		return sb.toString();
	}

	public static MirrorData decode(String text) {
		MirrorData d = new MirrorData();
		String[] parts = text.split("\\|", 7);
		if (parts.length < 7) {
			d.lines.add("The glass is clouded.");
			return d;
		}
		String[] lv = parts[0].split(",");
		for (int i = 0; i < d.levels.length && i < lv.length; i++) {
			d.levels[i] = clamp(parse(lv[i]), 0, 100);
		}
		d.glitch = clamp(parse(parts[1]), 0, 100);
		d.behind = parse(parts[2]) == 1;
		d.lead = parse(parts[3]);
		d.future = parse(parts[4]);
		d.could = parse(parts[5]);
		if (!parts[6].isEmpty()) {
			for (String line : parts[6].split("\n")) {
				d.lines.add(line);
			}
		}
		return d;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/**
	 * Builds a reading. The mirror lies sometimes, and the prouder or more frightened you are, the more often.
	 * A lie is consistent (the picture and the words agree), so you cannot tell from the screen alone.
	 */
	public static MirrorData build(PlayerIdentity id, Random rnd) {
		MirrorData d = new MirrorData();
		Trait[] traits = Trait.values();
		double total = 0.0;
		for (Trait t : traits) {
			d.levels[t.ordinal()] = level100(id.get(t));
			total += id.get(t);
		}

		double ego = d.levels[Trait.EGO.ordinal()] / 100.0;
		double fear = d.levels[Trait.FEAR.ordinal()] / 100.0;
		boolean lies = total >= 1.0 && rnd.nextDouble() < Math.min(0.6, 0.10 + 0.45 * ego + 0.25 * fear);

		if (lies) {
			if (ego >= fear) {
				// the flattering lie: you are prouder and harder than you are, and less afraid
				d.levels[Trait.EGO.ordinal()] = Math.min(100, d.levels[Trait.EGO.ordinal()] + 35);
				d.levels[Trait.VIOLENCE.ordinal()] = Math.min(100, d.levels[Trait.VIOLENCE.ordinal()] + 25);
				d.levels[Trait.FEAR.ordinal()] /= 3;
			} else {
				// the frightened lie: two parts of you swapped, and a little more dread than there is
				int x = rnd.nextInt(8);
				int y = rnd.nextInt(8);
				int tmp = d.levels[x];
				d.levels[x] = d.levels[y];
				d.levels[y] = tmp;
				d.levels[Trait.FEAR.ordinal()] = Math.min(100, d.levels[Trait.FEAR.ordinal()] + 30);
			}
		}

		d.glitch = (int) Math.round(100.0 * Math.min(1.0, 0.10 + 0.6 * fear + (lies ? 0.3 : 0.0)));

		// Rank by what is shown, so the words always agree with the picture.
		Integer[] order = new Integer[8];
		for (int i = 0; i < 8; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (p, q) -> Integer.compare(d.levels[q], d.levels[p]));
		if (d.levels[order[0]] >= 3) {
			d.lead = order[0];
		}

		if (d.lead < 0) {
			d.lines.add("Nothing follows you. Not yet.");
		} else {
			d.lines.add(traits[order[0]].omen);
			if (d.levels[order[1]] >= 12) {
				d.lines.add(traits[order[1]].omen);
			}
		}

		IdentityPath[] paths = IdentityPath.ranked(id);
		if (paths[0].score(id) >= 4.0) {
			d.future = paths[0].ordinal();
			d.lines.add(paths[0].future);
			IdentityPath other = paths[1].score(id) >= 2.0 ? paths[1] : paths[0].opposite();
			d.could = other.ordinal();
			d.lines.add(other.could);
		}

		if (id.echoes.size() >= 3) {
			d.lines.add("Somewhere, you are still dying.");
		} else if (!id.echoes.isEmpty()) {
			d.lines.add("The ground remembers where you fell.");
		}

		if (!id.memoriesEarned.isEmpty() && d.lines.size() < 7) {
			// a memory you are carrying, in its own words
			List<String> carried = new ArrayList<>(id.memoriesEarned);
			Memory memory = Memory.byName(carried.get(rnd.nextInt(carried.size())));
			if (memory != null) {
				d.lines.add(memory.line);
			}
		}

		double behindChance = Math.min(0.5, 0.04 + 0.4 * fear + (id.echoes.size() >= 3 ? 0.2 : 0.0));
		d.behind = rnd.nextDouble() < behindChance;
		if (d.behind && rnd.nextBoolean()) {
			d.lines.add("Something is standing behind you.");
		}
		PathPowers.shape(id, d.levels, 100); // the glass shows the shape your path has given you (the words stay honest)
		return d;
	}
}
