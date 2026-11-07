package de.sakulius.zyrox.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class Fx {
	private Fx() {
	}

	public static int hsv(float hue, float saturation, float value) {
		float h = (hue - (float) Math.floor(hue)) * 6f;
		int sector = (int) h;
		float f = h - sector;
		float p = value * (1 - saturation);
		float q = value * (1 - saturation * f);
		float t = value * (1 - saturation * (1 - f));
		return switch (sector) {
			case 0 -> argb(value, t, p);
			case 1 -> argb(q, value, p);
			case 2 -> argb(p, value, t);
			case 3 -> argb(p, q, value);
			case 4 -> argb(t, p, value);
			default -> argb(value, p, q);
		};
	}

	private static int argb(float r, float g, float b) {
		return 0xFF000000 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
	}

	public static int shiftHue(int color, float amount) {
		float r = (color >> 16 & 255) / 255f;
		float g = (color >> 8 & 255) / 255f;
		float b = (color & 255) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float delta = max - Math.min(r, Math.min(g, b));
		if (delta == 0)
			return color;
		float hue;
		if (max == r)
			hue = (g - b) / delta;
		else if (max == g)
			hue = 2 + (b - r) / delta;
		else
			hue = 4 + (r - g) / delta;
		return hsv(hue / 6f + amount, delta / max, max);
	}

	public static int alpha(int color, float alpha) {
		int value = Math.clamp((int) ((color >>> 24) * alpha), 0, 255);
		return value << 24 | color & 0xFFFFFF;
	}

	public static int mix(int a, int b, float t) {
		int alpha = (int) ((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
		int red = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
		int green = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
		int blue = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	public static float approach(float current, float target, float dt, float speed) {
		return current + (target - current) * (1 - (float) Math.exp(-speed * dt));
	}

	public static float easeOutCubic(float t) {
		float inverse = 1 - t;
		return 1 - inverse * inverse * inverse;
	}

	public static float easeOutBack(float t) {
		float inverse = t - 1;
		return 1 + 2.2f * inverse * inverse * inverse + 1.2f * inverse * inverse;
	}

	public static void roundRect(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
		graphics.fill(x1 + 1, y1, x2 - 1, y2, color);
		graphics.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		graphics.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	public static void quad(GuiGraphics graphics, float x1, float y1, float x2, float y2, int top, int bottom) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x1, y1);
		pose.scale(x2 - x1, y2 - y1);
		graphics.fillGradient(0, 0, 1, 1, top, bottom);
		pose.popMatrix();
	}

	public static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color) {
		float length = (float) Math.hypot(x2 - x1, y2 - y1);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x1, y1);
		pose.rotate((float) Math.atan2(y2 - y1, x2 - x1));
		graphics.fill(0, 0, Math.max(1, (int) length), 1, color);
		pose.popMatrix();
	}
}
