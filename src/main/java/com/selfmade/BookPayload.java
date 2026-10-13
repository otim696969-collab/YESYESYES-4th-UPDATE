package com.selfmade;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: "open Field Notes at this page". The page number is sent as text. */
public record BookPayload(String data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BookPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, "book"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BookPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BookPayload::data, BookPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
