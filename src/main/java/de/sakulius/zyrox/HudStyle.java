package de.sakulius.zyrox;

import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Fx;
import de.sakulius.zyrox.screen.Glass;
import de.sakulius.zyrox.screen.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudStyle {
	public record Swatch(String name, int color) {
	}

	public static final int GLASS = 0;
	public static final int SOLID = 1;
	public static final int NO_PANEL = 2;
	public static final List<String> PANEL_STYLES = List.of("Glass", "Solid", "None");
	public static final List<String> BLURS = List.of("Light", "Medium", "Strong");
	public static final List<String> CORNERS = List.of("Sharp", "Small", "Medium", "Large", "Round");
	public static final List<String> FONTS = List.of("Inter", "Minecraft");
	public static final List<String> OPACITIES;
	public static final List<Swatch> TEXT_COLORS = List.of(
		new Swatch("Accent", 0),
		new Swatch("White", 0xFFFFFFFF),
		new Swatch("Gray", 0xFFA6ABB5),
		new Swatch("Red", 0xFFF2716B),
		new Swatch("Orange", 0xFFF5A342),
		new Swatch("Green", 0xFF57E05B),
		new Swatch("Blue", 0xFF4FA3F7),
		new Swatch("Purple", 0xFFC58BFA));
	public static final List<Swatch> TINTS = List.of(
		new Swatch("Dark", 0xFF101116),
		new Swatch("Black", 0xFF000000),
		new Swatch("Accent", 0),
		new Swatch("Red", 0xFF5A1414),
		new Swatch("Green", 0xFF123F1E),
		new Swatch("Blue", 0xFF12305A),
		new Swatch("Purple", 0xFF34185A),
		new Swatch("White", 0xFFFFFFFF));

	private static final int[] RADII = { 0, 2, 4, 6, 8 };
	private static final int OPACITY_STEPS = 10;
	private static final int BORDER = 0x26FFFFFF;
	private static final int SHADOW = 0x70000000;
	private static final float SHADOW_SPREAD = 4f;
	private static final float INNER_ROUNDING = 1f;
	private static final int SLOT_HIGHLIGHT = 0x38FFFFFF;
	private static final Pattern WORDS = Pattern.compile("\\S+\\s*");
	private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");

	static {
		var opacities = new ArrayList<String>();
		for (int i = 0; i <= OPACITY_STEPS; i++)
			opacities.add(i * OPACITY_STEPS + "%");
		OPACITIES = List.copyOf(opacities);
	}

	public static int panelStyle;
	public static int blur;
	public static int opacity;
	public static int tint;
	public static int corners;
	public static boolean border;
	public static boolean shadow;
	public static boolean twoTone;
	public static int labelColor;
	public static int valueColor;
	public static boolean textShadow;
	public static int font;
	public static boolean scoreboard;
	public static boolean hideScores;
	public static boolean hotbar;

	private static boolean drawing;

	static {
		reset();
	}

	private HudStyle() {
	}

	public static void reset() {
		panelStyle = GLASS;
		blur = 1;
		opacity = 4;
		tint = 0;
		corners = 2;
		border = true;
		shadow = false;
		twoTone = true;
		labelColor = 0;
		valueColor = 1;
		textShadow = false;
		font = 0;
		scoreboard = true;
		hideScores = false;
		hotbar = true;
	}

	public static void begin() {
		drawing = true;
	}

	public static void end() {
		drawing = false;
	}

	public static boolean usesMinecraftFont() {
		return drawing && font == 1;
	}

	public static boolean usesTextShadow() {
		return drawing && textShadow;
	}

	public static int radius() {
		return RADII[Math.clamp(corners, 0, RADII.length - 1)];
	}

	private static int color(Swatch swatch) {
		return swatch.color() == 0 ? ZyroxTheme.accent() : swatch.color();
	}

	public static int swatchColor(List<Swatch> swatches, int index) {
		return color(swatches.get(Math.clamp(index, 0, swatches.size() - 1)));
	}

	public static int label() {
		return swatchColor(TEXT_COLORS, labelColor);
	}

	public static int value() {
		return swatchColor(TEXT_COLORS, valueColor);
	}

	private static int tintColor(float alpha) {
		return Fx.alpha(swatchColor(TINTS, tint) | 0xFF000000, opacity / (float) OPACITY_STEPS * alpha);
	}

	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		panel(graphics, x, y, w, h, 1f);
	}

	public static void panel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float alpha) {
		if (panelStyle == NO_PANEL)
			return;
		int radius = radius();
		if (shadow)
			Draw.glow(graphics, x, y, w, h, radius, SHADOW_SPREAD, Fx.alpha(SHADOW, alpha));
		if (panelStyle == GLASS && alpha >= 1f)
			Glass.draw(graphics, x, y, w, h, radius);
		Draw.roundRect(graphics, x, y, w, h, radius, tintColor(alpha));
		if (border)
			Draw.outline(graphics, x, y, w, h, radius, Fx.alpha(BORDER, alpha));
	}

	public static void slotHighlight(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		Draw.roundRect(graphics, x, y, w, h, Math.max(0, radius() - INNER_ROUNDING), SLOT_HIGHLIGHT);
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int left, int top) {
		if (!twoTone || ZyroxTheme.chromaHud) {
			Type.REGULAR.draw(graphics, font, text, x, y, ZyroxTheme.hudText(left, top));
			return;
		}
		var words = WORDS.matcher(text);
		int position = x;
		while (words.find()) {
			var word = words.group();
			Type.REGULAR.draw(graphics, font, word, position, y, HAS_LETTER.matcher(word).find() ? label() : value());
			position += Type.REGULAR.width(font, word);
		}
	}
}
