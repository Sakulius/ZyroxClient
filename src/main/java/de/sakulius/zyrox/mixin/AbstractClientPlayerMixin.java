package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.module.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void zyrox$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		var player = (AbstractClientPlayer) (Object) this;
		cir.setReturnValue(Cosmetics.apply(player.getUUID(), player == Minecraft.getInstance().player, cir.getReturnValue()));
	}
}
