package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.module.News;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class NewsScreen extends Screen {
	private record Line(FormattedCharSequence text, int color, int gapBefore) {
	}

	private static final int MAX_WIDTH = 440;
	private static final int MARGIN = 20;
	private static final int PADDING = 14;
	private static final float RADIUS = 10f;
	private static final int BACK = 0xF0101116;
	private static final int BORDER = 0x26FFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int BODY = 0xFFD4D7DD;
	private static final int MUTED = 0xFF8D929C;
	private static final int LINE_HEIGHT = 11;
	private static final int CLOSE_SIZE = 18;
	private static final float SCROLL_STEP = 22f;

	private final Screen parent;
	private List<Line> lines = List.of();
	private float scroll;
	private float scrollTarget;
	private long lastFrame = System.nanoTime();

	public NewsScreen(Screen parent) {
		super(Component.literal("News"));
		this.parent = parent;
	}

	private int frameWidth() {
		return Math.min(MAX_WIDTH, width - MARGIN * 2);
	}

	private int frameLeft() {
		return (width - frameWidth()) / 2;
	}

	private int frameTop() {
		return MARGIN;
	}

	private int frameBottom() {
		return height - MARGIN;
	}

	private int pictureHeight() {
		return Math.min(frameWidth() * 9 / 16, (frameBottom() - frameTop()) * 2 / 5);
	}

	private int textTop() {
		return frameTop() + pictureHeight() + 38;
	}

	@Override
	protected void init() {
		var item = News.item();
		var built = new ArrayList<Line>();
		int wrap = frameWidth() - PADDING * 2;
		if (item != null) {
			for (var raw : item.lines()) {
				var line = raw.strip();
				if (line.isEmpty()) {
					built.add(new Line(FormattedCharSequence.EMPTY, BODY, 0));
					continue;
				}
				boolean heading = line.startsWith("#");
				var type = heading ? Type.SEMIBOLD : Type.REGULAR;
				var shown = heading ? line.replaceFirst("^#+\\s*", "") : line.replaceFirst("^[-*]\\s+", "• ");
				boolean first = true;
				for (var part : font.split(type.of(shown), wrap)) {
					built.add(new Line(part, heading ? TEXT : BODY, first && heading ? 6 : 0));
					first = false;
				}
			}
		}
		lines = built;
	}

	private int contentHeight() {
		int total = 0;
		for (var line : lines)
			total += LINE_HEIGHT + line.gapBefore();
		return total;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - (frameBottom() - PADDING - textTop()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		scroll = Fx.approach(scroll, scrollTarget, dt, 16f);
		var item = News.item();
		int left = frameLeft();
		int top = frameTop();
		int w = frameWidth();
		int h = frameBottom() - top;
		Draw.glow(graphics, left, top, w, h, RADIUS, 10f, 0x90000000);
		Draw.roundRect(graphics, left, top, w, h, RADIUS, BACK);
		Draw.outline(graphics, left, top, w, h, RADIUS, BORDER);

		var picture = News.picture();
		int pictureHeight = pictureHeight();
		if (picture != null) {
			float scale = Math.max((float) (w - 8) / picture.width(), (float) pictureHeight / picture.height());
			int regionW = Math.round((w - 8) / scale);
			int regionH = Math.round(pictureHeight / scale);
			graphics.blit(RenderPipelines.GUI_TEXTURED, picture.id(), left + 4, top + 4, (picture.width() - regionW) / 2, (picture.height() - regionH) / 2, w - 8, pictureHeight, regionW, regionH, picture.width(), picture.height());
		} else {
			Fx.quad(graphics, left + 4, top + 4, left + w - 4, top + 4 + pictureHeight, Fx.alpha(ZyroxTheme.accent(), 0.4f), 0x00101116);
		}
		Fx.quad(graphics, left + 4, top + 4 + pictureHeight * 0.6f, left + w - 4, top + 4 + pictureHeight, 0x00101116, BACK);

		int closeX = left + w - CLOSE_SIZE - 8;
		int closeY = top + 8;
		boolean closeHovered = inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
		Draw.roundRect(graphics, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE / 2f, closeHovered ? 0xE0303540 : 0xB0101116);
		Type.SEMIBOLD.centered(graphics, font, "x", closeX + CLOSE_SIZE / 2, closeY + 5, TEXT);

		if (item != null) {
			Type.LARGE.draw(graphics, font, NewsCard.fitted(font, Type.LARGE, item.title(), w - PADDING * 2), left + PADDING, top + pictureHeight + 10, TEXT);
			Type.REGULAR.draw(graphics, font, item.date().isEmpty() ? "Changelog" : item.date(), left + PADDING, top + pictureHeight + 24, MUTED);
		}
		Draw.roundRect(graphics, left + PADDING, textTop() - 6, w - PADDING * 2, 1, 0f, BORDER);

		graphics.enableScissor(left + 1, textTop(), left + w - 1, frameBottom() - PADDING);
		int y = textTop() - Math.round(scroll);
		for (var line : lines) {
			y += line.gapBefore();
			graphics.text(font, line.text(), left + PADDING, y, line.color(), false);
			y += LINE_HEIGHT;
		}
		graphics.disableScissor();
		int overflow = maxScroll();
		if (overflow > 0) {
			int track = frameBottom() - PADDING - textTop();
			int bar = Math.max(14, track * track / (track + overflow));
			int barTop = textTop() + (int) ((track - bar) * scroll / overflow);
			Draw.roundRect(graphics, left + w - 6, barTop, 2, bar, 1f, 0x50FFFFFF);
		}
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int left = frameLeft();
		int top = frameTop();
		int w = frameWidth();
		boolean onClose = inside(event.x(), event.y(), left + w - CLOSE_SIZE - 8, top + 8, CLOSE_SIZE, CLOSE_SIZE);
		boolean outside = !inside(event.x(), event.y(), left, top, w, frameBottom() - top);
		if (onClose || outside) {
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scrollTarget = Math.clamp(scrollTarget - (float) vertical * SCROLL_STEP, 0f, maxScroll());
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
