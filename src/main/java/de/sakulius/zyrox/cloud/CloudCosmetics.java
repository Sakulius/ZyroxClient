package de.sakulius.zyrox.cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.module.Cosmetics;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Signature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudCosmetics {
	public enum Status {
		NOT_SET_UP,
		WAITING,
		SYNCING,
		SYNCED,
		FAILED
	}

	public record Proof(String publicKey, long expiresAt, String keySignature, String signature) {
	}

	public interface Prover {
		Proof prove(String text) throws Exception;
	}

	private record Loadout(Map<String, String> items, long fetchedAt) {
	}

	public record Answer(int status, JsonObject body) {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final Duration TIMEOUT = Duration.ofSeconds(8);
	private static final int TICKS_BETWEEN_CHECKS = 5;
	private static final int MAX_BATCH = 50;
	private static final int MAX_REMEMBERED = 500;
	private static final long REFRESH_MS = 5_000;
	private static final long IDLE_REFRESH_MS = 20_000;
	private static final long TAB_REFRESH_MS = 300_000;
	private static final int ACCOUNT_UUID_VERSION = 4;
	private static final long RETRY_MS = 60_000;
	private static final long TOO_FAST_RETRY_MS = 3_000;
	private static final int CERTIFICATE_TIMEOUT_SECONDS = 20;
	private static final int HTTP_OK = 200;
	private static final int HTTP_UNAUTHORIZED = 401;
	private static final int HTTP_TOO_MANY = 429;

	public static volatile String serverUrl = "https://zyrox-cosmetics.sakuliusde.workers.dev";
	public static volatile Prover prover;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
		var thread = new Thread(task, "Zyrox-Cloud");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<UUID, Loadout> LOADOUTS = new ConcurrentHashMap<>();

	private static volatile Status status = Status.WAITING;
	private static volatile boolean uploading;
	private static volatile boolean fetching;
	private static volatile String uploaded;
	private static volatile long nextUpload;
	private static long ticks;

	private CloudCosmetics() {
	}

	public static Status status() {
		return serverUrl.isBlank() ? Status.NOT_SET_UP : status;
	}

	public static String item(UUID player, String slot) {
		var loadout = LOADOUTS.get(player);
		return loadout == null ? null : loadout.items().get(slot);
	}

	public static boolean usesZyrox(UUID player) {
		var loadout = LOADOUTS.get(player);
		return loadout != null && !loadout.items().isEmpty();
	}

	public static void tick(Minecraft client) {
		if (++ticks % TICKS_BETWEEN_CHECKS != 0 || serverUrl.isBlank() || client.player == null || client.level == null)
			return;
		long now = System.currentTimeMillis();
		var own = Cosmetics.ownItems();
		if (!own.toString().equals(uploaded) && !uploading && now >= nextUpload) {
			uploading = true;
			status = Status.SYNCING;
			var player = client.getUser().getProfileId();
			WORKER.execute(() -> upload(client, player, own));
		}

		var wanted = new ArrayList<UUID>();
		for (var player : client.level.players()) {
			var id = player.getUUID();
			if (player == client.player || wanted.size() >= MAX_BATCH)
				continue;
			var known = LOADOUTS.get(id);
			if (known == null || now - known.fetchedAt() >= (known.items().isEmpty() ? IDLE_REFRESH_MS : REFRESH_MS))
				wanted.add(id);
		}
		var connection = client.getConnection();
		if (connection != null) {
			for (var info : connection.getOnlinePlayers()) {
				var id = info.getProfile().id();
				if (wanted.size() >= MAX_BATCH)
					break;
				if (id.version() != ACCOUNT_UUID_VERSION || id.equals(client.player.getUUID()) || wanted.contains(id))
					continue;
				var known = LOADOUTS.get(id);
				if (known == null || now - known.fetchedAt() >= TAB_REFRESH_MS)
					wanted.add(id);
			}
		}
		if (!wanted.isEmpty() && !fetching)
			request(wanted);
		if (LOADOUTS.size() > MAX_REMEMBERED)
			LOADOUTS.values().removeIf(loadout -> now - loadout.fetchedAt() > TAB_REFRESH_MS);
	}

	public static void submit(Runnable task) {
		WORKER.execute(task);
	}

	public static Answer call(Minecraft client, String method, String path, JsonObject body) throws Exception {
		var player = client.getUser().getProfileId();
		var token = readToken(player);
		if (token == null)
			token = signIn(client, player);
		var answer = send(method, path, body, token);
		if (answer.status() == HTTP_UNAUTHORIZED) {
			token = signIn(client, player);
			answer = send(method, path, body, token);
		}
		return answer;
	}

	public static void request(List<UUID> players) {
		long now = System.currentTimeMillis();
		for (var id : players)
			LOADOUTS.compute(id, (key, old) -> new Loadout(old == null ? Map.of() : old.items(), now));
		fetching = true;
		WORKER.execute(() -> fetch(players));
	}

	private static void fetch(List<UUID> players) {
		var ids = new ArrayList<String>();
		for (var id : players)
			ids.add(compact(id));
		try {
			var answer = send("GET", "/v1/loadouts?uuids=" + String.join(",", ids), null, null);
			if (answer.status() != HTTP_OK)
				return;
			var loadouts = answer.body().getAsJsonObject("loadouts");
			var roles = answer.body().has("roles") && answer.body().get("roles").isJsonObject() ? answer.body().getAsJsonObject("roles") : null;
			long now = System.currentTimeMillis();
			for (var id : players) {
				var items = new ConcurrentHashMap<String, String>();
				if (loadouts != null && loadouts.has(compact(id))) {
					for (var entry : loadouts.getAsJsonObject(compact(id)).entrySet())
						if (entry.getValue().isJsonPrimitive())
							items.put(entry.getKey(), entry.getValue().getAsString());
				}
				LOADOUTS.put(id, new Loadout(items, now));
				Roles.put(id, roles != null && roles.has(compact(id)) ? Roles.Role.of(roles.get(compact(id)).getAsString()) : null);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.debug("Could not load cosmetics", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			fetching = false;
		}
	}

	private static void upload(Minecraft client, UUID player, Map<String, String> own) {
		try {
			var body = new JsonObject();
			var items = new JsonObject();
			own.forEach(items::addProperty);
			body.add("items", items);

			var token = readToken(player);
			if (token == null)
				token = signIn(client, player);
			var answer = send("PUT", "/v1/loadout", body, token);
			if (answer.status() == HTTP_UNAUTHORIZED) {
				token = signIn(client, player);
				answer = send("PUT", "/v1/loadout", body, token);
			}
			if (answer.status() == HTTP_OK) {
				uploaded = own.toString();
				status = Status.SYNCED;
			} else {
				nextUpload = System.currentTimeMillis() + (answer.status() == HTTP_TOO_MANY ? TOO_FAST_RETRY_MS : RETRY_MS);
				status = Status.FAILED;
			}
		} catch (Exception e) {
			if (e instanceof InterruptedException)
				Thread.currentThread().interrupt();
			LOGGER.info("Could not sync cosmetics: {}", e.toString());
			nextUpload = System.currentTimeMillis() + RETRY_MS;
			status = Status.FAILED;
		} finally {
			uploading = false;
		}
	}

	private static String signIn(Minecraft client, UUID player) throws Exception {
		var start = send("POST", "/v1/auth/start", new JsonObject(), null);
		if (start.status() != HTTP_OK)
			throw new IOException("Sign in could not start: " + start.status());
		var challenge = start.body().get("challenge").getAsString();
		var proof = (prover != null ? prover : defaultProver(client)).prove("zyrox-auth:" + challenge);

		var finish = new JsonObject();
		finish.addProperty("challenge", challenge);
		finish.addProperty("uuid", compact(player));
		finish.addProperty("publicKey", proof.publicKey());
		finish.addProperty("expiresAt", proof.expiresAt());
		finish.addProperty("keySignature", proof.keySignature());
		finish.addProperty("signature", proof.signature());
		var answer = send("POST", "/v1/auth/finish", finish, null);
		if (answer.status() != HTTP_OK)
			throw new IOException("Sign in was refused: " + answer.status() + " " + answer.body());
		var token = answer.body().get("token").getAsString();
		var file = tokenFile(player);
		Files.createDirectories(file.getParent());
		Files.writeString(file, token);
		return token;
	}

	private static Prover defaultProver(Minecraft client) {
		return text -> {
			var pair = client.getProfileKeyPairManager().prepareKeyPair().get(CERTIFICATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.orElseThrow(() -> new IOException("This account has no Mojang certificate"));
			var data = pair.publicKey().data();
			var signer = Signature.getInstance("SHA256withRSA");
			signer.initSign(pair.privateKey());
			signer.update(text.getBytes(StandardCharsets.UTF_8));
			var base64 = Base64.getEncoder();
			return new Proof(base64.encodeToString(data.key().getEncoded()), data.expiresAt().toEpochMilli(),
				base64.encodeToString(data.keySignature()), base64.encodeToString(signer.sign()));
		};
	}

	private static String readToken(UUID player) throws IOException {
		var file = tokenFile(player);
		return Files.exists(file) ? Files.readString(file).trim() : null;
	}

	private static Path tokenFile(UUID player) {
		return FabricLoader.getInstance().getConfigDir().resolve("zyrox/cloud").resolve(compact(player) + ".token");
	}

	private static String compact(UUID id) {
		return id.toString().replace("-", "");
	}

	private static Answer send(String method, String path, JsonObject body, String token) throws IOException, InterruptedException {
		var request = HttpRequest.newBuilder(URI.create(serverUrl + path)).timeout(TIMEOUT).header("Content-Type", "application/json");
		if (token != null)
			request.header("Authorization", "Bearer " + token);
		request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
		var response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
		try {
			var parsed = JsonParser.parseString(response.body());
			return new Answer(response.statusCode(), parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject());
		} catch (JsonParseException e) {
			return new Answer(response.statusCode(), new JsonObject());
		}
	}
}
