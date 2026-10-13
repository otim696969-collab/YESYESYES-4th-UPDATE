package com.selfmade;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The starting book. Right-click to read it. */
public class FieldNotesItem extends Item {
	public FieldNotesItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player user, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		if (user instanceof ServerPlayer player) {
			if (ServerPlayNetworking.canSend(player, BookPayload.TYPE)) {
				ServerPlayNetworking.send(player, new BookPayload("0"));
			} else {
				// no mod on the client: read it out in chat instead
				for (String page : BookText.PAGES) {
					for (String line : page.split("\n")) {
						if (!line.isBlank()) {
							SelfMade.whisperDim(player, line);
						}
					}
				}
			}
		}
		return InteractionResult.SUCCESS;
	}
}
