package de.sakulius.zyrox.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Waypoints {
	public static final class Waypoint {
		public String name;
		public int x;
		public int y;
		public int z;
		public String dimension;
		public int color;
		public boolean visible = true;
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int[] COLORS = { 0xFF4F9BFF, 0xFFFF5D73, 0xFF4ED98A, 0xFFFFC94A, 0xFFB57BFF, 0xFF4FD8E8 };
	private static final int EDGE = 14;
	private static final int DOT = 6;
	private static final int LABEL_BACK = 0xB8101116;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFB9BEC8;
	private static final float BLOCK_CENTER = 0.5f;
	private static final float ABOVE_FEET = 1f;

	private static Map<String, List<Waypoint>> worlds;

	private Waypoints() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("zyrox-waypoints.json");
	}

	private static Map<String, List<Waypoint>> worlds() {
		if (worlds != null)
			return worlds;
		worlds = new HashMap<>();
		var path = path();
		if (Files.exists(path)) {
			try {
				Map<String, List<Waypoint>> saved = GSON.fromJson(Files.readString(path), new TypeToken<Map<String, List<Waypoint>>>() {
				}.getType());
				if (saved != null)
					worlds.putAll(saved);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Could not read {}", path, e);
			}
		}
		return worlds;
	}

	public static void save() {
		var path = path();
		var temp = path.resolveSibling("zyrox-waypoints.json.tmp");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(temp, GSON.toJson(worlds()));
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn("Could not save {}", path, e);
		}
	}

	private static String worldKey(Minecraft client) {
		if (client.getCurrentServer() != null)
			return "server:" + client.getCurrentServer().ip;
		var server = client.getSingleplayerServer();
		return server == null ? null : "world:" + server.getWorldData().getLevelName();
	}

	public static List<Waypoint> here(Minecraft client) {
		var key = worldKey(client);
		return key == null ? List.of() : worlds().computeIfAbsent(key, ignored -> new ArrayList<>());
	}

	public static boolean canAdd(Minecraft client) {
		return client.player != null && client.level != null && worldKey(client) != null;
	}

	public static String nextName(Minecraft client) {
		return "Waypoint " + (here(client).size() + 1);
	}

	public static void add(Minecraft client, String name) {
		if (!canAdd(client))
			return;
		var list = here(client);
		var waypoint = new Waypoint();
		waypoint.name = name;
		waypoint.x = client.player.getBlockX();
		waypoint.y = client.player.getBlockY();
		waypoint.z = client.player.getBlockZ();
		waypoint.dimension = client.level.dimension().identifier().toString();
		waypoint.color = COLORS[list.size() % COLORS.length];
		list.add(waypoint);
		save();
	}

	public static void remove(Minecraft client, Waypoint waypoint) {
		here(client).remove(waypoint);
		save();
	}

	public static int distance(Minecraft client, Waypoint waypoint) {
		if (client.player == null)
			return 0;
		return (int) Math.round(Math.sqrt(client.player.distanceToSqr(waypoint.x + BLOCK_CENTER, waypoint.y + BLOCK_CENTER, waypoint.z + BLOCK_CENTER)));
	}

	public static void render(GuiGraphicsExtractor graphics, Minecraft client) {
		if (!Modules.WAYPOINTS.enabled || client.player == null || client.level == null)
			return;
		var dimension = client.level.dimension().identifier().toString();
		for (var waypoint : here(client))
			if (waypoint.visible && dimension.equals(waypoint.dimension))
				draw(graphics, client, waypoint);
	}

	public static float[] project(Minecraft client, double worldX, double worldY, double worldZ, int width, int height) {
		var camera = client.gameRenderer.mainCamera();
		var from = camera.position();
		var view = new Vector3f((float) (worldX - from.x), (float) (worldY - from.y), (float) (worldZ - from.z));
		view.rotate(new Quaternionf(camera.rotation()).conjugate());
		float focal = height / 2f / (float) Math.tan(Math.toRadians(camera.getFov()) / 2f);
		float centerX = width / 2f;
		float centerY = height / 2f;
		float offsetX;
		float offsetY;
		boolean behind = view.z >= -0.01f;
		if (behind) {
			float length = Math.max(0.001f, (float) Math.hypot(view.x, view.y));
			offsetX = view.x / length * width;
			offsetY = -view.y / length * width;
		} else {
			offsetX = view.x / -view.z * focal;
			offsetY = -view.y / -view.z * focal;
		}
		float limitX = centerX - EDGE;
		float limitY = centerY - EDGE;
		float shrink = Math.min(1f, Math.min(limitX / Math.max(0.001f, Math.abs(offsetX)), limitY / Math.max(0.001f, Math.abs(offsetY))));
		return new float[] { centerX + offsetX * shrink, centerY + offsetY * shrink };
	}

	private static void draw(GuiGraphicsExtractor graphics, Minecraft client, Waypoint waypoint) {
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		var at = project(client, waypoint.x + BLOCK_CENTER, waypoint.y + ABOVE_FEET, waypoint.z + BLOCK_CENTER, width, height);
		var font = client.font;
		var meters = distance(client, waypoint) + " m";
		int nameWidth = Type.SEMIBOLD.width(font, waypoint.name);
		int metersWidth = Type.REGULAR.width(font, meters);
		int boxWidth = Math.max(nameWidth, metersWidth) + 10;
		int boxHeight = 23;
		int left = Math.clamp(Math.round(at[0]) - boxWidth / 2, 2, Math.max(2, width - boxWidth - 2));
		int top = Math.clamp(Math.round(at[1]) - boxHeight - DOT, 2, Math.max(2, height - boxHeight - DOT - 2));
		Draw.roundRect(graphics, left, top, boxWidth, boxHeight, 4f, LABEL_BACK);
		Type.SEMIBOLD.centered(graphics, font, waypoint.name, left + boxWidth / 2, top + 3, TEXT);
		Type.REGULAR.centered(graphics, font, meters, left + boxWidth / 2, top + 13, MUTED);
		Draw.roundRect(graphics, Math.round(at[0]) - DOT / 2f, top + boxHeight + 1, DOT, DOT, DOT / 2f, waypoint.color);
	}
}
