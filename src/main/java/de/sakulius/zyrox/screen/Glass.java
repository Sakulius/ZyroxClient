package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import de.sakulius.zyrox.HudStyle;
import de.sakulius.zyrox.ZyroxClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;

public final class Glass {
	private static final List<Identifier> CHAINS = List.of(
		Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "hud_blur_light"),
		Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "hud_blur_medium"),
		Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "hud_blur_strong"));

	private static RenderTarget target;
	private static RenderTarget retired;
	private static boolean ready;

	private Glass() {
	}

	public static boolean isReady() {
		return ready;
	}

	public static void capture(Minecraft client, RenderTarget main, GraphicsResourceAllocator allocator) {
		if (retired != null) {
			retired.destroyBuffers();
			retired = null;
		}
		if (HudStyle.panelStyle != HudStyle.GLASS || client.level == null) {
			ready = false;
			return;
		}
		if (target == null || target.width != main.width || target.height != main.height) {
			retired = target;
			target = new TextureTarget("zyrox glass", main.width, main.height, main.getColorTexture().getFormat(), null);
		}
		target.copyColorFrom(main);
		var chain = client.getShaderManager().getPostChain(CHAINS.get(Math.clamp(HudStyle.blur, 0, CHAINS.size() - 1)), LevelTargetBundle.MAIN_TARGETS);
		if (chain != null)
			chain.process(target, allocator);
		ready = chain != null;
	}

	public static void draw(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int radius) {
		if (!ready || target == null)
			return;
		var client = Minecraft.getInstance();
		float guiScale = (float) client.getWindow().getGuiScale();
		int s = Draw.scale();
		int px = Math.round(x * s);
		int py = Math.round(y * s);
		int pw = Math.round(w * s);
		int ph = Math.round(h * s);
		int r = Math.min(radius * s, Math.min(pw, ph) / 2);
		var view = target.getColorTextureView();
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / s, 1f / s);
		var corner = new Vector2f();
		pose.transformPosition(px, py, corner);
		float u0 = corner.x * guiScale / target.width;
		float v0 = corner.y * guiScale / target.height;
		pose.transformPosition(px + pw, py + ph, corner);
		float u1 = corner.x * guiScale / target.width;
		float v1 = corner.y * guiScale / target.height;
		float uPerPixel = (u1 - u0) / Math.max(1, pw);
		float vPerPixel = (v1 - v0) / Math.max(1, ph);
		for (int row = 0; row < r; row++) {
			float fromCorner = r - row - 0.5f;
			int inset = r - Math.round((float) Math.sqrt(Math.max(0f, r * r - fromCorner * fromCorner)));
			band(graphics, view, sampler, px + inset, py + row, pw - inset * 2, 1, u0 + inset * uPerPixel, v0 + row * vPerPixel, uPerPixel, vPerPixel);
			band(graphics, view, sampler, px + inset, py + ph - row - 1, pw - inset * 2, 1, u0 + inset * uPerPixel, v0 + (ph - row - 1) * vPerPixel, uPerPixel, vPerPixel);
		}
		band(graphics, view, sampler, px, py + r, pw, ph - r * 2, u0, v0 + r * vPerPixel, uPerPixel, vPerPixel);
		pose.popMatrix();
	}

	private static void band(GuiGraphicsExtractor graphics, com.mojang.renderpearl.api.textures.GpuTextureView view, com.mojang.renderpearl.api.textures.GpuSampler sampler, int x, int y, int w, int h, float u, float v, float uPerPixel, float vPerPixel) {
		if (w <= 0 || h <= 0)
			return;
		graphics.blit(view, sampler, x, y, x + w, y + h, u, u + w * uPerPixel, 1f - v, 1f - (v + h * vPerPixel));
	}
}
