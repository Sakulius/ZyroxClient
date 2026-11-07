package de.sakulius.zyrox.screen;

import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.ZyroxTheme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public final class ZyroxTitle {
	public static final int COLUMN_X = 24;
	public static final int COLUMN_WIDTH = 124;
	public static final int BUTTON_HEIGHT = 20;
	public static final int BUTTON_STEP = 24;
	public static final int HEADER_HEIGHT = 52;
	public static final int ICON_STEP = 24;
	public static final float NEWS_SHARE = 0.27f;
	public static final int NEWS_MIN_WIDTH = 150;
	public static final int NEWS_MAX_WIDTH = 240;
	public static final int SIDE_MARGIN = 12;
	public static final int SIDE_GAP = 6;
	public static final int MODEL_BOTTOM = 16;
	public static final int MODEL_MIN_HEIGHT = 60;
	public static final float MODEL_SHARE = 0.48f;

	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "textures/gui/logo.png");
	private static final int LOGO_TEXTURE_WIDTH = 565;
	private static final int LOGO_TEXTURE_HEIGHT = 110;
	private static final int LOGO_WIDTH = 112;
	private static final int LOGO_HEIGHT = LOGO_WIDTH * LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH;
	private static final int SPARKS = 26;
	private static final int STRIPS = 32;
	private static final float SCRIM_WIDTH = 0.62f;
	private static final int SCRIM = 0xE0080A10;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0x99FFFFFF;
	private static final float SPARK_SECONDS = 26f;
	private static final float PULSE = 0.025f;
	private static final float PULSE_SPEED = 2.6f;
	private static final int VERSION_BOTTOM = 20;

	private static int headerTop;
	private static String versionText;

	private ZyroxTitle() {
	}

	public static void layout(int top) {
		headerTop = top;
	}

	public static int columnTop(int height, int buttons) {
		int total = HEADER_HEIGHT + buttons * BUTTON_STEP + BUTTON_HEIGHT;
		return Math.max(6, (height - total) / 2);
	}

	public static void background(GuiGraphics graphics, int width, int height, float alpha) {
		float end = width * SCRIM_WIDTH;
		float step = end / STRIPS;
		for (int i = 0; i < STRIPS; i++) {
			float t = 1f - (float) i / STRIPS;
			int color = Fx.alpha(SCRIM, t * t * alpha);
			Fx.quad(graphics, i * step, 0, (i + 1) * step, height, color, color);
		}
		sparks(graphics, width, height, alpha);
	}

	private static float noise(int seed, float scale) {
		float value = (float) Math.sin(seed * 127.1f + scale * 311.7f) * 43758.547f;
		return value - (float) Math.floor(value);
	}

	private static void sparks(GuiGraphics graphics, int width, int height, float alpha) {
		float seconds = Util.getMillis() / 1000f;
		int accent = ZyroxTheme.accent();
		for (int i = 0; i < SPARKS; i++) {
			float life = (seconds / (SPARK_SECONDS * (0.6f + noise(i, 1f) * 0.8f)) + noise(i, 2f)) % 1f;
			float x = noise(i, 3f) * width + (float) Math.sin(seconds * 0.4f + i) * 10f;
			float y = height + 8 - life * (height + 16);
			float size = 1.5f + noise(i, 4f) * 2.5f;
			float fade = (float) Math.sin(life * Math.PI);
			Draw.roundRect(graphics, x, y, size, size, size / 2f, Fx.alpha(accent, 0.55f * fade * alpha));
		}
	}

	public static void logo(GuiGraphics graphics, float alpha) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, COLUMN_X, headerTop, 0f, 0f, LOGO_WIDTH, LOGO_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, Fx.alpha(TEXT, alpha));
		Draw.roundRect(graphics, COLUMN_X, headerTop + LOGO_HEIGHT + 4, 34, 2, 1f, Fx.alpha(ZyroxTheme.accent(), alpha));
	}

	public static void splash(GuiGraphics graphics, Font font, Component text, float alpha) {
		float pulse = 1f + (float) Math.sin(Util.getMillis() / 1000f * PULSE_SPEED) * PULSE;
		int y = headerTop + LOGO_HEIGHT + 11;
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(COLUMN_X, y + 4f);
		pose.scale(pulse, pulse);
		graphics.drawString(font, text, 0, -4, Fx.alpha(TEXT, alpha), true);
		pose.popMatrix();
	}

	public static void version(GuiGraphics graphics, Font font, int height, float alpha) {
		if (versionText == null) {
			versionText = "Zyrox Client " + FabricLoader.getInstance().getModContainer(ZyroxClient.MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
		}
		Type.REGULAR.draw(graphics, font, versionText, 2, height - VERSION_BOTTOM, Fx.alpha(MUTED, alpha));
	}
}
