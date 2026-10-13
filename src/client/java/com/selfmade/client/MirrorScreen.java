package com.selfmade.client;

import com.selfmade.IdentityPath;
import com.selfmade.MirrorData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Identity Mirror. It shows the Eidolon the way the glass sees it: no numbers and no labels, only a figure and a
 * few lines. Depending on who you are, the reflection glitches, something stands behind you, and a smaller, fainter
 * figure shows who you could have been.
 */
public class MirrorScreen extends Screen {
	private static final int TEXT = 0xC9B8D6;

	private final MirrorData data;
	private final long openedAt = System.currentTimeMillis();

	public MirrorScreen(MirrorData data) {
		super(Component.literal("Identity Mirror"));
		this.data = data;
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		float t = (System.currentTimeMillis() - openedAt) / 1000f;

		g.fill(0, 0, this.width, this.height, 0xE8050507);
		vignette(g);

		int pw = Math.min(this.width - 24, 260);
		int ph = Math.min(this.height - 24, 236);
		int px = (this.width - pw) / 2;
		int py = (this.height - ph) / 2;
		int glassH = (int) (ph * 0.60f);

		frame(g, px, py, pw, ph);
		glass(g, px, py, pw, glassH);
		figures(g, px, py, pw, glassH, t);
		cracks(g, px, py, pw, glassH);
		lines(g, px + 8, py + glassH + 8, pw - 16, t);

		String hint = "Esc: look away";
		g.text(this.font, hint, px + pw - this.font.width(hint) - 6, py + ph - 11, argb(150, 0x777777), false);
	}

	private void vignette(GuiGraphicsExtractor g) {
		for (int i = 0; i < 14; i++) {
			int c = argb((int) (150 * (1f - i / 14f)), 0x000000);
			g.fill(0, i, this.width, i + 1, c);
			g.fill(0, this.height - 1 - i, this.width, this.height - i, c);
			g.fill(i, 0, i + 1, this.height, c);
			g.fill(this.width - 1 - i, 0, this.width - i, this.height, c);
		}
	}

	private void frame(GuiGraphicsExtractor g, int px, int py, int pw, int ph) {
		g.fill(px - 4, py - 4, px + pw + 4, py + ph + 4, 0xFF1A1014);
		g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0xFF8A8478);
		g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF0C0A0E);
		g.fill(px, py, px + pw, py + ph, 0xFF0A0A0E);
		// four red studs in the corners
		int[] xs = {px - 4, px + pw - 1};
		int[] ys = {py - 4, py + ph - 1};
		for (int x : xs) {
			for (int y : ys) {
				g.fill(x, y, x + 5, y + 5, 0xFFB0121B);
			}
		}
	}

	private void glass(GuiGraphicsExtractor g, int px, int py, int pw, int gh) {
		for (int i = 0; i < gh; i += 4) {
			float f = i / (float) gh;
			int r = (int) (8 + 10 * f);
			int gr = (int) (8 + 8 * f);
			int b = (int) (14 + 16 * f);
			g.fill(px, py + i, px + pw, Math.min(py + gh, py + i + 4), 0xFF000000 | (r << 16) | (gr << 8) | b);
		}
	}

	private void figures(GuiGraphicsExtractor g, int px, int py, int pw, int gh, float t) {
		int mid = px + pw / 2;
		int ground = py + gh - 6;
		int unit = Math.max(1, (gh - 14) / 62);
		float glitch = data.glitch / 100f;

		float strongest = 0f;
		float[] lv = new float[8];
		for (int i = 0; i < 8; i++) {
			lv[i] = data.levels[i] / 100f;
			strongest = Math.max(strongest, lv[i]);
		}

		// something stands behind you
		if (data.behind) {
			Portrait.drawLurker(g, mid + unit * 7, ground, unit, 0.55f + 0.2f * (float) Math.sin(t * 0.8), t);
		}

		// the one you could have been: smaller, fainter, off to one side
		if (data.could >= 0 && data.could < IdentityPath.values().length) {
			IdentityPath path = IdentityPath.values()[data.could];
			float[] ghost = new float[8];
			ghost[path.a.ordinal()] = 0.85f;
			ghost[path.b.ordinal()] = 0.85f;
			new Portrait(g, px + pw / 5, ground, Math.max(1, unit - 1), 0.28f, t, 0f).draw(ghost);
		}

		// you. When the glass is unhappy the reflection drifts out of step with you.
		int drift = glitch > 0.4f ? (int) Math.round(Math.sin(t * 0.8) * unit * 3 * glitch) : 0;
		float alpha = Math.min(1f, 0.35f + 0.9f * strongest);
		new Portrait(g, mid + drift, ground, unit, alpha, t, glitch).draw(lv);
	}

	private void cracks(GuiGraphicsExtractor g, int px, int py, int pw, int gh) {
		Random rnd = new Random(7);
		int[] starts = {px + 6, px + pw - 8, px + pw / 2 + 20};
		for (int s : starts) {
			int x = s;
			int y = py;
			for (int step = 0; step < 22 && y < py + gh; step++) {
				g.fill(x, y, x + 1, y + 1, argb(90, 0xFFFFFF));
				x += rnd.nextInt(3) - 1;
				y += 1 + rnd.nextInt(2);
			}
		}
	}

	private void lines(GuiGraphicsExtractor g, int x, int y, int maxWidth, float t) {
		int cursor = y;
		for (int i = 0; i < data.lines.size(); i++) {
			float appear = 0.6f + i * 1.2f;
			int a = (int) (255 * Math.max(0f, Math.min(1f, (t - appear) / 0.8f)));
			List<String> wrapped = wrap(data.lines.get(i), maxWidth);
			for (String row : wrapped) {
				if (a > 8) {
					g.text(this.font, row, x, cursor, argb(a, TEXT), true);
				}
				cursor += this.font.lineHeight + 1;
			}
			cursor += 3;
		}
	}

	private List<String> wrap(String text, int maxWidth) {
		List<String> rows = new ArrayList<>();
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			String trial = row.length() == 0 ? word : row + " " + word;
			if (this.font.width(trial) > maxWidth && row.length() > 0) {
				rows.add(row.toString());
				row = new StringBuilder(word);
			} else {
				row = new StringBuilder(trial);
			}
		}
		if (row.length() > 0) {
			rows.add(row.toString());
		}
		return rows;
	}
}
