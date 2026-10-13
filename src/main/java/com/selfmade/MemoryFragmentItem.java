package com.selfmade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A piece of something that happened to you. Held in the air it only remembers out loud.
 * Its real use is on a Memory Altar (a lectern standing on crying obsidian): see {@link Memories}.
 */
public class MemoryFragmentItem extends Item {
	public final Memory memory;

	public MemoryFragmentItem(Properties properties, Memory memory) {
		super(properties);
		this.memory = memory;
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (user instanceof ServerPlayer player) {
			SelfMade.whisper(player, memory.line);
		}
		return InteractionResult.SUCCESS;
	}
}
