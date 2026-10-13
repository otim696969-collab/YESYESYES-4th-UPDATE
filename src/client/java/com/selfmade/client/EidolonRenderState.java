package com.selfmade.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Everything the model needs to draw one Eidolon. Filled in by {@link EidolonRenderer#extractRenderState}. */
public class EidolonRenderState extends LivingEntityRenderState {
	/** 0..1 per trait, indexed by Trait ordinal: WILL, EGO, EMPATHY, RESOLVE, FEAR, CURIOSITY, VIOLENCE, ATTACHMENT. */
	public final float[] level = new float[8];
	/** 0..1: how formed it is. */
	public float maturity;
	/** Ordinal of the strongest trait, or -1. */
	public int lead = -1;
}
