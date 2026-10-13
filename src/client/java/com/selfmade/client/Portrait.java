package com.selfmade.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws an Eidolon as flat pixel art for the mirror, using nothing but filled rectangles. It is built from the same
 * trait levels as the 3D body, so the reflection and the creature agree (unless the mirror is lying).
 *
 * Coordinates are in "grid units": x is relative to the centre, y goes up from the ground, one unit is {@code unit}
 * screen pixels. The figure is about 60 units tall.
 */
final class Portrait {
	static final int BONE = 0xD8D4C8;
	static final int DARK = 0x131318;
	static final int DARK2 = 0x1E1E28;
	static final int RED = 0xB0121B;
	static final int DRED = 0x5A0A12;
	static final int WHITE = 0xF4F4F4;
	static final int VOID = 0x050507;
	static final int THREAD = 0xAAAAB4;

	private static final float SHOW = EidolonModel.SHOW;
	private static final int[] CROWN = {1, 2, 3, 2, 3, 2, 2, 1};

	private final GuiGraphicsExtractor g;
	private final int cx;
	private final int ground;
	private final int unit;
	private final float alpha;
	private final float time;
	private final float glitch;

	Portrait(GuiGraphicsExtractor g, int cx, int ground, int unit, float alpha, float time, float glitch) {
		this.g = g;
		this.cx = cx;
		this.ground = ground;
		this.unit = unit;
		this.alpha = alpha;
		this.time = time;
		this.glitch = glitch;
	}

	/** A rectangle in grid units. {@code a} is 0-255 and is multiplied by the portrait's overall alpha. */
	private void px(int x, int y, int w, int h, int rgb, int a) {
		int aa = Math.min(255, Math.round(a * alpha));
		if (aa <= 4) {
			return;
		}
		int x0 = cx + (x + shiftAt(y)) * unit;
		int y1 = ground - y * unit;
		g.fill(x0, y1 - h * unit, x0 + w * unit, y1, (aa << 24) | (rgb & 0xFFFFFF));
	}

	/** The reflection misbehaves in short bursts: horizontal slices slide out of place. */
	private int shiftAt(int y) {
		if (glitch < 0.15f) {
			return 0;
		}
		float phase = (time * 0.9f) % 1.0f;
		if (phase < 0.88f) {
			return 0;
		}
		int s = ((y * 7 + (int) (time * 20f)) % 5) - 2;
		return glitch > 0.5f ? s : s / 2;
	}

