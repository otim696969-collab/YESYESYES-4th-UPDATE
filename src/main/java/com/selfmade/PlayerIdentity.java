package com.selfmade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the mod remembers about one player. Public non-transient fields are saved to JSON. */
public class PlayerIdentity {

	/** One recorded death. This is the seed of the future "Grave of You". */
	public static class DeathEcho {
		public String dim;
		public int x, y, z;
		public long when;
		public String cause;
		/** What killed you: a mob's name or a damage type like "fall". */
		public String by;
		/** Registry ids of what you were holding and wearing. */
		public List<String> gear = new ArrayList<>();

		public DeathEcho() {
		}

		public DeathEcho(String dim, int x, int y, int z, long when, String cause) {
			this.dim = dim;
			this.x = x;
			this.y = y;
			this.z = z;
			this.when = when;
			this.cause = cause;
		}
	}

	/** A Grave of You: formed after repeated deaths in one place (see Graves). */
	public static class Grave {
		public String dim;
		public int x, y, z;
		public boolean defeated;
		public List<String> gear = new ArrayList<>();
		public List<String> names = new ArrayList<>();
		public transient UUID boss;
	}

	/** Something your identity left standing in the world: a statue, a claim marker, a grave's name (see Fixtures). */
	public static class Fixture {
		public String dim;
		public int x, y, z;
		public String kind; // "statue", "claim" or "grave"
		public String text;
		public transient UUID entity;

		public Fixture() {
		}

		public Fixture(String dim, int x, int y, int z, String kind, String text) {
			this.dim = dim;
			this.x = x;
			this.y = y;
			this.z = z;
			this.kind = kind;
			this.text = text;
		}
	}

	/** Exponentially decaying counter, used to give repeated identical actions diminishing returns. */
	private static class Heat {
		double value;
		long lastTick;
	}

	/** Heat halves every minute (1200 ticks) of not repeating the same action. */
	private static final double HEAT_HALF_LIFE_TICKS = 1200.0;

	// ---- saved ----
	public Map<String, Double> traits = new LinkedHashMap<>();
	public List<DeathEcho> echoes = new ArrayList<>();
	public Set<Long> visitedCells = new HashSet<>();
	public Set<String> omensSeen = new HashSet<>();
	/** Memory fragments earned so far (names of {@link Memory}). */
	public Set<String> memoriesEarned = new HashSet<>();
	/** Memory Altars: "dim:x:y:z" -> the memories placed in it. */
	public Map<String, List<String>> altars = new LinkedHashMap<>();
	public List<Grave> graves = new ArrayList<>();
	public List<Fixture> fixtures = new ArrayList<>();
	/** Fear watchers standing in the world: "dim,x,y,z" of the base block. */
	public List<String> watchers = new ArrayList<>();
	public int kills;
	/** The Identity Path you currently lean toward (name of {@link IdentityPath}), or null. */
	public String path;
	/** Powers you have already been told about (names of PathPowers.Power). */
	public Set<String> powersSeen = new HashSet<>();
	/** Which of your active powers the mirror reaches for. */
	public int selected;
	/** Where you first arrived in each dimension: dim -> "x,y,z". */
	public Map<String, String> arrivals = new LinkedHashMap<>();
	/** Conqueror territories: "dim,x,y,z". */
	public List<String> territories = new ArrayList<>();

