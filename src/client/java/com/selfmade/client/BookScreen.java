package com.selfmade.client;

import com.selfmade.BookText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The starting book, "Field Notes": a dark page with a red title and two buttons to turn it. */
public class BookScreen extends Screen {
	private static final int TEXT = 0xD8CCE0;
	private static final int TITLE = 0xE0303A;

	private int page;
	private Button prev;
	private Button next;

	public BookScreen(int page) {
		super(Component.literal("Field Notes"));
		this.page = Math.max(0, Math.min(BookText.PAGES.length - 1, page));
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private int panelWidth() {
		return Math.min(this.width - 24, 300);
	}

	private int panelHeight() {
		return Math.min(this.height - 56, 220);
	}

	@Override
	protected void init() {
		int by = (this.height - panelHeight() - 28) / 2 + panelHeight() + 10;
		prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
				.bounds(this.width / 2 - 52, by, 24, 18).build());
		next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
				.bounds(this.width / 2 + 28, by, 24, 18).build());
		refresh();
	}

	private void turn(int delta) {
		page = Math.max(0, Math.min(BookText.PAGES.length - 1, page + delta));
		refresh();
	}

	private void refresh() {
		if (prev != null) {
			prev.active = page > 0;
		}
		if (next != null) {
			next.active = page < BookText.PAGES.length - 1;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta); // background and the two buttons

		int pw = panelWidth();
		int ph = panelHeight();
		int px = (this.width - pw) / 2;
		int py = (this.height - ph - 28) / 2;

		g.fill(px - 4, py - 4, px + pw + 4, py + ph + 4, 0xFF1A1014);
		g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0xFF8A8478);
		g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF0C0A0E);
		g.fill(px, py, px + pw, py + ph, 0xFF0F0D12);
		int[] xs = {px - 4, px + pw - 1};
		int[] ys = {py - 4, py + ph - 1};
		for (int x : xs) {
			for (int y : ys) {
				g.fill(x, y, x + 5, y + 5, 0xFFB0121B);
			}
		}

		String raw = BookText.PAGES[page];
		int split = raw.indexOf('\n');
		String title = split < 0 ? raw : raw.substring(0, split);
		String body = split < 0 ? "" : raw.substring(split + 1);

		int x = px + 12;
		int y = py + 12;
		g.text(this.font, title, x, y, argb(255, TITLE), true);
		g.fill(x, y + this.font.lineHeight + 2, x + pw - 24, y + this.font.lineHeight + 3, argb(120, 0xB0121B));
		y += this.font.lineHeight + 9;

		for (String paragraph : body.split("\n", -1)) {
			if (paragraph.isEmpty()) {
				y += this.font.lineHeight / 2 + 2;
				continue;
			}
			for (String row : wrap(paragraph, pw - 24)) {
				g.text(this.font, row, x, y, argb(255, TEXT), false);
				y += this.font.lineHeight + 1;
			}
		}

		String count = (page + 1) + " / " + BookText.PAGES.length;
		g.text(this.font, count, px + (pw - this.font.width(count)) / 2, py + ph - 12, argb(160, 0x888888), false);
		String hint = "Esc: close";
		g.text(this.font, hint, px + pw - this.font.width(hint) - 6, py + ph - 12, argb(120, 0x777777), false);
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
