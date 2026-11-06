package de.sakulius.zyrox.screen;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CameraRig {
	public record Pose(Vec3 position, float yaw, float pitch) {
	}

	private static final double DISTANCE = 2.1;
	private static final double SIDE = 1.25;
	private static final double HEIGHT = -0.55;
	private static final double WALL_MARGIN = 0.25;
	private static final float MENU_FOV = 70f;
	private static final float TRAVEL_SECONDS = 0.75f;
	private static final float PARALLAX_YAW = 2.5f;
	private static final float PARALLAX_PITCH = 1.5f;
	private static final float BACK_VIEW = 90f;
	private static final float HIDE_HUD_FROM = 0.05f;

	private static boolean active;
	private static float progress;
	private static CameraType savedType;
	private static float anchorYaw;
	private static float orbit;
	private static float orbitTarget;
	private static float mouseX = 0.5f;
	private static float mouseY = 0.5f;
	private static float headYaw;
	private static float headPitch;
	private static long lastFrame = System.nanoTime();

	private CameraRig() {
	}

	public static void setActive(boolean value) {
		var client = Minecraft.getInstance();
		if (value && client.player == null)
			return;
		if (value && !active) {
			if (savedType == null) {
				savedType = client.options.getCameraType();
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			}
			if (progress == 0f)
				anchorYaw = client.player.yBodyRot;
		}
		active = value;
	}

	public static void setOrbit(float degrees) {
		orbitTarget = degrees;
	}

	public static void addOrbit(float degrees) {
		orbitTarget += degrees;
	}

	public static void look(float x, float y) {
		mouseX = Math.clamp(x, 0f, 1f);
		mouseY = Math.clamp(y, 0f, 1f);
	}

	public static float amount() {
		return progress * progress * (3f - 2f * progress);
	}

	public static boolean hidesHud() {
		return amount() > HIDE_HUD_FROM;
	}

	public static float fov(float normal) {
		return Mth.lerp(amount(), normal, MENU_FOV);
	}

	public static Pose pose(Entity entity, float partialTick) {
		update();
		if (progress == 0f || entity == null)
			return null;
		float t = amount();
		var eye = entity.getEyePosition(partialTick);
		double angle = Math.toRadians(anchorYaw + orbit);
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		var target = eye.add(-sin * DISTANCE + cos * SIDE, HEIGHT, cos * DISTANCE + sin * SIDE);
		var position = eye.lerp(clip(entity, eye, target), t);

		float startYaw = entity.getViewYRot(partialTick);
		float endYaw = anchorYaw + orbit + 180f + (mouseX - 0.5f) * PARALLAX_YAW;
		float yaw = startYaw + Mth.wrapDegrees(endYaw - startYaw) * t;
		float pitch = Mth.lerp(t, entity.getViewXRot(partialTick), (mouseY - 0.5f) * PARALLAX_PITCH);
		return new Pose(position, yaw, pitch);
	}

	private static Vec3 clip(Entity entity, Vec3 from, Vec3 to) {
		var hit = entity.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
		if (hit.getType() == HitResult.Type.MISS)
			return to;
		var back = from.subtract(hit.getLocation());
		double length = back.length();
		return length <= WALL_MARGIN ? from : hit.getLocation().add(back.scale(WALL_MARGIN / length));
	}

	private static void update() {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		progress = Math.clamp(progress + (active ? dt : -dt) / TRAVEL_SECONDS, 0f, 1f);
		orbit = Fx.approach(orbit, orbitTarget, dt, 6f);
		headYaw = Fx.approach(headYaw, Math.clamp((0.27f - mouseX) * 110f, -55f, 55f), dt, 9f);
		headPitch = Fx.approach(headPitch, Math.clamp((mouseY - 0.42f) * 60f, -30f, 35f), dt, 9f);
		if (progress == 0f && !active) {
			orbit = 0f;
			orbitTarget = 0f;
			if (savedType != null) {
				Minecraft.getInstance().options.setCameraType(savedType);
				savedType = null;
			}
		}
	}

	public static void applyHead(Entity entity, LivingEntityRenderState state) {
		if (progress == 0f || entity != Minecraft.getInstance().player)
			return;
		float wrapped = Mth.wrapDegrees(orbit);
		if (Math.abs(wrapped) >= BACK_VIEW)
			return;
		float t = amount();
		state.yRot = Mth.lerp(t, state.yRot, headYaw);
		state.xRot = Mth.lerp(t, state.xRot, headPitch);
	}
}
