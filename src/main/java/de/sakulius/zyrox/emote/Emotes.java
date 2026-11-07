package de.sakulius.zyrox.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.sakulius.zyrox.ZyroxClient;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Emotes {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final String FOLDER = "emotes";
	private static final float DEGREES = (float) (Math.PI / 180.0);

	private static List<Emote> loaded;
	private static Emote playing;
	private static long startNanos;
	private static CameraType savedCamera;

	private Emotes() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("zyrox").resolve(FOLDER);
	}

	public static void reload() {
		loaded = null;
	}

	public static List<Emote> all() {
		if (loaded != null)
			return loaded;
		var found = new LinkedHashMap<String, Emote>();
		var manager = Minecraft.getInstance().getResourceManager();
		for (var entry : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
			if (!entry.getKey().getNamespace().equals(ZyroxClient.MOD_ID))
				continue;
			try (var reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
				read(JsonParser.parseReader(reader).getAsJsonObject(), found);
			} catch (IOException | JsonParseException | IllegalStateException e) {
				LOGGER.warn("Could not read emote {}", entry.getKey(), e);
			}
		}
		var folder = folder();
		if (Files.isDirectory(folder)) {
			try (var files = Files.list(folder)) {
				for (var file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
					try {
						read(JsonParser.parseString(Files.readString(file)).getAsJsonObject(), found);
					} catch (IOException | JsonParseException | IllegalStateException e) {
						LOGGER.warn("Could not read emote file {}", file, e);
					}
				}
			} catch (IOException e) {
				LOGGER.warn("Could not list {}", folder, e);
			}
		}
		loaded = List.copyOf(new ArrayList<>(found.values()));
		return loaded;
	}

	private static void read(JsonObject json, LinkedHashMap<String, Emote> found) {
		if (!json.has("animations") || !json.get("animations").isJsonObject())
			return;
		for (var entry : json.getAsJsonObject("animations").entrySet()) {
			if (!entry.getValue().isJsonObject())
				continue;
			var emote = Emote.parse(entry.getKey(), entry.getValue().getAsJsonObject());
			found.put(emote.id(), emote);
		}
	}

	public static Emote playing() {
		return playing;
	}

	public static void play(Minecraft client, Emote emote) {
		if (client.player == null)
			return;
		playing = emote;
		startNanos = System.nanoTime();
		if (savedCamera == null) {
			savedCamera = client.options.getCameraType();
			if (savedCamera.isFirstPerson())
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}
	}

	public static void stop(Minecraft client) {
		playing = null;
		if (savedCamera != null) {
			client.options.setCameraType(savedCamera);
			savedCamera = null;
		}
	}

	private static float time() {
		return (System.nanoTime() - startNanos) / 1_000_000_000f;
	}

	public static void tick(Minecraft client) {
		if (playing == null)
			return;
		var options = client.options;
		boolean moving = options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown()
			|| options.keyJump.isDown() || options.keyAttack.isDown() || options.keyUse.isDown();
		if (client.player == null || moving || playing.loop() == Emote.Loop.ONCE && time() > playing.length())
			stop(client);
	}

	public static void apply(PlayerModel model, AvatarRenderState state) {
		var emote = playing;
		var client = Minecraft.getInstance();
		if (emote == null || client.player == null || state.id != client.player.getId())
			return;
		float time = time();
		if (emote.loop() == Emote.Loop.LOOP)
			time %= emote.length();
		else
			time = Math.min(time, emote.length());
		pose(model.head, emote, "head", time);
		pose(model.body, emote, "body", time);
		pose(model.rightArm, emote, "rightarm", time);
		pose(model.leftArm, emote, "leftarm", time);
		pose(model.rightLeg, emote, "rightleg", time);
		pose(model.leftLeg, emote, "leftleg", time);
	}

	private static void pose(ModelPart part, Emote emote, String bone, float time) {
		var track = emote.bones().get(bone);
		if (track == null)
			return;
		var rotation = Emote.sample(track.rotation(), time);
		var position = Emote.sample(track.position(), time);
		if (rotation == null && position == null)
			return;
		part.resetPose();
		if (rotation != null) {
			part.xRot = -rotation.x * DEGREES;
			part.yRot = -rotation.y * DEGREES;
			part.zRot = rotation.z * DEGREES;
		}
		if (position != null) {
			part.x += position.x;
			part.y -= position.y;
			part.z += position.z;
		}
	}
}
