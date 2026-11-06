package de.sakulius.zyrox.module;

import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.screen.Fx;
import net.minecraft.client.Minecraft;

public final class Zoom {
	private static final float MIN = 1.5f;
	private static final float MAX = 12f;
	private static final float STEP = 1.2f;
	private static final float SPEED = 14f;
	private static final float SETTLED = 0.002f;

	private static float target = 4f;
	private static float current = 1f;
	private static long lastFrame = System.nanoTime();

	private Zoom() {
	}

	public static boolean isHeld() {
		return Modules.ZOOM.enabled && ZyroxClient.zoomKey != null && ZyroxClient.zoomKey.isDown()
			&& Minecraft.getInstance().gui.screen() == null;
	}

	public static void scroll(double amount) {
		if (amount != 0)
			target = Math.clamp(amount > 0 ? target * STEP : target / STEP, MIN, MAX);
	}

	public static float divisor() {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		current = Fx.approach(current, isHeld() ? target : 1f, dt, SPEED);
		if (Math.abs(current - 1f) < SETTLED)
			current = 1f;
		return current;
	}

	public static float sensitivityScale() {
		return 1f / current;
	}
}
