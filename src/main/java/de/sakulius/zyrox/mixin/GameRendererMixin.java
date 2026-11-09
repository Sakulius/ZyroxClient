package de.sakulius.zyrox.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import de.sakulius.zyrox.screen.Effects;
import de.sakulius.zyrox.screen.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private RenderTarget mainRenderTarget;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
	private void zyrox$captureGlass(CallbackInfo ci) {
		Effects.apply(minecraft, mainRenderTarget, resourcePool);
		Glass.capture(minecraft, mainRenderTarget, resourcePool);
	}
}
