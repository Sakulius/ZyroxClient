package de.sakulius.zyrox.module;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class TntTimer {
	private static final float TICKS_PER_SECOND = 20f;
	private static final float FULL_FUSE_TICKS = 80f;
	private static final double ABOVE_BLOCK = 1.7;
	private static final int SAFE = 0x55FF55;
	private static final int DANGER = 0xFF5555;
	private static final int MIDDLE = 0xFFD84A;

	public static final Vec3 LABEL_POSITION = new Vec3(0, ABOVE_BLOCK, 0);

	private TntTimer() {
	}

	public static String seconds(float fuseTicks) {
		return String.format(Locale.ROOT, "%.1fs", Math.max(0f, fuseTicks) / TICKS_PER_SECOND);
	}

	public static int color(float fuseTicks) {
		float left = Math.clamp(fuseTicks / FULL_FUSE_TICKS, 0f, 1f);
		return left > 0.5f ? blend(MIDDLE, SAFE, (left - 0.5f) * 2f) : blend(DANGER, MIDDLE, left * 2f);
	}

	private static int blend(int from, int to, float t) {
		int red = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
		int green = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
		int blue = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
		return red << 16 | green << 8 | blue;
	}

	public static Component label(float fuseTicks) {
		return Component.literal(seconds(fuseTicks)).withColor(color(fuseTicks)).withStyle(ChatFormatting.BOLD);
	}
}
