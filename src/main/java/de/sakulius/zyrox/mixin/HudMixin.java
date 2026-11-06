package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.screen.CameraRig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void zyrox$hideDuringMenu(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (CameraRig.hidesHud())
			ci.cancel();
	}
}
