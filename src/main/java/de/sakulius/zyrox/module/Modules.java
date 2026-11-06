package de.sakulius.zyrox.module;

import java.lang.management.ManagementFactory;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;

public final class Modules {
	private static final int RIGHT = 100_000;
	private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final String[] DIRECTIONS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
	private static final int TICKS_PER_SECOND = 20;
	private static final long MEGABYTE = 1024 * 1024;

	public static final UtilityModule ZOOM = new UtilityModule("zoom", "Zoom", "Hold C and scroll", true);
	public static final UtilityModule FULLBRIGHT = new UtilityModule("fullbright", "Fullbright", "See in the dark", false);
	public static final UtilityModule AUTO_SPRINT = new UtilityModule("autosprint", "Auto Sprint", "Run automatically", false);

	public static final UtilityModule BADGE = new UtilityModule("badge", "Zyrox Badge", "Mark Zyrox players by name", true);
	public static final PvpModule TNT_TIMER = new PvpModule("tnt_timer", "TNT Timer", "Fuse time above TNT", true, Identifier.withDefaultNamespace("textures/block/tnt_side.png"), 16);
	public static final Module WAYPOINTS = new Module("waypoints", "Show waypoints", "Name and distance on screen", Module.Category.WAYPOINTS, true) {
	};

	public static final LyricsModule LYRICS = new LyricsModule(-1, 30);
	public static final NowPlayingModule NOW_PLAYING = new NowPlayingModule(100_000, 130);

	private static final int CENTER = -1;
	private static final float LOW_HEALTH = 6f;
	private static final float LOW_ARMOR = 0.15f;
	private static final EquipmentSlot[] ARMOR_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

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
		AUTO_SPRINT,
		BADGE,
		TNT_TIMER,
		new TextHudModule("combo", "Combo", "Hits in a row", Module.Category.PVP, CENTER, 130,
			client -> PvpTracker.combo() + " Combo"),
		new TextHudModule("reach", "Reach", "Distance of your last hit", Module.Category.PVP, CENTER, 147,
			client -> PvpTracker.reachText()),
		new ItemCounterModule(4, 140),
		new TextHudModule("block_counter", "Blocks", "Blocks in your inventory", Module.Category.PVP, CENTER, 164, Modules::blocks),
		new WarningModule("armor_warning", "Armor Alert", "Warns before armor breaks", CENTER, 96, "Armor low", Modules::armorWarning),
		new WarningModule("low_health", "Low Health", "Warns at three hearts", CENTER, 80, "Low health", Modules::healthWarning),
		WAYPOINTS);

	private Modules() {
	}

	private static String blocks(Minecraft client) {
		if (client.player == null)
			return "0 Blocks";
		int count = 0;
		for (var stack : client.player.getInventory().getNonEquipmentItems())
			if (stack.getItem() instanceof BlockItem)
				count += stack.getCount();
		if (client.player.getOffhandItem().getItem() instanceof BlockItem)
			count += client.player.getOffhandItem().getCount();
		return count + " Blocks";
	}

	private static String armorWarning(Minecraft client) {
		float lowest = 1f;
		for (var slot : ARMOR_SLOTS) {
			var stack = client.player.getItemBySlot(slot);
			if (stack.isDamageableItem() && stack.getMaxDamage() > 0)
				lowest = Math.min(lowest, 1f - (float) stack.getDamageValue() / stack.getMaxDamage());
		}
		return lowest <= LOW_ARMOR ? "Armor low: " + Math.round(lowest * 100) + "%" : null;
	}

	private static String healthWarning(Minecraft client) {
		return client.player.getHealth() <= LOW_HEALTH ? "Low health" : null;
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
