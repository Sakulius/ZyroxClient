package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.module.Modules;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public class LightmapMixin {
	@Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
	private boolean zyrox$fullbright(LocalPlayer player, Holder<MobEffect> effect) {
		if (Modules.FULLBRIGHT.enabled && effect == MobEffects.NIGHT_VISION)
			return true;
		return player.hasEffect(effect);
	}
}
