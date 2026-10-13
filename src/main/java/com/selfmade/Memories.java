package com.selfmade;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Memory Fragments and Memory Altars.
 *
 * A Memory Altar is built from vanilla blocks: a lectern standing on a block of crying obsidian.
 * Right-click it holding a fragment to place the fragment in it (three at most). Sneak with an empty hand to take
 * the last one back. Every pair of memories in one altar makes an {@link Ability}; take a memory out and the ability goes quiet.
 * An altar belongs to the first person who places a memory in it.
 */
final class Memories {
	/** A fragment waiting to be handed over (it cannot be given to someone who is dead). */
	record Award(Memory memory, boolean force) {
	}

	private static final int ALTAR_SLOTS = 3;

	private Memories() {
	}

	static void init() {
		UseBlockCallback.EVENT.register(Memories::onUseBlock);
	}

	// ------------------------------------------------------------------ earning

	/** Queues a fragment. Unless forced, each memory can only be earned once per player. */
	static void queue(PlayerIdentity id, Memory memory, boolean force) {
		if (!force && id.memoriesEarned.contains(memory.name())) {
			return;
		}
		id.pendingAwards.add(new Award(memory, force));
	}

	/** Hands over anything queued, once the player is alive. */
	static void deliver(ServerPlayer player, PlayerIdentity id) {
		if (id.pendingAwards.isEmpty()) {
			return;
		}
		List<Award> batch = new ArrayList<>(id.pendingAwards);
		id.pendingAwards.clear();
		for (Award award : batch) {
			Memory memory = award.memory();
			if (!award.force() && id.memoriesEarned.contains(memory.name())) {
				continue;
			}
			id.memoriesEarned.add(memory.name());
			ItemStack stack = new ItemStack(ModItems.fragment(memory));
			SelfMade.giveOrDrop(player, stack);
			SelfMade.whisper(player, memory.born);
			if (player.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(),
						30, 0.5, 0.6, 0.5, 0.4);
			}
		}
	}

	/** The registry ids of what a player is holding and wearing: mainhand, helmet, chest, legs, boots. */
	static List<String> gearOf(ServerPlayer player) {
		List<String> out = new ArrayList<>();
		EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
				EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (EquipmentSlot slot : slots) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ the altar

	static boolean isAltar(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.LECTERN) && level.getBlockState(pos.below()).is(Blocks.CRYING_OBSIDIAN);
	}

	private static String key(String dim, BlockPos pos) {
		return dim + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		ItemStack held = sp.getItemInHand(hand);
		boolean placing = held.getItem() instanceof MemoryFragmentItem;
		boolean taking = sp.isShiftKeyDown() && held.isEmpty();
		BlockPos pos = hit.getBlockPos();
		if ((!placing && !taking) || !isAltar(level, pos)) {
			return InteractionResult.PASS;
		}

		String key = key(SelfMade.dimensionOf(sp), pos);
		PlayerIdentity id = SelfMade.STORE.get(sp.getUUID());
		UUID owner = SelfMade.STORE.altarOwner(key);
		if (owner != null && !owner.equals(sp.getUUID())) {
			SelfMade.whisper(sp, "This altar remembers someone else.");
			return InteractionResult.SUCCESS;
		}

		Set<Ability> before = unlocked(id);
		List<String> slots = id.altars.get(key);

		if (placing) {
			Memory memory = ((MemoryFragmentItem) held.getItem()).memory;
			if (slots == null) {
				slots = new ArrayList<>();
				id.altars.put(key, slots);
			}
			if (slots.size() >= ALTAR_SLOTS) {
				SelfMade.whisper(sp, "The altar holds three memories. No more.");
				return InteractionResult.SUCCESS;
			}
			if (slots.contains(memory.name())) {
				SelfMade.whisper(sp, "It already holds that one.");
				return InteractionResult.SUCCESS;
			}
			slots.add(memory.name());
			held.shrink(1);
			SelfMade.whisper(sp, "The glass of the altar clouds, and clears. " + memory.line);
		} else {
			if (slots == null || slots.isEmpty()) {
				SelfMade.whisper(sp, "The altar is empty. It is waiting.");
				return InteractionResult.SUCCESS;
			}
			Memory memory = Memory.byName(slots.remove(slots.size() - 1));
			if (slots.isEmpty()) {
				id.altars.remove(key);
			}
			if (memory != null) {
				ItemStack back = new ItemStack(ModItems.fragment(memory));
				SelfMade.giveOrDrop(sp, back);
			}
			SelfMade.whisper(sp, "A memory comes loose in your hand.");
		}

		Set<Ability> after = unlocked(id);
		for (Ability ability : after) {
			if (!before.contains(ability)) {
				SelfMade.whisper(sp, ability.line);
				SelfMade.whisperDim(sp, "(" + ability.label + ")");
			}
		}
		for (Ability ability : before) {
			if (!after.contains(ability)) {
				SelfMade.whisper(sp, "Something you had has gone quiet.");
				break;
			}
		}

		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5,
					40, 0.4, 0.5, 0.4, 0.6);
			serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
					6, 0.2, 0.3, 0.2, 0.02);
		}
		return InteractionResult.SUCCESS;
	}

	/** Every ability made by pairs of memories sharing an altar, over all of a player's altars. */
	static Set<Ability> unlocked(PlayerIdentity id) {
		Set<Ability> out = EnumSet.noneOf(Ability.class);
		for (List<String> slots : id.altars.values()) {
			for (int i = 0; i < slots.size(); i++) {
				for (int j = i + 1; j < slots.size(); j++) {
					Memory x = Memory.byName(slots.get(i));
					Memory y = Memory.byName(slots.get(j));
					if (x != null && y != null) {
						Ability ability = Ability.of(x, y);
						if (ability != null) {
							out.add(ability);
						}
					}
				}
			}
		}
		return out;
	}

	/** Once a second: altars glow faintly while you are near, and if one has been broken its memories fall out. */
	static void tickAltars(ServerPlayer player, ServerLevel level, PlayerIdentity id) {
		if (id.altars.isEmpty()) {
			return;
		}
		String dim = SelfMade.dimensionOf(player);
		Iterator<Map.Entry<String, List<String>>> it = id.altars.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, List<String>> entry = it.next();
			String[] parts = entry.getKey().split(":");
			if (parts.length != 4 || !parts[0].equals(dim)) {
				continue;
			}
			BlockPos pos;
			try {
				pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
			} catch (NumberFormatException e) {
				it.remove();
				continue;
			}
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 32.0 * 32.0 || !level.hasChunkAt(pos)) {
				continue;
			}
			if (!isAltar(level, pos)) {
				for (String name : entry.getValue()) {
					Memory memory = Memory.byName(name);
					if (memory != null) {
						Block.popResource(level, pos, new ItemStack(ModItems.fragment(memory)));
					}
				}
				it.remove();
				continue;
			}
			level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5,
					entry.getValue().size() * 2, 0.3, 0.4, 0.3, 0.3);
		}
	}
}
