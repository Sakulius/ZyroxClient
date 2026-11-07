package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.screen.CameraRig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void zyrox$hideDuringMenu(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (CameraRig.hidesHud())
			ci.cancel();
	}
}
