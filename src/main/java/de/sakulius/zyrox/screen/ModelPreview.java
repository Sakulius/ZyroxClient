package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.platform.NativeImage;
import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.module.WearableModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelPreview {
	public static final int SIZE = 128;
	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final int MARGIN = 3;
	private static final int CORNERS = 4;
	private static final int CORNER_VALUES = 5;
	private static final int SOLID_ALPHA = 128;
	private static final float SIN_YAW = (float) Math.sin(Math.toRadians(-35));
	private static final float COS_YAW = (float) Math.cos(Math.toRadians(-35));
	private static final float SIN_PITCH = (float) Math.sin(Math.toRadians(25));
	private static final float COS_PITCH = (float) Math.cos(Math.toRadians(25));
	private static final float[] LIGHT = { 0.29f, 0.81f, 0.51f };
	private static final float AMBIENT = 0.6f;
	private static final Map<String, Optional<Identifier>> LOADED = new HashMap<>();

	private ModelPreview() {
	}

	public static Identifier of(String id) {
		return LOADED.computeIfAbsent(id, ModelPreview::load).orElse(null);
	}

	private static Optional<Identifier> load(String id) {
		var model = WearableModel.of(id);
		if (model.texture() == null)
			return Optional.empty();
		var client = Minecraft.getInstance();
		try (var stream = client.getResourceManager().open(model.texture()); var skin = NativeImage.read(stream)) {
			var location = Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "generated/preview_" + id);
			client.getTextureManager().register(location, new DynamicTexture(() -> "zyrox preview " + id, render(model, skin)));
			return Optional.of(location);
		} catch (IOException e) {
			LOGGER.warn("Could not draw a preview of {}", id, e);
			return Optional.empty();
		}
	}

	private static float[] turn(float x, float y, float z) {
		float turnedX = x * COS_YAW + z * SIN_YAW;
		float turnedZ = -x * SIN_YAW + z * COS_YAW;
		return new float[] { turnedX, y * COS_PITCH - turnedZ * SIN_PITCH, y * SIN_PITCH + turnedZ * COS_PITCH };
	}

	private static NativeImage render(WearableModel model, NativeImage skin) {
		var image = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++)
			for (int x = 0; x < SIZE; x++)
				image.setPixel(x, y, 0);

		var faces = new ArrayList<float[][]>();
		float left = Float.MAX_VALUE;
		float right = -Float.MAX_VALUE;
		float top = Float.MAX_VALUE;
		float bottom = -Float.MAX_VALUE;
		for (var face : model.faces()) {
			var corners = new float[CORNERS][];
			for (int corner = 0; corner < CORNERS; corner++) {
				int at = corner * CORNER_VALUES;
				var seen = turn(face[at], face[at + 1], face[at + 2]);
				corners[corner] = new float[] { seen[0], -seen[1], seen[2], face[at + 3], face[at + 4] };
				left = Math.min(left, seen[0]);
				right = Math.max(right, seen[0]);
				top = Math.min(top, -seen[1]);
				bottom = Math.max(bottom, -seen[1]);
			}
			faces.add(corners);
		}
		float scale = (SIZE - MARGIN * 2) / Math.max(Math.max(right - left, bottom - top), 0.001f);
		float offsetX = SIZE / 2f - (left + right) / 2f * scale;
		float offsetY = SIZE / 2f - (top + bottom) / 2f * scale;

		var depth = new float[SIZE * SIZE];
		Arrays.fill(depth, -Float.MAX_VALUE);
		int index = 0;
		for (var face : model.faces()) {
			var corners = faces.get(index++);
			for (var corner : corners) {
				corner[0] = corner[0] * scale + offsetX;
				corner[1] = corner[1] * scale + offsetY;
			}
			int normal = CORNERS * CORNER_VALUES;
			var seenNormal = turn(face[normal], face[normal + 1], face[normal + 2]);
			float facing = Math.max(0f, seenNormal[0] * LIGHT[0] + seenNormal[1] * LIGHT[1] + seenNormal[2] * LIGHT[2]);
			float shade = AMBIENT + (1f - AMBIENT) * facing;
			triangle(image, skin, depth, corners[0], corners[1], corners[2], shade);
			triangle(image, skin, depth, corners[0], corners[2], corners[3], shade);
		}
		return image;
	}

	private static void triangle(NativeImage image, NativeImage skin, float[] depth, float[] a, float[] b, float[] c, float shade) {
		float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
		if (Math.abs(area) < 0.0001f)
			return;
		int minX = Math.max(0, (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
		int maxX = Math.min(SIZE - 1, (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
		int minY = Math.max(0, (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
		int maxY = Math.min(SIZE - 1, (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				float px = x + 0.5f;
				float py = y + 0.5f;
				float weightB = ((px - a[0]) * (c[1] - a[1]) - (py - a[1]) * (c[0] - a[0])) / area;
				float weightC = ((b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0])) / area;
				float weightA = 1f - weightB - weightC;
				if (weightA < 0f || weightB < 0f || weightC < 0f)
					continue;
				float z = weightA * a[2] + weightB * b[2] + weightC * c[2];
				if (z <= depth[y * SIZE + x])
					continue;
				float u = weightA * a[3] + weightB * b[3] + weightC * c[3];
				float v = weightA * a[4] + weightB * b[4] + weightC * c[4];
				int color = skin.getPixel(Math.clamp((int) (u * skin.getWidth()), 0, skin.getWidth() - 1), Math.clamp((int) (v * skin.getHeight()), 0, skin.getHeight() - 1));
				if (color >>> 24 < SOLID_ALPHA)
					continue;
				depth[y * SIZE + x] = z;
				image.setPixel(x, y, 0xFF000000 | channel(color, 16, shade) | channel(color, 8, shade) | channel(color, 0, shade));
			}
		}
	}

	private static int channel(int color, int shift, float shade) {
		return (int) ((color >> shift & 0xFF) * shade) << shift;
	}
}
