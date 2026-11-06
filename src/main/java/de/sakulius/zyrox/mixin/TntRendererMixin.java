package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.module.Modules;
import de.sakulius.zyrox.module.TntTimer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntRenderer.class)
public class TntRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/PrimedTnt;Lnet/minecraft/client/renderer/entity/state/TntRenderState;F)V", at = @At("TAIL"))
	private void zyrox$fuseLabel(PrimedTnt entity, TntRenderState state, float partialTick, CallbackInfo ci) {
		if (!Modules.TNT_TIMER.enabled)
			return;
		state.nameTag = TntTimer.label(state.fuseRemainingInTicks);
		state.nameTagAttachment = TntTimer.LABEL_POSITION;
	}
}
