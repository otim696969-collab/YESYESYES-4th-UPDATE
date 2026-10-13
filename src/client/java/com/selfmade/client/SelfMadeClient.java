package com.selfmade.client;

import com.selfmade.BookPayload;
import com.selfmade.MirrorData;
import com.selfmade.MirrorPayload;
import com.selfmade.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only setup: how to draw an Eidolon, and what to do when the server opens the mirror. */
public class SelfMadeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(EidolonModel.LAYER, EidolonModel::createBodyLayer);
		EntityRenderers.register(ModEntities.EIDOLON, EidolonRenderer::new);
		EntityRenderers.register(ModEntities.SHADE, ShadeRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(BookPayload.TYPE, (payload, context) -> {
			int page = 0;
			try {
				page = Integer.parseInt(payload.data());
			} catch (NumberFormatException ignored) {
				// open at the first page
			}
			final int open = page;
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.execute(() -> minecraft.gui.setScreen(new BookScreen(open)));
		});

		ClientPlayNetworking.registerGlobalReceiver(MirrorPayload.TYPE, (payload, context) -> {
			MirrorData data = MirrorData.decode(payload.data());
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.execute(() -> minecraft.gui.setScreen(new MirrorScreen(data)));
		});
	}
}
