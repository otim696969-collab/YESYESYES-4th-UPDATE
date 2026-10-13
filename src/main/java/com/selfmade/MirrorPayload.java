package com.selfmade;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: "open the mirror and show this". The data is one string, see {@link MirrorData#encode()}. */
public record MirrorPayload(String data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MirrorPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, "mirror"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MirrorPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, MirrorPayload::data, MirrorPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
