package com.selfmade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Right-click it to look into the mirror. It never shows numbers, only what is starting to follow you.
 * Sneak and right-click to reach for the powers your Identity Path has given you (see PathPowers).
 */
public class IdentityMirrorItem extends Item {
	public IdentityMirrorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (user instanceof ServerPlayer player) {
			if (player.isShiftKeyDown()) {
				PathPowers.invoke(player); // reaching for what you can do
			} else {
				SelfMade.showReading(player);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