	void draw(float[] lv) {
		float will = lv[0];
		float ego = lv[1];
		float empathy = lv[2];
		float resolve = lv[3];
		float fear = lv[4];
		float curiosity = lv[5];
		float violence = lv[6];
		float attachment = lv[7];
		boolean faceless = fear > 0.55f;

		// ---- behind the body
		if (empathy > SHOW) {
			int h = 16 + (int) (14 * empathy);
			for (int side = -1; side <= 1; side += 2) {
				int x = side > 0 ? 9 : -13;
				px(x, 10, 4, h, BONE, 235);
				px(x + 1, 11, 2, h - 2, DRED, 255);
			}
		}
		if (attachment > SHOW) {
			for (int k = 0; k < 4; k++) {
				int sway = Math.round((float) Math.sin(time * 1.6 + k) * 2f);
				px(-7 + k * 4 + sway, 0, 1, 24, THREAD, 130);
			}
		}

		// ---- wisps and cloak
		for (int y = 0; y < 12; y++) {
			int hw = 1 + y / 3;
			int sway = Math.round((float) Math.sin(time * 2.0 + y * 0.5) * 1.5f);
			px(-hw + sway, y, hw * 2, 1, DARK2, 60 + y * 14);
		}
		for (int y = 12; y < 32; y++) {
			int hw = 4 + (y - 12) * 3 / 19;
			px(-hw, y, hw * 2, 1, (y & 1) == 0 ? DARK : DARK2, 240);
		}

		// ---- arms
		int armLen = fear > SHOW ? 14 + (int) (10 * fear) : 14;
		int armW = fear > SHOW ? 1 : 2;
		for (int side = -1; side <= 1; side += 2) {
			int x = side > 0 ? 7 : -8;
			px(x, 32 - armLen, armW, armLen, DARK2, 235);
			if (violence > SHOW) {
				px(x, 32 - armLen - 4, 1, 4, RED, 255);
				if (armW > 1) {
					px(x + 1, 32 - armLen - 3, 1, 3, RED, 255);
				}
			}
		}
		if (resolve > SHOW) {
			px(4, 31, 5, 3, BONE, 255);
			px(-9, 31, 5, 3, BONE, 255);
		}

		// ---- chest: the burning core and the seam of light (Will), the locket (Attachment)
		if (will > SHOW) {
			px(0, 13, 1, 18, WHITE, 150);
			px(-1, 24, 3, 3, RED, 255);
		}
		if (attachment > SHOW) {
			px(0, 18, 2, 2, RED, 255);
		}

		// ---- head
		px(-4, 33, 8, 9, BONE, 255);
		if (faceless) {
			px(-1, 34, 1, 6, DARK, 255);
		} else {
			px(-3, 36, 2, 2, VOID, 255);
			px(1, 36, 2, 2, VOID, 255);
			px(-3, 37, 1, 1, WHITE, 255);
			px(1, 37, 1, 1, WHITE, 255);
			px(0, 33, 1, 5, DARK, 200);
		}
		if (violence > SHOW) {
			px(-5, 41, 1, 3, BONE, 255);
			px(-6, 43, 1, 2, BONE, 255);
			px(4, 41, 1, 3, BONE, 255);
			px(5, 43, 1, 2, BONE, 255);
		}
		if (ego > SHOW) {
			px(-6, 48, 12, 1, WHITE, 200);
			px(-6, 46, 1, 2, WHITE, 200);
			px(5, 46, 1, 2, WHITE, 200);
		}
		if (ego > 0.45f) {
			for (int i = 0; i < CROWN.length; i++) {
				px(-4 + i, 42, 1, CROWN[i], WHITE, 255);
			}
		}

		// ---- Curiosity: eyes that circle it
		if (curiosity > SHOW) {
			int orbs = 1 + (int) (curiosity * 2.99f);
			for (int i = 0; i < orbs; i++) {
				double a = time * 1.4 + i * 2.094;
				int x = (int) Math.round(Math.cos(a) * 13);
				int y = 24 + i * 6 + (int) Math.round(Math.sin(a) * 5);
				px(x, y, 2, 2, WHITE, 255);
				px(x, y, 1, 1, VOID, 255);
			}
		}
	}

	/** Something tall and dark standing where the reflection's shoulder should be. */
	static void drawLurker(GuiGraphicsExtractor g, int cx, int ground, int unit, float alpha, float time) {
		int a = Math.min(255, Math.round(150 * alpha));
		for (int y = 0; y < 70; y++) {
			int hw = 3 + Math.min(y, 40) / 5 + (y > 55 ? 0 : 1);
			int sway = Math.round((float) Math.sin(time * 0.9 + y * 0.12) * 1.2f);
			int x0 = cx + (-hw + sway) * unit;
			int y1 = ground - y * unit;
			g.fill(x0, y1 - unit, x0 + hw * 2 * unit, y1, (a << 24) | VOID);
		}
		boolean blink = ((int) (time * 1.7)) % 5 != 0;
		if (blink) {
			int ey = ground - 58 * unit;
			g.fill(cx - 3 * unit, ey, cx - 2 * unit, ey + unit, 0xFFF4F4F4);
			g.fill(cx + 2 * unit, ey, cx + 3 * unit, ey + unit, 0xFFF4F4F4);
		}
	}
}
