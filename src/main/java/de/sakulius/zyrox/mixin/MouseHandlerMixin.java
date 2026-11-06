package de.sakulius.zyrox.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import de.sakulius.zyrox.module.ClickTracker;
import de.sakulius.zyrox.module.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void zyrox$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action != InputConstants.PRESS || Minecraft.getInstance().gui.screen() != null)
			return;
		if (info.button() == InputConstants.MOUSE_BUTTON_LEFT)
			ClickTracker.LEFT.click();
		else if (info.button() == InputConstants.MOUSE_BUTTON_RIGHT)
			ClickTracker.RIGHT.click();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void zyrox$scrollZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (Zoom.isHeld()) {
			Zoom.scroll(vertical);
			ci.cancel();
		}
	}

	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object zyrox$zoomSensitivity(OptionInstance<Double> sensitivity) {
		return sensitivity.get() * Zoom.sensitivityScale();
	}
}
