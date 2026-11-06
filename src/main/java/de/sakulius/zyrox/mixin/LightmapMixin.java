package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.module.Modules;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void zyrox$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (Modules.FULLBRIGHT.enabled) {
			state.nightVisionEffectIntensity = 1f;
			state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
		}
	}
}
