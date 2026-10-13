package com.selfmade.client;

import com.selfmade.SelfMade;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Eidolon's body. There is no fixed shape: a bare silhouette (mask, cloak, wisps) is always there, and every
 * trait grows its own parts once it passes {@link #SHOW}. The strongest traits also change the proportions.
 *
 * Texture atlas (128x128, 32x32 cells): dark body, bone, mask with eye holes, white glow, red, dark red, void, thread.
 * Model space: y points down, the feet are at y = 24, and the face is on the -z side.
 */
public class EidolonModel extends EntityModel<EidolonRenderState> {
	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(Identifier.fromNamespaceAndPath(SelfMade.MOD_ID, "eidolon"), "main");

	/** A trait grows its parts once it is above this (about a score of 10, so a few real fights). */
	static final float SHOW = 0.2f;

	private static final int[] DARK = {0, 0};
	private static final int[] BONE = {32, 0};
	private static final int[] MASK = {64, 0};
	private static final int[] WHITE = {96, 0};
	private static final int[] RED = {0, 32};
	private static final int[] THREAD = {96, 32};

	private static final float[] CROWN_HEIGHT = {0.6f, 1.0f, 1.4f, 1.0f, 0.6f};

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart headBlank;
	private final ModelPart armL;
	private final ModelPart armR;
	private final ModelPart[] claws = new ModelPart[4];
	private final ModelPart tail0;
	private final ModelPart tail1;
	private final ModelPart tail2;
	private final ModelPart pauldronL;
	private final ModelPart pauldronR;
	private final ModelPart shieldL;
	private final ModelPart shieldR;
	private final ModelPart hornL;
	private final ModelPart hornR;
	private final ModelPart halo;
	private final ModelPart[] crown = new ModelPart[5];
	private final ModelPart spine;
	private final ModelPart core;
	private final ModelPart locket;
	private final ModelPart[] threads = new ModelPart[4];
	private final ModelPart[] orbs = new ModelPart[3];

	public EidolonModel(ModelPart root) {
		super(root);
		this.root = root;
		this.head = root.getChild("head");
		this.headBlank = root.getChild("head_blank");
		this.armL = root.getChild("arm_l");
		this.armR = root.getChild("arm_r");
		this.claws[0] = armL.getChild("claw_0");
		this.claws[1] = armL.getChild("claw_1");
		this.claws[2] = armR.getChild("claw_0");
		this.claws[3] = armR.getChild("claw_1");
		this.tail0 = root.getChild("tail_0");
		this.tail1 = root.getChild("tail_1");
		this.tail2 = root.getChild("tail_2");
		this.pauldronL = root.getChild("pauldron_l");
		this.pauldronR = root.getChild("pauldron_r");
		this.shieldL = root.getChild("shield_l");
		this.shieldR = root.getChild("shield_r");
		this.hornL = root.getChild("horn_l");
		this.hornR = root.getChild("horn_r");
		this.halo = root.getChild("halo");
		for (int i = 0; i < crown.length; i++) {
			crown[i] = root.getChild("crown_" + i);
		}
		this.spine = root.getChild("spine");
		this.core = root.getChild("core");
		this.locket = root.getChild("locket");
		for (int i = 0; i < threads.length; i++) {
			threads[i] = root.getChild("thread_" + i);
		}
		for (int i = 0; i < orbs.length; i++) {
			orbs[i] = root.getChild("orb_" + i);
		}
	}

	private static PartDefinition add(PartDefinition parent, String name, int[] cell,
			float x, float y, float z, float w, float h, float d, float px, float py, float pz) {
		return parent.addOrReplaceChild(name,
				CubeListBuilder.create().texOffs(cell[0], cell[1]).addBox(x, y, z, w, h, d),
				PartPose.offset(px, py, pz));
	}

	private static PartDefinition addTilted(PartDefinition parent, String name, int[] cell,
			float x, float y, float z, float w, float h, float d, float px, float py, float pz,
			float rx, float ry, float rz) {
		return parent.addOrReplaceChild(name,
				CubeListBuilder.create().texOffs(cell[0], cell[1]).addBox(x, y, z, w, h, d),
				PartPose.offsetAndRotation(px, py, pz, rx, ry, rz));
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// the silhouette that is always there
		add(root, "chest", DARK, -4f, 0f, -2f, 8f, 10f, 4f, 0f, 2f, 0f);
		add(root, "cloak", DARK, -3.5f, 0f, -2f, 7f, 8f, 4f, 0f, 12f, 0f);
		add(root, "tail_0", DARK, -1.5f, 0f, -1.5f, 3f, 5f, 3f, 0f, 19f, 0f);
		add(root, "tail_1", DARK, -1f, 0f, -1f, 2f, 6f, 2f, -3f, 18f, 1f);
		add(root, "tail_2", DARK, -1f, 0f, -1f, 2f, 6f, 2f, 3f, 18f, 1f);
		add(root, "head", MASK, -3f, -7f, -3f, 6f, 7f, 6f, 0f, 3f, 0f);
		add(root, "head_blank", BONE, -3f, -7f, -3f, 6f, 7f, 6f, 0f, 3f, 0f); // the faceless one (Fear)

		// arms, with claws as children so they follow the arm
		PartDefinition armL = add(root, "arm_l", DARK, -1.5f, 0f, -1.5f, 3f, 11f, 3f, 6f, 3.5f, 0f);
		PartDefinition armR = add(root, "arm_r", DARK, -1.5f, 0f, -1.5f, 3f, 11f, 3f, -6f, 3.5f, 0f);
		add(armL, "claw_0", RED, -0.5f, 0f, -0.5f, 1f, 6f, 1f, -0.7f, 10.5f, -0.6f);
		add(armL, "claw_1", RED, -0.5f, 0f, -0.5f, 1f, 6f, 1f, 0.9f, 10.5f, -0.6f);
		add(armR, "claw_0", RED, -0.5f, 0f, -0.5f, 1f, 6f, 1f, -0.7f, 10.5f, -0.6f);
		add(armR, "claw_1", RED, -0.5f, 0f, -0.5f, 1f, 6f, 1f, 0.9f, 10.5f, -0.6f);

		// Resolve: pauldrons. Empathy: a shield wing on each side, angled forward.
		add(root, "pauldron_l", BONE, -3f, -1.5f, -3f, 6f, 3f, 6f, 6f, 3f, 0f);
		add(root, "pauldron_r", BONE, -3f, -1.5f, -3f, 6f, 3f, 6f, -6f, 3f, 0f);
		addTilted(root, "shield_l", BONE, -5f, -9f, -1f, 10f, 18f, 2f, 8f, 11f, -4f, 0f, 0.45f, 0f);
		addTilted(root, "shield_r", BONE, -5f, -9f, -1f, 10f, 18f, 2f, -8f, 11f, -4f, 0f, -0.45f, 0f);

		// Violence: horns
		addTilted(root, "horn_l", BONE, -1f, -5f, -1f, 2f, 5f, 2f, 3f, -3f, 0f, 0f, 0f, -0.5f);
		addTilted(root, "horn_r", BONE, -1f, -5f, -1f, 2f, 5f, 2f, -3f, -3f, 0f, 0f, 0f, 0.5f);

		// Ego: a ring that turns above the head, and a crown
		PartDefinition halo = root.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offset(0f, -11f, 0f));
		add(halo, "n", WHITE, -6f, -0.5f, -0.5f, 12f, 1f, 1f, 0f, 0f, -6f);
		add(halo, "s", WHITE, -6f, -0.5f, -0.5f, 12f, 1f, 1f, 0f, 0f, 6f);
		add(halo, "w", WHITE, -0.5f, -0.5f, -6f, 1f, 1f, 12f, -6f, 0f, 0f);
		add(halo, "e", WHITE, -0.5f, -0.5f, -6f, 1f, 1f, 12f, 6f, 0f, 0f);
		for (int i = 0; i < 5; i++) {
			add(root, "crown_" + i, WHITE, -0.5f, -4f, -0.5f, 1f, 4f, 1f, -4f + i * 2f, -4f, 0f);
		}

		// Will: a spine of light down the back and a burning core in the chest
		add(root, "spine", WHITE, -0.5f, 0f, 0f, 1f, 20f, 1f, 0f, 2f, 2.2f);
		add(root, "core", RED, -1.5f, -1.5f, -2.8f, 3f, 3f, 1f, 0f, 7f, 0f);

		// Attachment: a locket and threads running off the body
		add(root, "locket", RED, -1f, -1f, -2.6f, 2f, 2f, 1f, 0f, 10f, 0f);
		for (int i = 0; i < 4; i++) {
			add(root, "thread_" + i, THREAD, -0.25f, 0f, -0.25f, 0.5f, 14f, 0.5f, -3f + i * 2f, 9f, 2.4f);
		}

		// Curiosity: eyes that circle it
		for (int i = 0; i < 3; i++) {
			add(root, "orb_" + i, WHITE, -1f, -1f, -1f, 2f, 2f, 2f, 0f, 0f, 0f);
		}

		return LayerDefinition.create(mesh, 128, 128);
	}

	private static boolean on(float level) {
		return level > SHOW;
	}

	@Override
	public void setupAnim(EidolonRenderState state) {
		super.setupAnim(state);

		float t = state.ageInTicks;
		float[] lv = state.level;
		float maturity = state.maturity;
		float ego = lv[1];
		float empathy = lv[2];
		float fear = lv[4];
		float curiosity = lv[5];
		float violence = lv[6];
		boolean formed = maturity > 0.10f;

		// Size: a young Eidolon is small, a proud one is big. The feet stay where they are, and it hovers.
		float scale = Mth.clamp(0.55f + 0.45f * maturity + 0.30f * ego + 0.12f * empathy, 0.5f, 1.55f);
		root.xScale = scale;
		root.yScale = scale;
		root.zScale = scale;
		root.y = 24f * (1f - scale) + Mth.sin(t * 0.09f) * 1.2f;

		// Fear: it has no face, and its limbs are too long and too thin.
		boolean faceless = state.lead == 4 || fear > 0.55f;
		head.visible = !faceless;
		headBlank.visible = faceless;
		float sway = Mth.sin(t * 0.03f) * 0.25f;
		head.yRot = sway;
		headBlank.yRot = sway;

		float armLong = on(fear) ? 1f + fear * 0.7f : 1f;
		float armThin = on(fear) ? 1f - fear * 0.35f : 1f;
		for (ModelPart arm : new ModelPart[]{armL, armR}) {
			arm.visible = formed;
			arm.xScale = armThin;
			arm.yScale = armLong;
			arm.zScale = armThin;
		}
		armL.zRot = -0.12f - Mth.sin(t * 0.07f) * 0.06f;
		armR.zRot = 0.12f + Mth.sin(t * 0.07f + 1.3f) * 0.06f;
		armL.xRot = Mth.sin(t * 0.05f) * 0.10f;
		armR.xRot = Mth.sin(t * 0.05f + 2f) * 0.10f;

		tail0.zRot = Mth.sin(t * 0.10f) * 0.12f;
		tail1.zRot = 0.25f + Mth.sin(t * 0.13f) * 0.15f;
		tail2.zRot = -0.25f - Mth.sin(t * 0.11f) * 0.15f;

		// Violence
		boolean claw = on(violence);
		for (ModelPart c : claws) {
			c.visible = claw;
			c.yScale = 0.7f + violence;
		}
		hornL.visible = claw;
		hornR.visible = claw;

		// Resolve and Empathy
		pauldronL.visible = formed && on(lv[3]);
		pauldronR.visible = formed && on(lv[3]);
		boolean shield = formed && on(empathy);
		shieldL.visible = shield;
		shieldR.visible = shield;
		shieldL.yScale = 0.7f + 0.6f * empathy;
		shieldR.yScale = 0.7f + 0.6f * empathy;

		// Ego
		halo.visible = on(ego);
		halo.yRot = t * 0.04f;
		for (int i = 0; i < crown.length; i++) {
			crown[i].visible = ego > 0.45f;
			crown[i].yScale = (0.6f + 0.5f * ego) * CROWN_HEIGHT[i];
		}

		// Will
		spine.visible = on(lv[0]);
		core.visible = on(lv[0]);

		// Attachment
		locket.visible = on(lv[7]);
		for (int i = 0; i < threads.length; i++) {
			threads[i].visible = on(lv[7]);
			threads[i].xRot = Mth.sin(t * 0.08f + i) * 0.25f;
			threads[i].zRot = Mth.cos(t * 0.07f + i * 1.7f) * 0.15f;
		}

		// Curiosity
		int visibleOrbs = on(curiosity) ? 1 + (int) (curiosity * 2.99f) : 0;
		for (int i = 0; i < orbs.length; i++) {
			ModelPart orb = orbs[i];
			orb.visible = i < visibleOrbs;
			float angle = t * 0.06f + i * 2.094f;
			orb.x = Mth.cos(angle) * 11f;
			orb.z = Mth.sin(angle) * 11f;
			orb.y = 2f + i * 3f + Mth.sin(t * 0.1f + i) * 2f;
		}
	}
}
