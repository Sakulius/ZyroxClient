package de.sakulius.zyrox.module;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.sakulius.zyrox.ZyroxClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WearableModel {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final float UNITS_PER_BLOCK = 16f;
	private static final int CORNERS = 4;
	private static final int CORNER_VALUES = 5;
	private static final int COLOR = 0xFFFFFFFF;
	private static final Map<String, WearableModel> LOADED = new ConcurrentHashMap<>();
	private static final WearableModel EMPTY = new WearableModel(null, new float[3], new float[3], new float[] { 1f, 1f, 1f }, List.of());

	private final Identifier texture;
	private final float[] translation;
	private final float[] rotation;
	private final float[] scale;
	private final List<float[]> faces;

	private WearableModel(Identifier texture, float[] translation, float[] rotation, float[] scale, List<float[]> faces) {
		this.texture = texture;
		this.translation = translation;
		this.rotation = rotation;
		this.scale = scale;
		this.faces = faces;
	}

	public static WearableModel of(String id) {
		return LOADED.computeIfAbsent(id, WearableModel::load);
	}

	private static WearableModel load(String id) {
		var location = Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "cosmetic/" + id + ".json");
		try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
			var root = JsonParser.parseReader(reader).getAsJsonObject();
			var faces = new ArrayList<float[]>();
			for (var face : root.getAsJsonArray("quads"))
				faces.add(numbers(face.getAsJsonArray()));
			return new WearableModel(Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "textures/cosmetic/" + id + ".png"),
				numbers(root.getAsJsonArray("translation")), root.has("rotation") ? numbers(root.getAsJsonArray("rotation")) : new float[3], numbers(root.getAsJsonArray("scale")), faces);
		} catch (IOException | JsonParseException | IllegalStateException | NullPointerException e) {
			LOGGER.warn("Could not load cosmetic {}", id, e);
			return EMPTY;
		}
	}

	private static float[] numbers(com.google.gson.JsonArray array) {
		var values = new float[array.size()];
		for (int i = 0; i < values.length; i++)
			values[i] = array.get(i).getAsFloat();
		return values;
	}

	public Identifier texture() {
		return texture;
	}

	public List<float[]> faces() {
		return faces;
	}

	public void place(PoseStack pose) {
		pose.translate(translation[0] / UNITS_PER_BLOCK, translation[1] / UNITS_PER_BLOCK, translation[2] / UNITS_PER_BLOCK);
		pose.mulPose(new Matrix4f().rotationXYZ((float) Math.toRadians(rotation[0]), (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[2])));
		pose.scale(scale[0], scale[1], scale[2]);
		pose.translate(-0.5f, -0.5f, -0.5f);
	}

	public void draw(PoseStack.Pose pose, VertexConsumer consumer, int light) {
		for (var face : faces) {
			int normal = CORNERS * CORNER_VALUES;
			for (int corner = 0; corner < CORNERS; corner++) {
				int at = corner * CORNER_VALUES;
				consumer.addVertex(pose, face[at], face[at + 1], face[at + 2])
					.setColor(COLOR)
					.setUv(face[at + 3], face[at + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, face[normal], face[normal + 1], face[normal + 2]);
			}
		}
	}
}
