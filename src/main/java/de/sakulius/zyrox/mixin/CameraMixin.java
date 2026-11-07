package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.screen.CameraRig;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Entity entity;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "setup", at = @At("TAIL"))
	private void zyrox$menuCamera(Level level, Entity focused, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
		var pose = CameraRig.pose(entity, partialTick);
		if (pose != null) {
			setPosition(pose.position());
			setRotation(pose.yaw(), pose.pitch());
		}
	}

}
