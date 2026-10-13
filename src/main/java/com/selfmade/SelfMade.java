package com.selfmade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Slices 1 to 3 of "The Self Made Flesh".
 * <ul>
 *   <li>The identity engine: watches what each player does and keeps eight hidden trait scores.</li>
 *   <li>The Eidolon: a real creature that follows you and whose body is built from those scores.</li>
 *   <li>The Identity Mirror: opens a screen showing the Eidolon as the mirror sees it (it sometimes lies).</li>
 *   <li>Death echoes: places you have died slowly start to haunt you, and eventually grow a Grave of You (Graves).</li>
 *   <li>Memory Fragments and Memory Altars: events become items, pairs of items become abilities (Memories, Abilities).</li>
 *   <li>Physicalized identity: Fear and Ego change the actual world (Physicalize, ShadeEntity).</li>
 * </ul>
 * There is no Inner World or final boss yet.
 */
public class SelfMade implements ModInitializer {
	public static final String MOD_ID = "selfmade";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Tuning knobs. These numbers are educated guesses: play, then adjust.
	private static final int SAMPLE_EVERY_TICKS = 10;       // look at each player twice a second
	private static final double OMEN_THRESHOLD = 25.0;      // trait score at which an omen is whispered
	private static final double DECAY_PER_MINUTE = 0.999;   // slow fade of old behaviour (online time only)
	private static final int ECHO_LIMIT = 64;               // remembered deaths per player

	static final IdentityStore STORE = new IdentityStore();
	private static long tick = 0;

	@Override
	public void onInitialize() {
		ModItems.initialize();
		ModEntities.initialize();
		ModNetworking.initialize();
		Memories.init();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			STORE.load(server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID));
			EidolonManager.reset();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TempBlocks.restoreAll(); // put every barrier, bridge and tunnel back before the world is saved
			STORE.save();
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register(SelfMade::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(SelfMade::onDeath);
		ServerTickEvents.END_SERVER_TICK.register(SelfMade::onTick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				registerCommands(dispatcher));
		LOGGER.info("The Self Made Flesh: identity engine online. Something is watching what you do.");
	}

	// ------------------------------------------------------------------ events