	// ---- this session only (not saved) ----
	public transient DeathEcho pendingReturn;
	public transient long pendingFromTick;
	public transient long pendingUntilTick;
	public transient boolean wasLow;
	public transient long closeCallTick; // 0 = no close call being watched
	public transient double closeCallX;
	public transient double closeCallY;
	public transient double closeCallZ;
	public transient long lastHauntMessageTick = -100000L;
	public transient List<Memories.Award> pendingAwards = new ArrayList<>();
	public transient List<String> pendingWhispers = new ArrayList<>();
	public transient List<String> lastGear = new ArrayList<>();
	public transient Map<String, Long> cooldowns = new HashMap<>();
	public transient List<UUID> constructs = new ArrayList<>();
	public transient String lastDim;
	public transient long cheatReadyTick;
	public transient long lastGhostTick;
	public transient long lastStatueTick;
	public transient long lastOtherTick;
	public transient long lastWatcherTick;
	public transient UUID otherYou;
	public transient List<UUID> ghosts = new ArrayList<>();
	public transient boolean shadowInit;
	public transient double shadowX, shadowZ, shadowHead;
	public transient double lastPosX, lastPosZ;
	private transient Map<String, Heat> heat = new HashMap<>();

	public double get(Trait trait) {
		return traits.getOrDefault(trait.name(), 0.0);
	}

	/**
	 * Adds to a trait. If a signal key is given, repeating that same signal in quick succession is worth less
	 * each time, so grinding a mob farm moves you far less than a handful of real fights.
	 */
	public void gain(Trait trait, double base, String signal, long nowTick) {
		double scale = 1.0;
		if (signal != null) {
			Heat h = heat.computeIfAbsent(signal, k -> new Heat());
			long dt = Math.max(0L, nowTick - h.lastTick);
			h.value *= Math.pow(0.5, dt / HEAT_HALF_LIFE_TICKS);
			scale = 1.0 / (1.0 + h.value);
			h.value += 1.0;
			h.lastTick = nowTick;
		}
		traits.merge(trait.name(), base * scale, Double::sum);
	}

	/** Slow fade, so old behaviour counts for less than recent behaviour and people can change. */
	public void decay(double factor) {
		traits.replaceAll((k, v) -> v * factor);
	}

	/** Called after loading from JSON: swap Gson's collection types for plain ones and fill any gaps. */
	public void normalize() {
		traits = traits == null ? new LinkedHashMap<>() : new LinkedHashMap<>(traits);
		echoes = echoes == null ? new ArrayList<>() : new ArrayList<>(echoes);
		visitedCells = visitedCells == null ? new HashSet<>() : new HashSet<>(visitedCells);
		omensSeen = omensSeen == null ? new HashSet<>() : new HashSet<>(omensSeen);
		powersSeen = powersSeen == null ? new HashSet<>() : new HashSet<>(powersSeen);
		arrivals = arrivals == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arrivals);
		territories = territories == null ? new ArrayList<>() : new ArrayList<>(territories);
		memoriesEarned = memoriesEarned == null ? new HashSet<>() : new HashSet<>(memoriesEarned);
		altars = altars == null ? new LinkedHashMap<>() : new LinkedHashMap<>(altars);
		altars.replaceAll((k, v) -> v == null ? new ArrayList<>() : new ArrayList<>(v));
		graves = graves == null ? new ArrayList<>() : new ArrayList<>(graves);
		fixtures = fixtures == null ? new ArrayList<>() : new ArrayList<>(fixtures);
		watchers = watchers == null ? new ArrayList<>() : new ArrayList<>(watchers);
		echoes.removeIf(e -> e == null || e.dim == null);
		for (DeathEcho e : echoes) {
			e.gear = e.gear == null ? new ArrayList<>() : new ArrayList<>(e.gear);
		}
		graves.removeIf(g -> g == null || g.dim == null);
		for (Grave g : graves) {
			g.gear = g.gear == null ? new ArrayList<>() : new ArrayList<>(g.gear);
			g.names = g.names == null ? new ArrayList<>() : new ArrayList<>(g.names);
		}
		fixtures.removeIf(f -> f == null || f.dim == null || f.kind == null || f.text == null);
		if (heat == null) {
			heat = new HashMap<>();
		}
		cooldowns = new HashMap<>();
		constructs = new ArrayList<>();
		pendingAwards = new ArrayList<>();
		pendingWhispers = new ArrayList<>();
		lastGear = new ArrayList<>();
		ghosts = new ArrayList<>();
	}
}
