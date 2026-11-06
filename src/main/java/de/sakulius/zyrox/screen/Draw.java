package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.platform.NativeImage;
import de.sakulius.zyrox.ZyroxClient;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class Draw {
	private interface Coverage {
		float at(float distanceFromCenter);
	}

	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final float FLAT_TAPER = 0.002f;

	public static float elementScale = 1f;

	private Draw() {
	}

	public static int scale() {
		return Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale() * elementScale));
	}

	private static float edge(float distance, float radius) {
		return Math.clamp(radius - distance + 0.5f, 0f, 1f);
	}

	private static Identifier radial(String key, int radius, Coverage coverage) {
		return TEXTURES.computeIfAbsent(key, name -> {
			int size = radius * 2;
			var image = new NativeImage(size, size, false);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float distance = (float) Math.hypot(x + 0.5f - radius, y + 0.5f - radius);
					int alpha = Math.clamp((int) (coverage.at(distance) * 255f), 0, 255);
					image.setPixel(x, y, alpha << 24 | 0xFFFFFF);
				}
			}
			var id = Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "generated/" + name);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "zyrox " + name, image));
			return id;
		});
	}

	private static Identifier disc(int radius) {
		return radial("disc_" + radius, radius, distance -> edge(distance, radius));
	}

	private static Identifier ring(int radius, int thickness) {
		return radial("ring_" + radius + "_" + thickness, radius, distance -> edge(distance, radius) - edge(distance, radius - thickness));
	}

	private static Identifier halo(int inner, int spread) {
		return radial("halo_" + inner + "_" + spread, inner + spread, distance -> {
			if (distance < inner)
				return 0f;
			float falloff = Math.max(0f, 1f - (distance - inner) / spread);
			return falloff * falloff;
		});
	}

	private static void quarters(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int w, int h, int r, int color) {
		int size = r * 2;
		blit(graphics, texture, x, y, 0, 0, r, r, r, r, size, color);
		blit(graphics, texture, x + w - r, y, r, 0, r, r, r, r, size, color);
		blit(graphics, texture, x, y + h - r, 0, r, r, r, r, r, size, color);
		blit(graphics, texture, x + w - r, y + h - r, r, r, r, r, r, r, size, color);
	}

	private static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int u, int v, int w, int h, int regionW, int regionH, int size, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, regionW, regionH, size, size, color);
	}

	public static void roundRect(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		int s = scale();
		int px = Math.round(x * s);
		int py = Math.round(y * s);
		int pw = Math.round(w * s);
		int ph = Math.round(h * s);
		int r = Math.min(Math.round(radius * s), Math.min(pw, ph) / 2);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		if (r > 0) {
			graphics.fill(px + r, py, px + pw - r, py + r, color);
			graphics.fill(px + r, py + ph - r, px + pw - r, py + ph, color);
			quarters(graphics, disc(r), px, py, pw, ph, r, color);
		}
		graphics.fill(px, py + r, px + pw, py + ph - r, color);
		pose.popMatrix();
	}

	public static void outline(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		int s = scale();
		int px = Math.round(x * s);
		int py = Math.round(y * s);
		int pw = Math.round(w * s);
		int ph = Math.round(h * s);
		int r = Math.min(Math.round(radius * s), Math.min(pw, ph) / 2);
		int t = Math.max(1, s / 2);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		graphics.fill(px + r, py, px + pw - r, py + t, color);
		graphics.fill(px + r, py + ph - t, px + pw - r, py + ph, color);
		graphics.fill(px, py + r, px + t, py + ph - r, color);
		graphics.fill(px + pw - t, py + r, px + pw, py + ph - r, color);
		quarters(graphics, ring(r, t), px, py, pw, ph, r, color);
		pose.popMatrix();
	}

	public static void glow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, float spread, int color) {
		int s = scale();
		int inner = Math.round(radius * s);
		int reach = Math.round(spread * s);
		int r = inner + reach;
		int px = Math.round(x * s) - reach;
		int py = Math.round(y * s) - reach;
		int pw = Math.round(w * s) + reach * 2;
		int ph = Math.round(h * s) + reach * 2;
		int size = r * 2;
		var texture = halo(inner, reach);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		quarters(graphics, texture, px, py, pw, ph, r, color);
		blit(graphics, texture, px + r, py, r, 0, pw - r * 2, reach, 1, reach, size, color);
		blit(graphics, texture, px + r, py + ph - reach, r, size - reach, pw - r * 2, reach, 1, reach, size, color);
		blit(graphics, texture, px, py + r, 0, r, reach, ph - r * 2, reach, 1, size, color);
		blit(graphics, texture, px + pw - reach, py + r, size - reach, r, reach, ph - r * 2, reach, 1, size, color);
		pose.popMatrix();
	}

	public static void card(GuiGraphicsExtractor graphics, float centerX, float centerY, float w, float h, float radius, float taper, int top, int bottom, int border) {
		if (Math.abs(taper) < FLAT_TAPER) {
			flatCard(graphics, centerX - w / 2, centerY - h / 2, w, h, radius, top, bottom, border);
			return;
		}
		int s = scale();
		int left = Math.round((centerX - w / 2) * s);
		int right = Math.round((centerX + w / 2) * s);
		float middle = centerY * s;
		float fullHeight = h * s;
		float r = radius * s;
		int stepInside = Math.max(2, s);
		int corner = (int) Math.ceil(r);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		for (int x = left, next; x < right; x = next) {
			boolean inCorner = x - left < corner || right - x <= corner;
			next = Math.min(x + (inCorner ? 1 : stepInside), right);
			float edgeTop = edge(x, next, left, right, middle, fullHeight, r, taper);
			float mirror = 2 * middle - edgeTop;
			int a = (int) Math.ceil(edgeTop);
			int b = (int) Math.floor(mirror);
			if (b <= a)
				continue;
			float gradientTop = (a - (middle - fullHeight / 2)) / fullHeight;
			float gradientBottom = (b - (middle - fullHeight / 2)) / fullHeight;
			graphics.fillGradient(x, a, next, b, Fx.mix(top, bottom, Math.clamp(gradientTop, 0f, 1f)), Fx.mix(top, bottom, Math.clamp(gradientBottom, 0f, 1f)));
			graphics.fill(x, a - 1, next, a, Fx.alpha(border, a - edgeTop));
			graphics.fill(x, b, next, b + 1, Fx.alpha(border, mirror - b));
		}
		pose.popMatrix();
	}

	private static void flatCard(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int top, int bottom, int border) {
		int s = scale();
		int px = Math.round(x * s);
		int py = Math.round(y * s);
		int pw = Math.round(w * s);
		int ph = Math.round(h * s);
		int r = Math.round(radius * s);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		graphics.fill(px + r, py, px + pw - r, py + r, top);
		graphics.fillGradient(px, py + r, px + pw, py + ph - r, top, bottom);
		graphics.fill(px + r, py + ph - r, px + pw - r, py + ph, bottom);
		var texture = disc(r);
		int size = r * 2;
		blit(graphics, texture, px, py, 0, 0, r, r, r, r, size, top);
		blit(graphics, texture, px + pw - r, py, r, 0, r, r, r, r, size, top);
		blit(graphics, texture, px, py + ph - r, 0, r, r, r, r, r, size, bottom);
		blit(graphics, texture, px + pw - r, py + ph - r, r, r, r, r, r, r, size, bottom);
		pose.popMatrix();
		outline(graphics, x, y, w, h, radius, border);
	}

	private static float edge(int x, int next, int left, int right, float middle, float fullHeight, float r, float taper) {
		float center = (x + next) / 2f;
		float u = (center - left) / (right - left);
		float half = fullHeight * (1f - (u - 0.5f) * 2f * taper) / 2f;
		float distance = Math.min(center - left, right - center);
		float inset = distance < r ? r - (float) Math.sqrt(Math.max(0f, r * r - (r - distance) * (r - distance))) : 0f;
		return middle - half + inset;
	}
}
