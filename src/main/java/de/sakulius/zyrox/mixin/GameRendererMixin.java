package de.sakulius.zyrox.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import de.sakulius.zyrox.screen.CameraRig;
import de.sakulius.zyrox.screen.Glass;
import de.sakulius.zyrox.module.Modules;
import de.sakulius.zyrox.module.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
	private void zyrox$captureGlass(CallbackInfo ci) {
		Glass.capture(minecraft, minecraft.getMainRenderTarget(), resourcePool);
	}

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void zyrox$fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (useFovSetting)
			cir.setReturnValue(CameraRig.fov(cir.getReturnValue()) / Zoom.divisor());
	}

	@Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
	private static void zyrox$fullbright(LivingEntity entity, float partialTick, CallbackInfoReturnable<Float> cir) {
		if (Modules.FULLBRIGHT.enabled)
			cir.setReturnValue(1f);
	}
}
