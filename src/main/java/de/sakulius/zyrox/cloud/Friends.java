package de.sakulius.zyrox.cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.sakulius.zyrox.ZyroxClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Friends {
	public record Friend(UUID id, String name, boolean online, String server) {
	}

	public record Request(UUID id, String name) {
	}

	public interface Resolver {
		UUID uuidOf(String name) throws IOException, InterruptedException;
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Duration TIMEOUT = Duration.ofSeconds(8);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private static final int TICKS_BETWEEN_CHECKS = 5;
	private static final long PRESENCE_MS = 120_000;
	private static final long RETRY_MS = 30_000;
	private static final long WATCHED_REFRESH_MS = 5_000;
	private static final long IDLE_REFRESH_MS = 120_000;
	private static final int HTTP_OK = 200;
	private static final int HTTP_NOT_FOUND = 404;
	private static final int HTTP_NO_CONTENT = 204;

	public static boolean appearOnline = true;
	public static boolean shareServer = true;
	public static volatile Resolver resolver = Friends::mojangUuid;

	private static volatile List<Friend> friends = List.of();
	private static volatile List<Request> incoming = List.of();
	private static volatile List<Request> outgoing = List.of();
	private static volatile boolean loaded;
	private static volatile boolean failed;
	private static volatile String message = "";
	private static volatile boolean busy;
	private static volatile String sentPresence;
	private static volatile long nextPresence;
	private static volatile long nextRefresh;
	private static volatile boolean shownOnline;
	private static long ticks;
	private static long watchedUntil;

	private Friends() {
	}

	public static List<Friend> friends() {
		return friends;
	}

	public static List<Request> incoming() {
		return incoming;
	}

	public static List<Request> outgoing() {
		return outgoing;
	}

	public static boolean loaded() {
		return loaded;
	}

	public static boolean failed() {
		return failed;
	}

	public static String message() {
		return message;
	}

	public static boolean available() {
		return !CloudCosmetics.serverUrl.isBlank();
	}

	public static void watch() {
		long now = System.currentTimeMillis();
		if (watchedUntil < now)
			nextRefresh = 0;
		watchedUntil = now + 2_000;
	}

	public static void refreshSoon() {
		nextRefresh = 0;
	}

	public static void tick(Minecraft client) {
		if (++ticks % TICKS_BETWEEN_CHECKS != 0 || !available() || busy)
			return;
		long now = System.currentTimeMillis();
		var server = appearOnline ? currentServer(client) : null;
		if (appearOnline && (!server.equals(sentPresence) || now >= nextPresence)) {
			busy = true;
			var body = new JsonObject();
			body.addProperty("name", client.getUser().getName());
			body.addProperty("server", server);
			CloudCosmetics.submit(() -> {
				try {
					var answer = CloudCosmetics.call(client, "PUT", "/v1/presence", body);
					nextPresence = System.currentTimeMillis() + (answer.status() == HTTP_OK ? PRESENCE_MS : RETRY_MS);
					if (answer.status() == HTTP_OK) {
						sentPresence = server;
						shownOnline = true;
					}
				} catch (Exception e) {
					handle(e);
					nextPresence = System.currentTimeMillis() + RETRY_MS;
				} finally {
					busy = false;
				}
			});
			return;
		}
		if (!appearOnline && shownOnline) {
			busy = true;
			CloudCosmetics.submit(() -> {
				try {
					CloudCosmetics.call(client, "DELETE", "/v1/presence", null);
					shownOnline = false;
					sentPresence = null;
				} catch (Exception e) {
					handle(e);
				} finally {
					busy = false;
				}
			});
			return;
		}
		if (now >= nextRefresh) {
			nextRefresh = now + (watchedUntil > now ? WATCHED_REFRESH_MS : IDLE_REFRESH_MS);
			busy = true;
			CloudCosmetics.submit(() -> {
				try {
					load(client);
				} finally {
					busy = false;
				}
			});
		}
	}

	private static String currentServer(Minecraft client) {
		var server = client.getCurrentServer();
		if (!shareServer || server == null || server.isLan())
			return "";
		var address = server.ip.strip();
		return address.length() <= 100 && address.chars().allMatch(c -> c >= 0x20 && c <= 0x7e) ? address : "";
	}

	private static void handle(Exception e) {
		if (e instanceof InterruptedException)
			Thread.currentThread().interrupt();
		LOGGER.debug("Friends request failed", e);
	}

	static UUID uuid(String compact) {
		return UUID.fromString(compact.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
	}

	static String compact(UUID id) {
		return id.toString().replace("-", "");
	}

	static String text(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
	}

	private static List<Request> requests(JsonObject body, String key) {
		var list = new ArrayList<Request>();
		if (body.has(key) && body.get(key).isJsonArray())
			for (var entry : body.getAsJsonArray(key))
				list.add(new Request(uuid(text(entry.getAsJsonObject(), "uuid")), text(entry.getAsJsonObject(), "name")));
		return List.copyOf(list);
	}

	private static void load(Minecraft client) {
		try {
			var answer = CloudCosmetics.call(client, "GET", "/v1/friends", null);
			if (answer.status() != HTTP_OK) {
				failed = true;
				return;
			}
			var body = answer.body();
			var list = new ArrayList<Friend>();
			if (body.has("friends") && body.get("friends").isJsonArray()) {
				for (var entry : body.getAsJsonArray("friends")) {
					var json = entry.getAsJsonObject();
					list.add(new Friend(uuid(text(json, "uuid")), text(json, "name"), json.has("online") && json.get("online").getAsBoolean(), text(json, "server")));
				}
			}
			list.sort(Comparator.comparing((Friend friend) -> !friend.online()).thenComparing(friend -> friend.name().toLowerCase()));
			friends = List.copyOf(list);
			Roles.accept(body);
			incoming = requests(body, "incoming");
			outgoing = requests(body, "outgoing");
			loaded = true;
			failed = false;
		} catch (Exception e) {
			handle(e);
			failed = true;
		}
	}

	private static void act(Minecraft client, String path, UUID player, String done) {
		var body = new JsonObject();
		body.addProperty("uuid", compact(player));
		CloudCosmetics.submit(() -> {
			try {
				var answer = CloudCosmetics.call(client, "POST", path, body);
				message = answer.status() == HTTP_OK ? done : "That did not work, try again.";
				load(client);
			} catch (Exception e) {
				handle(e);
				message = "The Zyrox server is not reachable.";
			}
		});
	}

	public static void add(Minecraft client, String name) {
		var wanted = name.strip();
		if (!NAME.matcher(wanted).matches()) {
			message = "That is not a Minecraft name.";
			return;
		}
		message = "Looking for " + wanted + "...";
		CloudCosmetics.submit(() -> {
			try {
				var id = resolver.uuidOf(wanted);
				if (id == null) {
					message = "There is no player called " + wanted + ".";
					return;
				}
				if (id.equals(client.getUser().getProfileId())) {
					message = "That is you.";
					return;
				}
				var body = new JsonObject();
				body.addProperty("uuid", compact(id));
				var answer = CloudCosmetics.call(client, "POST", "/v1/friends/request", body);
				if (answer.status() == HTTP_OK)
					message = "friends".equals(text(answer.body(), "status")) ? "You and " + wanted + " are now friends." : "Request sent to " + wanted + ".";
				else
					message = "Could not send the request.";
				load(client);
			} catch (Exception e) {
				handle(e);
				message = "Could not reach the server.";
			}
		});
	}

	public static void accept(Minecraft client, Request request) {
		act(client, "/v1/friends/accept", request.id(), "You and " + request.name() + " are now friends.");
	}

	public static void remove(Minecraft client, UUID player, String name) {
		act(client, "/v1/friends/remove", player, name.isEmpty() ? "Removed." : name + " was removed.");
	}

	public static boolean canJoin(Minecraft client, Friend friend) {
		if (!friend.online() || friend.server().isEmpty() || !ServerAddress.isValidAddress(friend.server()))
			return false;
		var current = client.getCurrentServer();
		return current == null || !current.ip.equalsIgnoreCase(friend.server());
	}

	public static void join(Minecraft client, Friend friend) {
		if (!canJoin(client, friend))
			return;
		Screen parent = new TitleScreen();
		if (client.level != null)
			client.disconnectFromWorld(Component.literal("Joining " + friend.name()));
		ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(friend.server()), new ServerData(friend.name(), friend.server(), ServerData.Type.OTHER), false, null);
	}

	private static UUID mojangUuid(String name) throws IOException, InterruptedException {
		var request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name)).timeout(TIMEOUT).GET().build();
		var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == HTTP_NOT_FOUND || response.statusCode() == HTTP_NO_CONTENT)
			return null;
		if (response.statusCode() != HTTP_OK)
			throw new IOException("Mojang answered " + response.statusCode());
		try {
			return uuid(JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString());
		} catch (JsonParseException | IllegalStateException | NullPointerException e) {
			throw new IOException("Unexpected answer from Mojang", e);
		}
	}
}
