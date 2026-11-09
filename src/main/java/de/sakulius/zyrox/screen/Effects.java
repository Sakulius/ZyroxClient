package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import de.sakulius.zyrox.ZyroxClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;

public final class Effects {
	public static final List<String> MOTION_BLUR = List.of("Off", "Low", "Medium", "High");
	public static final List<String> MENU_BLUR = List.of("Off", "Soft", "Clean", "Strong");
	private static final List<String> MOTION_CHAINS = List.of("low", "medium", "high");
	private static final int[] MENU_LEVELS = { 0, 3, 5, 8 };

	public static int motionBlur;
	public static int menuBlur = 2;
	public static float menuAmount;

	private static Identifier activeMotion;

	private Effects() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, path);
	}

	public static void apply(Minecraft client, RenderTarget main, GraphicsResourceAllocator allocator) {
		if (!(client.gui.screen() instanceof ZyroxScreen))
			menuAmount = 0f;
		if (client.level == null)
			return;
		var shaders = client.getShaderManager();
		if (motionBlur > 0) {
			var motion = id("motion_blur_" + MOTION_CHAINS.get(Math.clamp(motionBlur - 1, 0, MOTION_CHAINS.size() - 1)));
			var chain = shaders.getPostChain(motion, LevelTargetBundle.MAIN_TARGETS);
			if (activeMotion != null && !activeMotion.equals(motion)) {
				var old = shaders.getPostChain(activeMotion, LevelTargetBundle.MAIN_TARGETS);
				if (old != null)
					old.closePersistentTargets();
			}
			activeMotion = motion;
			if (chain != null)
				chain.process(main, allocator);
		} else if (activeMotion != null) {
			var old = shaders.getPostChain(activeMotion, LevelTargetBundle.MAIN_TARGETS);
			if (old != null)
				old.closePersistentTargets();
			activeMotion = null;
		}
		int strongest = MENU_LEVELS[Math.clamp(menuBlur, 0, MENU_LEVELS.length - 1)];
		int level = Math.round(menuAmount * strongest);
		if (level <= 0)
			return;
		var chain = shaders.getPostChain(id("menu_blur_" + level), LevelTargetBundle.MAIN_TARGETS);
		if (chain != null)
			chain.process(main, allocator);
	}
}
