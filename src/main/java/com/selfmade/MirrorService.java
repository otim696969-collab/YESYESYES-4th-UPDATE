package com.selfmade;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/** Builds a reading and sends it to the player's screen. */
public final class MirrorService {
	private static final Random RANDOM = new Random();

	private MirrorService() {
	}

	/** Returns false if the player's client cannot show the mirror (no mod installed), so the caller can fall back to chat. */
	public static boolean open(ServerPlayer player, PlayerIdentity id) {
		if (!ServerPlayNetworking.canSend(player, MirrorPayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new MirrorPayload(MirrorData.build(id, RANDOM).encode()));
		return true;
	}
}