	/** Runs just before a player would die: Ashen Return may catch them, otherwise we note what they were wearing. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player) {
			PlayerIdentity id = STORE.get(player.getUUID());
			if (Abilities.tryCheatDeath(player, id, tick) || PathPowers.tryRevive(player, tick)) {
				return false;
			}
			id.lastGear = Memories.gearOf(player);
		}
		return true;
	}

	private static void onDeath(LivingEntity victim, DamageSource source) {
		if (victim instanceof ShadeEntity shade) {
			Graves.onShadeDied(shade, victim.level().getServer());
			return;
		}
		Entity attacker = source.getEntity();
		if (victim instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) {
			PlayerIdentity ownerId = STORE.get(owner.getUUID());
			ownerId.gain(Trait.ATTACHMENT, 3.0, "pet-lost", tick);
			Memories.queue(ownerId, Memory.GRIEF, false);
			later(ownerId, "It was following you. Now it is not.");
		}
		if (attacker instanceof ServerPlayer killer && !(victim instanceof ServerPlayer)) {
			onPlayerKilled(killer, victim);
		}
		if (victim instanceof ServerPlayer player) {
			onPlayerDied(player, attacker, source);
		}
	}

	private static void onPlayerKilled(ServerPlayer killer, LivingEntity victim) {
		PlayerIdentity id = STORE.get(killer.getUUID());
		EntityType<?> type = victim.getType();

		if (type == EntityTypes.VILLAGER || type == EntityTypes.WANDERING_TRADER || type == EntityTypes.IRON_GOLEM) {
			id.gain(Trait.VIOLENCE, 4.0, "kill:innocent", tick);
		} else if (type.getCategory() == MobCategory.MONSTER) {
			id.gain(Trait.VIOLENCE, 0.6, "kill:monster", tick);
			if (id.closeCallTick != 0 && tick - id.closeCallTick < 600) {
				// They were nearly dead and kept fighting. That is a choice, so it counts for more than the kill.
				id.gain(Trait.RESOLVE, 2.0, "pressed-on", tick);
				id.gain(Trait.WILL, 1.0, "pressed-on", tick);
				id.closeCallTick = 0;
			}
		} else if (type.getCategory() == MobCategory.CREATURE) {
			id.gain(Trait.VIOLENCE, 0.3, "kill:animal", tick);
		}

		if (!(killer.level() instanceof ServerLevel level)) {
			return;
		}

		// Slice 3: a boss falls (the dragon, the wither, the warden): Ego, a memory, and your name on the ground.
		if (victim.getMaxHealth() >= 150.0f) {
			id.gain(Trait.EGO, 6.0, "boss", tick);
			Memories.queue(id, Memory.TRIUMPH, false);
			Physicalize.claim(killer, level, id, victim);
		} else if (type.getCategory() == MobCategory.MONSTER) {
			id.kills++;
			if (id.kills % 25 == 0 && id.get(Trait.EGO) >= 20.0) {
				Physicalize.claim(killer, level, id, victim);
			}
		}

		// Slice 3: killing the thing that was hurting someone else who was close to dying.
		for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
			if (other == killer || !other.isAlive() || other.level() != killer.level()
					|| other.distanceToSqr(killer) > 24.0 * 24.0) {
				continue;
			}
			if (other.getHealth() <= other.getMaxHealth() * 0.4f && other.getLastHurtByMob() == victim) {
				id.gain(Trait.EMPATHY, 4.0, "saved-player", tick);
				Memories.queue(id, Memory.COMPASSION, false);
				later(id, "Someone is still breathing because you were there.");
				STORE.get(other.getUUID()).gain(Trait.ATTACHMENT, 1.0, "was-saved", tick);
				break;
			}
		}
	}

	private static void onPlayerDied(ServerPlayer player, Entity attacker, DamageSource source) {
		PlayerIdentity id = STORE.get(player.getUUID());
		String cause = attacker == null ? "environment" : (attacker instanceof ServerPlayer ? "player" : "creature");

		PlayerIdentity.DeathEcho echo = new PlayerIdentity.DeathEcho(
				dimensionOf(player), player.getBlockX(), player.getBlockY(), player.getBlockZ(),
				System.currentTimeMillis(), cause);
		echo.by = attacker != null ? attacker.getName().getString() : source.getMsgId();
		echo.gear = new ArrayList<>(id.lastGear);
		id.echoes.add(echo);
		while (id.echoes.size() > ECHO_LIMIT) {
			id.echoes.remove(0);
		}

		// Slice 3: your first death is a memory; many deaths in one place become a grave.
		Memories.queue(id, Memory.MORTALITY, false);
		Graves.onDeath(player, id, echo);

		id.gain(Trait.FEAR, 3.0, "death", tick);

		// If they come back for the place where they died, that is Will (see trackReturnToDeathSite).
		id.pendingReturn = echo;
		id.pendingFromTick = tick + 600;             // not within the first 30 seconds
		id.pendingUntilTick = tick + 20L * 60 * 10;  // and not after 10 minutes
		id.closeCallTick = 0;
		id.wasLow = false;
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		if (tick % 5 == 0) {
			TempBlocks.tick(tick);
		}
		if (tick % 20 == 0) {
			PathPowers.healthPass(server); // Protectors share their friends' damage
		}
		if (tick % SAMPLE_EVERY_TICKS == 0) {
			boolean decayNow = tick % 1200 == 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PlayerIdentity id = STORE.get(player.getUUID());
				sample(player);
				EidolonManager.sync(player, id); // spawns the Eidolon if needed and refreshes its looks
				if (decayNow) {
					id.decay(DECAY_PER_MINUTE);
				}
			}
		}
		if (tick % 6000 == 0) {
			STORE.save();
		}
	}

	// ------------------------------------------------------------ helpers shared with the Eidolon

	static Trait leadingTrait(PlayerIdentity id) {
		Trait best = null;
		double bestValue = 1.0;
		for (Trait trait : Trait.values()) {
			double value = id.get(trait);
			if (value >= bestValue) {
				best = trait;
				bestValue = value;
			}
		}
		return best;
	}

	static double totalOf(PlayerIdentity id) {
		double sum = 0.0;
		for (Trait trait : Trait.values()) {
			sum += id.get(trait);
		}
		return sum;
	}

	// ------------------------------------------------- the Identity Mirror

	/** The lines the chat fallback shows (no mod on the client, or /eidolon read): no numbers, just omens. */
	private static List<String> reading(PlayerIdentity id) {
		List<Trait> ranked = new ArrayList<>(List.of(Trait.values()));
		ranked.sort((x, y) -> Double.compare(id.get(y), id.get(x)));

		List<String> lines = new ArrayList<>();
		if (id.get(ranked.get(0)) < 1.0) {
			lines.add("Nothing follows you. Not yet.");
			return lines;
		}
		lines.add(ranked.get(0).omen);
		if (id.get(ranked.get(1)) >= 1.0) {
			lines.add(ranked.get(1).omen);
		}
		return lines;
	}

