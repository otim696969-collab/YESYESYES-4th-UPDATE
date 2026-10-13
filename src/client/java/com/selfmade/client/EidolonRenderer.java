package com.selfmade.client;

import com.selfmade.EidolonEntity;
import com.selfmade.SelfMade;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Draws the Eidolon. Three textures use the same layout and differ only in how solid they are: the young Eidolon is a
 * dithered, flickering shadow, and it fills in as its owner becomes someone. It is always fully lit, like a ghost.
 */
public class EidolonRenderer extends MobRenderer<EidolonEntity, EidolonRenderState, EidolonModel> {
	private static final Identifier[] TEXTURES = {
			Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, "textures/entity/eidolon_0.png"),
			Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, "textures/entity/eidolon_1.png"),
			Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, "textures/entity/eidolon_2.png")
	};

	private static final int FULL_BRIGHT = 15728880;

	public EidolonRenderer(EntityRendererProvider.Context context) {
		super(context, new EidolonModel(context.bakeLayer(EidolonModel.LAYER)), 0.0f);
	}

	@Override
	public EidolonRenderState createRenderState() {
		return new EidolonRenderState();
	}

	@Override
	public Identifier getTextureLocation(EidolonRenderState state) {
		if (state.maturity < 0.12f) {
			return TEXTURES[0];
		}
		return state.maturity < 0.45f ? TEXTURES[1] : TEXTURES[2];
	}

	@Override
	public void extractRenderState(EidolonEntity entity, EidolonRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		for (int i = 0; i < state.level.length; i++) {
			state.level[i] = entity.traitLevel(i);
		}
		state.maturity = entity.maturity();
		state.lead = entity.leadOrdinal();
		state.lightCoords = FULL_BRIGHT;
	}
}
