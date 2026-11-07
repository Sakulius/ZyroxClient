package de.sakulius.zyrox.screen;

import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.module.News;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public class NewsCard extends AbstractWidget {
	private static final float RADIUS = 7f;
	private static final int INSET = 3;
	private static final int BACK = 0xD8101116;
	private static final int BORDER = 0x26FFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFB4B9C3;
	private static final int SHADE = 0xE0080A10;
	private static final float SPEED = 14f;

	private final Screen parent;
	private long lastFrame = System.nanoTime();
	private float hover;

	public NewsCard(Screen parent, int x, int y, int width, int height) {
		super(x, y, width, height, Component.literal("News"));
		this.parent = parent;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (News.item() != null)
			Minecraft.getInstance().setScreen(new NewsScreen(parent));
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		var item = News.item();
		hover = Fx.approach(hover, item != null && isHovered() ? 1f : 0f, dt, SPEED);
		var font = Minecraft.getInstance().font;
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		Draw.roundRect(graphics, x, y, w, h, RADIUS, Fx.alpha(BACK, alpha));
		var picture = News.picture();
		if (picture != null)
			cover(graphics, picture, x + INSET, y + INSET, w - INSET * 2, h - INSET * 2);
		else
			Fx.quad(graphics, x + INSET, y + INSET, x + w - INSET, y + h - INSET, Fx.alpha(ZyroxTheme.accent(), 0.35f * alpha), Fx.alpha(0x00101116, alpha));
		Fx.quad(graphics, x + INSET, y + h * 0.45f, x + w - INSET, y + h - INSET, Fx.alpha(0x00080A10, alpha), Fx.alpha(SHADE, alpha));
		Draw.outline(graphics, x, y, w, h, RADIUS, Fx.alpha(Fx.mix(BORDER, ZyroxTheme.accent(), hover), alpha));

		int tagWidth = Type.SEMIBOLD.width(font, "NEWS") + 10;
		Draw.roundRect(graphics, x + 7, y + 7, tagWidth, 13, 4f, Fx.alpha(ZyroxTheme.accent(), alpha));
		Type.SEMIBOLD.draw(graphics, font, "NEWS", x + 12, y + 10, Fx.alpha(TEXT, alpha));

		String title = switch (News.state()) {
			case LOADING -> "Loading news...";
			case EMPTY -> "No news right now";
			case READY -> item == null ? "" : item.title();
		};
		String detail = item == null ? "" : item.date().isEmpty() ? "Click for the changelog" : item.date() + "  -  Click for the changelog";
		Type.LARGE.draw(graphics, font, fitted(font, Type.LARGE, title, w - 16), x + 8, y + h - 27, Fx.alpha(TEXT, alpha));
		Type.REGULAR.draw(graphics, font, fitted(font, Type.REGULAR, detail, w - 16), x + 8, y + h - 13, Fx.alpha(MUTED, alpha));
	}

	private void cover(GuiGraphics graphics, News.Picture picture, int x, int y, int w, int h) {
		float scale = Math.max((float) w / picture.width(), (float) h / picture.height());
		int regionW = Math.round(w / scale);
		int regionH = Math.round(h / scale);
		int u = (picture.width() - regionW) / 2;
		int v = (picture.height() - regionH) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, picture.id(), x, y, u, v, w, h, regionW, regionH, picture.width(), picture.height(), Fx.alpha(TEXT, alpha));
	}

	static String fitted(net.minecraft.client.gui.Font font, Type type, String text, int width) {
		if (type.width(font, text) <= width)
			return text;
		var shorter = text;
		while (!shorter.isEmpty() && type.width(font, shorter + "...") > width)
			shorter = shorter.substring(0, shorter.length() - 1);
		return shorter + "...";
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
