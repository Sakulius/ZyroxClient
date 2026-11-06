package de.sakulius.zyrox.module;

import java.lang.management.ManagementFactory;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class Modules {
	private static final int RIGHT = 100_000;
	private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final String[] DIRECTIONS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
	private static final int TICKS_PER_SECOND = 20;
	private static final long MEGABYTE = 1024 * 1024;

	public static final UtilityModule ZOOM = new UtilityModule("zoom", "Zoom", "Hold C and scroll", true);
	public static final UtilityModule FULLBRIGHT = new UtilityModule("fullbright", "Fullbright", "See in the dark", false);
	public static final UtilityModule AUTO_SPRINT = new UtilityModule("autosprint", "Auto Sprint", "Run automatically", false);

	public static final LyricsModule LYRICS = new LyricsModule(-1, 30);
	public static final NowPlayingModule NOW_PLAYING = new NowPlayingModule(100_000, 130);

	public static final List<Module> ALL = List.of(
		new TextHudModule("fps", "FPS", "Frames per second", true, 4, 4,
			client -> client.getFps() + " FPS"),
		new TextHudModule("coordinates", "Coordinates", "Your position", true, 4, 21,
			client -> client.player == null ? "-" : "%d, %d, %d".formatted(client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ())),
		new TextHudModule("cps", "CPS", "Clicks per second", true, 4, 38,
			client -> ClickTracker.LEFT.perSecond() + " | " + ClickTracker.RIGHT.perSecond() + " CPS"),
		new KeystrokesModule(4, 55),
		new TextHudModule("ping", "Ping", "Server latency", false, RIGHT, 4, Modules::ping),
		new TextHudModule("compass", "Compass", "Facing direction", false, RIGHT, 21, Modules::compass),
		new TextHudModule("speed", "Speed", "Blocks per second", false, RIGHT, 38, Modules::speed),
		new TextHudModule("clock", "Clock", "Time of day", false, RIGHT, 55,
			client -> LocalTime.now().format(CLOCK_FORMAT)),
		new TextHudModule("memory", "Memory", "Java memory use", false, RIGHT, 72, Modules::memory),
		new TextHudModule("server", "Server", "Server address", false, RIGHT, 89, Modules::server),
		new ArmorModule(RIGHT, 106),
		new EffectsModule(90, 4),
		LYRICS,
		NOW_PLAYING,
		ZOOM,
		FULLBRIGHT,
		AUTO_SPRINT);

	private Modules() {
	}

	private static String ping(Minecraft client) {
		if (client.player == null || client.getConnection() == null)
			return "- ms";
		var info = client.getConnection().getPlayerInfo(client.player.getUUID());
		return info == null ? "- ms" : info.getLatency() + " ms";
	}

	private static String compass(Minecraft client) {
		if (client.player == null)
			return "-";
		float yaw = client.player.getYRot() % 360f;
		if (yaw < 0)
			yaw += 360f;
		int sector = Math.round(yaw / 45f) % DIRECTIONS.length;
		return "%s  %d°".formatted(DIRECTIONS[sector], Math.round(yaw) % 360);
	}

	private static String speed(Minecraft client) {
		if (client.player == null)
			return "- m/s";
		var movement = client.player.getDeltaMovement();
		return "%.1f m/s".formatted(Math.hypot(movement.x, movement.z) * TICKS_PER_SECOND);
	}

	private static String memory(Minecraft client) {
		var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		return "%d / %d MB".formatted(heap.getUsed() / MEGABYTE, heap.getMax() / MEGABYTE);
	}

	private static String server(Minecraft client) {
		var server = client.getCurrentServer();
		return server == null ? "Singleplayer" : server.ip;
	}
}
