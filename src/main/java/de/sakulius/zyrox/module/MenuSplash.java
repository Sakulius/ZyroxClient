package de.sakulius.zyrox.module;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class MenuSplash {
	private static final int MAX_LENGTH = 48;
	private static final int COLOR = 0xFFFF00;
	private static final int HTTP_OK = 200;
	private static final long REFRESH_MS = 60_000;
	private static final Duration TIMEOUT = Duration.ofSeconds(8);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	public static volatile String url = "https://gist.githubusercontent.com/Sakulius/95675b7525b45d0b04551cb5521f61bd/raw/splash.txt";

	private static volatile String text = "";
	private static volatile boolean fetching;
	private static volatile long nextFetch;
	private static String drawnText;
	private static SplashRenderer drawn;

	private MenuSplash() {
	}

	public static String text() {
		return text;
	}

	public static void refreshSoon() {
		nextFetch = 0;
	}

	public static void tick(Minecraft client) {
		if (fetching || url.isBlank() || nextFetch != 0 && !(client.screen instanceof TitleScreen))
			return;
		long now = System.currentTimeMillis();
		if (now < nextFetch)
			return;
		nextFetch = now + REFRESH_MS;
		fetching = true;
		try {
			var request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).build();
			HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
				if (error == null && response.statusCode() == HTTP_OK)
					text = clean(response.body());
				fetching = false;
			});
		} catch (IllegalArgumentException e) {
			fetching = false;
		}
	}

	private static String clean(String body) {
		var line = body.lines().filter(candidate -> !candidate.isBlank()).findFirst().orElse("");
		line = line.replaceAll("[\\p{Cntrl}§]", "").strip();
		return line.length() > MAX_LENGTH ? line.substring(0, MAX_LENGTH) : line;
	}

	public static SplashRenderer custom() {
		var current = text;
		if (current.isEmpty())
			return null;
		if (!current.equals(drawnText)) {
			drawnText = current;
			drawn = new SplashRenderer(Component.literal(current).withColor(COLOR));
		}
		return drawn;
	}
}