	/** Used by the Identity Mirror item when you right-click it: opens the mirror screen. */
	static void showReading(ServerPlayer player) {
		PlayerIdentity id = STORE.get(player.getUUID());
		if (!MirrorService.open(player, id)) {
			for (String line : reading(id)) {
				whisper(player, line);
			}
		}
		if (player.level() instanceof ServerLevel level) {
			// a ring of light rising off the glass
			for (int i = 0; i < 24; i++) {
				double angle = i * (Math.PI * 2.0 / 24.0);
				level.sendParticles(ParticleTypes.END_ROD,
						player.getX() + Math.cos(angle) * 1.3, player.getY() + 1.0, player.getZ() + Math.sin(angle) * 1.3,
						1, 0.0, 0.05, 0.0, 0.0);
			}
		}
	}

	/** Puts the item in the inventory, or on the ground at the player's feet if it is full. */
	static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (!player.addItem(stack) && player.level() instanceof ServerLevel level) {
			net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), stack);
		}
	}

	private static void giveMirror(ServerPlayer player) {
		ItemStack mirror = new ItemStack(ModItems.IDENTITY_MIRROR);
		giveOrDrop(player, mirror);
	}

	/** The first time a player is seen: hand them the mirror and explain, once. */
	private static void welcome(ServerPlayer player, PlayerIdentity id) {
		if (!id.omensSeen.add("welcome")) {
			return;
		}
		giveMirror(player);
		whisper(player, "Something has noticed you.");
		whisper(player, "A shadow has begun to follow. Look into the Identity Mirror to see what it is becoming.");
		player.sendSystemMessage(Component.literal(
				"(Right-click the Identity Mirror in your hotbar. The shadow floats near you; it grows as you become someone.)")
				.withStyle(ChatFormatting.GRAY));
	}

	// ---------------------------------------------------------------- sampling

	private static void sample(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}
		PlayerIdentity id = STORE.get(player.getUUID());
		String dim = dimensionOf(player);

		welcome(player, id);

		// Slice 4: remember where you came into each dimension (for the Wanderer's sense of doors).
		if (!id.arrivals.containsKey(dim) || (id.lastDim != null && !id.lastDim.equals(dim))) {
			id.arrivals.put(dim, player.getBlockX() + "," + player.getBlockY() + "," + player.getBlockZ());
		}
		id.lastDim = dim;
		if (id.omensSeen.add("field-notes")) {
			giveOrDrop(player, new ItemStack(ModItems.FIELD_NOTES));
			whisper(player, "There is a small book in your pocket. You do not remember putting it there.");
		}

		// Slice 3: things that were waiting for the player to be alive.
		if (dim.equals("nether")) {
			Memories.queue(id, Memory.INFERNO, false);
		}
		Memories.deliver(player, id);
		while (!id.pendingWhispers.isEmpty()) {
			whisper(player, id.pendingWhispers.remove(0));
		}

		// Curiosity: stepping into a 64x64 patch of the world you have never stood in before.
		if (id.visitedCells.add(cellKey(dim, player.getBlockX() >> 6, player.getBlockZ() >> 6))) {
			id.gain(Trait.CURIOSITY, 0.5, null, tick);
		}

		trackCloseCalls(player, id);
		trackReturnToDeathSite(player, id, dim);

		if (player.level() instanceof ServerLevel level) {
			Physicalize.visual(player, level, id);
			if (tick % 20 == 0) {
				haunt(player, id, dim);
				PathPowers.update(player, id);
				PathPowers.tick(player, level, id, tick);
				Abilities.tick(player, level, id, tick);
				Physicalize.tick(player, level, id, tick);
				Memories.tickAltars(player, level, id);
				Graves.tick(player, level, id);
			}
			if (tick % 40 == 0) {
				Fixtures.tick(player, level, id);
			}
		}

		for (Trait trait : Trait.values()) {
			if (id.get(trait) >= OMEN_THRESHOLD && id.omensSeen.add(trait.name())) {
				whisper(player, trait.omen);
			}
		}
	}

	/** Fear or Resolve, depending on what the player does after nearly dying to a creature. */
	private static void trackCloseCalls(ServerPlayer player, PlayerIdentity id) {
		float hp = player.getHealth();
		float max = player.getMaxHealth();

		if (hp > max * 0.5f) {
			id.wasLow = false;
		}

		if (hp <= max * 0.3f && !id.wasLow) {
			id.wasLow = true;
			DamageSource last = player.getLastDamageSource();
			if (last != null && last.getEntity() != null) { // hurt by a creature, not by hunger or a fall
				id.closeCallTick = tick;
				id.closeCallX = player.getX();
				id.closeCallY = player.getY();
				id.closeCallZ = player.getZ();
			}
		}

		// Ten seconds after a close call, look at what they did about it.
		if (id.closeCallTick != 0 && tick - id.closeCallTick >= 200) {
			boolean fled = player.distanceToSqr(id.closeCallX, id.closeCallY, id.closeCallZ) > 20.0 * 20.0;
			if (fled) {
				id.gain(Trait.FEAR, 1.5, "fled", tick);
			} else {
				id.gain(Trait.RESOLVE, 1.0, "held-ground", tick);
			}
			id.closeCallTick = 0;
		}
	}

	/** Will: going back for the place you died. */
	private static void trackReturnToDeathSite(ServerPlayer player, PlayerIdentity id, String dim) {
		PlayerIdentity.DeathEcho site = id.pendingReturn;
		if (site == null) {
			return;
		}
		if (tick > id.pendingUntilTick) {
			id.pendingReturn = null;
			return;
		}
		if (tick < id.pendingFromTick || !site.dim.equals(dim)) {
			return;
		}
		if (player.distanceToSqr(site.x + 0.5, site.y, site.z + 0.5) < 6.0 * 6.0) {
			id.gain(Trait.WILL, 2.0, "returned", tick);
			id.gain(Trait.RESOLVE, 1.0, "returned", tick);
			id.pendingReturn = null;
		}
	}

	/** Soul particles where you have died. Three or more deaths in one spot and the place gets heavier. */
	private static void haunt(ServerPlayer player, PlayerIdentity id, String dim) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		for (PlayerIdentity.DeathEcho echo : id.echoes) {
			if (!echo.dim.equals(dim)) {
				continue;
			}
			double dx = echo.x + 0.5 - player.getX();
			double dz = echo.z + 0.5 - player.getZ();
			double distSq = dx * dx + dz * dz;
			if (distSq > 24.0 * 24.0) {
				continue;
			}

			int neighbours = 0;
			for (PlayerIdentity.DeathEcho other : id.echoes) {
				if (other != echo && other.dim.equals(dim)
						&& Math.abs(other.x - echo.x) <= 12 && Math.abs(other.z - echo.z) <= 12) {
					neighbours++;
				}
			}
			boolean haunted = neighbours >= 2;

			level.sendParticles(ParticleTypes.SOUL, echo.x + 0.5, echo.y + 0.5, echo.z + 0.5,
					haunted ? 8 : 2, 0.3, 0.6, 0.3, 0.01);
			if (haunted) {
				level.sendParticles(ParticleTypes.SCULK_SOUL, echo.x + 0.5, echo.y + 0.5, echo.z + 0.5,
						3, 0.4, 0.8, 0.4, 0.02);
				if (distSq < 20.0 * 20.0) {
					Graves.maybeGhost(player, level, id, echo); // sometimes a version of you walks out of it
				}
				if (distSq < 10.0 * 10.0 && tick - id.lastHauntMessageTick > 6000) {
					id.lastHauntMessageTick = tick;
					whisper(player, "The ground here remembers you.");
				}
			}
		}
	}

	// ---------------------------------------------------------------- commands

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("eidolon")
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("read").executes(ctx -> read(ctx.getSource())))
				.then(Commands.literal("mirror").executes(ctx -> mirror(ctx.getSource())))
				.then(Commands.literal("debug").executes(ctx -> debug(ctx.getSource())))
				.then(Commands.literal("respawn").executes(ctx -> respawn(ctx.getSource())))
				.then(Commands.literal("set")
						.then(Commands.argument("trait", StringArgumentType.word())
								.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 1000.0))
										.executes(ctx -> setTrait(ctx.getSource(),
												StringArgumentType.getString(ctx, "trait"),
												DoubleArgumentType.getDouble(ctx, "amount"))))))
				.then(Commands.literal("reset").executes(ctx -> resetTraits(ctx.getSource())))
				.then(Commands.literal("memory")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(ctx -> giveMemory(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
				.then(Commands.literal("grave").executes(ctx -> devGrave(ctx.getSource())))
				.then(Commands.literal("other").executes(ctx -> devOther(ctx.getSource())))
				.then(Commands.literal("statue").executes(ctx -> devStatue(ctx.getSource())))
				.then(Commands.literal("book").executes(ctx -> devBook(ctx.getSource())))
				.then(Commands.literal("path").executes(ctx -> devPath(ctx.getSource())))
				.then(Commands.literal("rest").executes(ctx -> devRest(ctx.getSource()))));
	}

	private static int help(CommandSourceStack source) {
		say(source, "Look into the Identity Mirror. /eidolon mirror gives you a new one; /eidolon read prints the omens in chat.");
		say(source, "Dev tools: /eidolon set <trait> <amount>, reset, respawn, debug,");
		say(source, "memory <mortality|inferno|triumph|compassion|grief>, grave, other, statue, book, path, rest.");
		return 1;
	}

	private static int read(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		for (String line : reading(STORE.get(player.getUUID()))) {
			say(source, line);
		}
		return 1;
	}

	private static int mirror(CommandSourceStack source) throws CommandSyntaxException {
		giveMirror(source.getPlayerOrException());
		say(source, "The glass is cold in your hand.");
		return 1;
	}

	/** Dev tool: sets one trait to an exact score so you can look at every kind of Eidolon without grinding. */
	private static int setTrait(CommandSourceStack source, String name, double amount) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Trait trait;
		try {
			trait = Trait.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			say(source, "Unknown trait. Try: will, ego, empathy, resolve, fear, curiosity, violence, attachment.");
			return 0;
		}
		PlayerIdentity id = STORE.get(player.getUUID());
		id.traits.put(trait.name(), amount);
		EidolonManager.sync(player, id);
		say(source, trait.label() + " is now " + (int) amount + ". Watch the shadow change.");
		return 1;
	}

	private static int resetTraits(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerIdentity id = STORE.get(player.getUUID());
		id.traits.clear();
		EidolonManager.sync(player, id);
		say(source, "Everything you were is gone. The shadow forgets.");
		return 1;
	}

	/** Dev tool: gives you Field Notes again. */
	private static int devBook(CommandSourceStack source) throws CommandSyntaxException {
		giveOrDrop(source.getPlayerOrException(), new ItemStack(ModItems.FIELD_NOTES));
		return 1;
	}

	/** Dev tool: which path you lean toward, and what it has given you. (The game itself never says.) */
	private static int devPath(CommandSourceStack source) throws CommandSyntaxException {
		PlayerIdentity id = STORE.get(source.getPlayerOrException().getUUID());
		StringBuilder sb = new StringBuilder("Path: ").append(id.path == null ? "none" : id.path).append("\n");
		for (IdentityPath path : IdentityPath.ranked(id)) {
			sb.append(String.format("  %s %.1f\n", path.name(), path.score(id)));
		}
		sb.append("  powers: ").append(PathPowers.unlocked(id));
		say(source, sb.toString());
		return 1;
	}

	/** Dev tool: every power ready again. */
	private static int devRest(CommandSourceStack source) throws CommandSyntaxException {
		PlayerIdentity id = STORE.get(source.getPlayerOrException().getUUID());
		id.cooldowns.clear();
		say(source, "Rested.");
		return 1;
	}

	/** Dev tool: gives you a Memory Fragment. */
	private static int giveMemory(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Memory memory = Memory.byName(name);
		if (memory == null) {
			say(source, "Unknown memory. Try: mortality, inferno, triumph, compassion, grief.");
			return 0;
		}
		PlayerIdentity id = STORE.get(player.getUUID());
		Memories.queue(id, memory, true);
		Memories.deliver(player, id);
		return 1;
	}

	/** Dev tool: raises a Grave of You six blocks east of you. Walk up to it to wake what is inside. */
	private static int devGrave(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		PlayerIdentity id = STORE.get(player.getUUID());
		PlayerIdentity.DeathEcho echo = new PlayerIdentity.DeathEcho(dimensionOf(player),
				player.getBlockX() + 6, player.getBlockY(), player.getBlockZ(), System.currentTimeMillis(), "creature");
		echo.by = "your own command";
		echo.gear = Memories.gearOf(player);
		id.echoes.add(echo);
		Graves.form(player, level, id, echo);
		say(source, "A stone goes up where you have not died yet. Walk up to it.");
		return 1;
	}

	/** Dev tool: The Other You, now. */
	private static int devOther(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (player.level() instanceof ServerLevel level) {
			Physicalize.spawnOtherYou(player, level, STORE.get(player.getUUID()), tick);
		}
		return 1;
	}

	/** Dev tool: a statue of you, now. */
	private static int devStatue(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (player.level() instanceof ServerLevel level) {
			Physicalize.spawnStatue(player, level, STORE.get(player.getUUID()), tick);
		}
		return 1;
	}

	/** Dev tool: throws away the current Eidolon and makes a new one. */
	private static int respawn(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		EidolonManager.forget(player);
		EidolonManager.sync(player, STORE.get(player.getUUID()));
		say(source, "Something new steps out of your shadow.");
		return 1;
	}

	/**
	 * Raw numbers. This is a development tool and anyone can run it for now;
	 * the design says the game should never tell the player what they are, so lock this down before release.
	 */
	private static int debug(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerIdentity id = STORE.get(player.getUUID());

		StringBuilder sb = new StringBuilder("Identity (debug)");
		for (Trait trait : Trait.values()) {
			sb.append('\n').append(String.format(Locale.ROOT, "  %s: %.1f", trait.label(), id.get(trait)));
		}
		sb.append("\n  death echoes: ").append(id.echoes.size());
		sb.append("\n  places visited: ").append(id.visitedCells.size());
		sb.append("\n  path: ").append(id.path).append(", powers: ").append(PathPowers.unlocked(id));
		sb.append("\n  memories: ").append(id.memoriesEarned).append(", altars: ").append(id.altars.size());
		sb.append("\n  abilities: ").append(Memories.unlocked(id));
		sb.append("\n  graves: ").append(id.graves.size()).append(", fixtures: ").append(id.fixtures.size())
				.append(", watchers: ").append(id.watchers.size());

		String text = sb.toString();
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	// ----------------------------------------------------------------- helpers

	private static void say(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text)
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
	}

	static void whisper(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
	}

	/** A quiet grey aside, for the few things the game does name (an ability, after you earn it). */
	static void whisperDim(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
	}

	/** Whispers this the next time the player is alive (a dead player cannot be told anything). */
	static void later(PlayerIdentity id, String text) {
		id.pendingWhispers.add(text);
	}

	static long now() {
		return tick;
	}

	static String dimensionOf(ServerPlayer player) {
		var key = player.level().dimension();
		if (key.equals(Level.OVERWORLD)) {
			return "overworld";
		}
		if (key.equals(Level.NETHER)) {
			return "nether";
		}
		if (key.equals(Level.END)) {
			return "end";
		}
		return "other";
	}

	/** Packs a dimension and a 64x64-block cell coordinate into one long. */
	static long cellKey(String dim, int cellX, int cellZ) {
		long d = switch (dim) {
			case "overworld" -> 0L;
			case "nether" -> 1L;
			case "end" -> 2L;
			default -> 3L;
		};
		return (d << 40) | ((long) (cellX & 0xFFFFF) << 20) | (cellZ & 0xFFFFF);
	}
}
