package de.sakulius.zyrox.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import de.sakulius.zyrox.ZyroxClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class News {
	public enum State {
		LOADING,
		READY,
		EMPTY
	}

	public record Item(String title, String date, List<String> lines) {
	}

	public record Picture(Identifier id, int width, int height) {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final Duration TIMEOUT = Duration.ofSeconds(8);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final long REFRESH_MS = 60_000;
	private static final int HTTP_OK = 200;
	private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
	private static final int MAX_TITLE = 60;

	public static volatile String url = "https://gist.githubusercontent.com/Sakulius/95675b7525b45d0b04551cb5521f61bd/raw/news.json";

	private static volatile State state = State.LOADING;
	private static volatile Item item;
	private static volatile Picture picture;
	private static volatile String pictureUrl = "";
	private static volatile boolean fetching;
	private static volatile long nextFetch;
	private static int pictureCount;

	private News() {
	}

	public static State state() {
		return state;
	}

	public static Item item() {
		return item;
	}

	public static Picture picture() {
		return picture;
	}

	public static void refreshSoon() {
		nextFetch = 0;
	}

	public static void tick(Minecraft client) {
		if (fetching || url.isBlank() || nextFetch != 0 && !(client.gui.screen() instanceof TitleScreen))
			return;
		long now = System.currentTimeMillis();
		if (now < nextFetch)
			return;
		nextFetch = now + REFRESH_MS;
		fetching = true;
		try {
			var request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).build();
			HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
				try {
					if (error == null && response.statusCode() == HTTP_OK)
						read(client, response.body());
					else if (item == null)
						state = State.EMPTY;
				} finally {
					fetching = false;
				}
			});
		} catch (IllegalArgumentException e) {
			fetching = false;
			state = State.EMPTY;
		}
	}

	private static String text(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString().replaceAll("[\\p{Cntrl}§]", "").strip() : "";
	}

	private static void read(Minecraft client, String body) {
		try {
			var json = JsonParser.parseString(body).getAsJsonObject();
			var title = text(json, "title");
			if (title.isEmpty()) {
				item = null;
				state = State.EMPTY;
				return;
			}
			var lines = new ArrayList<String>();
			if (json.has("changelog") && json.get("changelog").isJsonArray()) {
				for (var line : json.getAsJsonArray("changelog"))
					if (line.isJsonPrimitive())
						lines.add(line.getAsString().replaceAll("[\\p{Cntrl}§]", ""));
			} else {
				text(json, "changelog").lines().forEach(lines::add);
			}
			item = new Item(title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title, text(json, "date"), List.copyOf(lines));
			state = State.READY;
			var image = text(json, "image");
			if (!image.equals(pictureUrl)) {
				pictureUrl = image;
				if (image.isEmpty())
					client.execute(() -> replacePicture(client, null));
				else
					loadPicture(client, image);
			}
		} catch (JsonParseException | IllegalStateException e) {
			LOGGER.debug("Could not read the news", e);
			if (item == null)
				state = State.EMPTY;
		}
	}

	private static void loadPicture(Minecraft client, String address) {
		try {
			var request = HttpRequest.newBuilder(URI.create(address)).timeout(TIMEOUT).build();
			HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
				if (error != null || response.statusCode() != HTTP_OK || response.body().length > MAX_IMAGE_BYTES)
					return;
				try {
					var image = NativeImage.read(new ByteArrayInputStream(response.body()));
					client.execute(() -> replacePicture(client, image));
				} catch (IOException e) {
					LOGGER.debug("The news picture is not a PNG", e);
				}
			});
		} catch (IllegalArgumentException e) {
			LOGGER.debug("Bad news picture address", e);
		}
	}

	private static void replacePicture(Minecraft client, NativeImage image) {
		var old = picture;
		if (image == null) {
			picture = null;
		} else {
			var id = Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "generated/news_" + pictureCount++);
			client.getTextureManager().register(id, new DynamicTexture(() -> "zyrox news", image));
			picture = new Picture(id, image.getWidth(), image.getHeight());
		}
		if (old != null)
			client.getTextureManager().release(old.id());
	}
}
