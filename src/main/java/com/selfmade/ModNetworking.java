package com.selfmade;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the packets both sides need to know about. */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(MirrorPayload.TYPE, MirrorPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BookPayload.TYPE, BookPayload.CODEC);
	}
}
