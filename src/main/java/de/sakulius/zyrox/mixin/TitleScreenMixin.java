package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.module.MenuSplash;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
	@Shadow
	private SplashRenderer splash;

	@Unique
	private SplashRenderer zyrox$gameSplash;

	@Inject(method = "init", at = @At("TAIL"))
	private void zyrox$init(CallbackInfo ci) {
		if (zyrox$gameSplash == null)
			zyrox$gameSplash = splash;
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void zyrox$useText(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		var custom = MenuSplash.custom();
		splash = custom != null ? custom : zyrox$gameSplash;
	}
}
