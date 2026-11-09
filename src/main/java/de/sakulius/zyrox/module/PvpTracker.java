package de.sakulius.zyrox.module;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class PvpTracker {
	private static final int CONFIRM_TICKS = 12;
	private static final int COMBO_TICKS = 60;
	private static final int REACH_TICKS = 100;
	private static final int TARGET_TICKS = 160;
	private static final int FIGHT_TICKS = 200;

	private static LivingEntity pending;
	private static float pendingReach;
	private static int pendingTicks;
	private static int combo;
	private static float reach = -1f;
	private static int sinceHit;
	private static boolean wasHurt;
	private static LivingEntity target;
	private static int targetTicks;
	private static int hits;
	private static int taken;
	private static int sinceFight = FIGHT_TICKS;

	private PvpTracker() {
	}

	public static int combo() {
		return combo;
	}

	public static LivingEntity target() {
		return target;
	}

	public static int hits() {
		return hits;
	}

	public static int taken() {
		return taken;
	}

	public static float reach() {
		return reach;
	}

	public static String reachText() {
		return reach < 0f ? "- m" : String.format(Locale.ROOT, "%.2f m", reach);
	}

	private static float distance(Player player, Entity target) {
		Vec3 eye = player.getEyePosition();
		var box = target.getBoundingBox();
		double x = Mth.clamp(eye.x, box.minX, box.maxX) - eye.x;
		double y = Mth.clamp(eye.y, box.minY, box.maxY) - eye.y;
		double z = Mth.clamp(eye.z, box.minZ, box.maxZ) - eye.z;
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	public static InteractionResult onAttack(Player player, Entity entity) {
		if (entity instanceof LivingEntity target && target.hurtTime == 0) {
			pending = target;
			pendingReach = distance(player, target);
			pendingTicks = CONFIRM_TICKS;
		}
		return InteractionResult.PASS;
	}

	public static void tick(Minecraft client) {
		var player = client.player;
		if (player == null) {
			pending = null;
			combo = 0;
			reach = -1f;
			wasHurt = false;
			target = null;
			hits = 0;
			taken = 0;
			return;
		}
		if (pending != null) {
			if (pending.hurtTime > 0) {
				combo++;
				hits++;
				sinceFight = 0;
				target = pending;
				targetTicks = TARGET_TICKS;
				reach = pendingReach;
				sinceHit = 0;
				pending = null;
			} else if (--pendingTicks <= 0) {
				pending = null;
			}
		}
		boolean hurt = player.hurtTime > 0;
		if (hurt && !wasHurt) {
			combo = 0;
			taken++;
			sinceFight = 0;
		}
		if (client.crosshairPickEntity instanceof LivingEntity looked && looked != player && looked.isAlive()) {
			target = looked;
			targetTicks = TARGET_TICKS;
		}
		if (target != null && (--targetTicks <= 0 || !target.isAlive() || target.isRemoved()))
			target = null;
		if (++sinceFight > FIGHT_TICKS) {
			hits = 0;
			taken = 0;
		}
		wasHurt = hurt;
		sinceHit++;
		if (sinceHit > COMBO_TICKS)
			combo = 0;
		if (sinceHit > REACH_TICKS)
			reach = -1f;
	}
}
