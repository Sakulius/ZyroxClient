package de.sakulius.zyrox.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.sun.net.httpserver.HttpServer;
import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.ZyroxConfig;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.cloud.CloudCosmetics;
import de.sakulius.zyrox.module.Cosmetics;
import de.sakulius.zyrox.module.MenuSplash;
import de.sakulius.zyrox.module.HudModule;
import de.sakulius.zyrox.module.Module;
import de.sakulius.zyrox.module.Modules;
import de.sakulius.zyrox.music.Lyrics;
import de.sakulius.zyrox.music.MusicManager;
import de.sakulius.zyrox.music.WindowsMediaSession;
import de.sakulius.zyrox.screen.ZyroxScreen;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;

public class ZyroxClientTest implements FabricClientGameTest {
	private static final int ANIMATION_TICKS = 80;
	private static final int NEARBY_ENTITY = 987_654;
	private static final String DEV_SERVER = "http://127.0.0.1:8799";

	private ClientGameTestContext context;
	private double scale;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		CloudCosmetics.serverUrl = "";
		for (var module : Modules.ALL)
			module.enabled = module.category == Module.Category.UTILITY || !module.id.equals("server") && !module.id.equals("memory");
		Modules.FULLBRIGHT.enabled = false;
		Modules.AUTO_SPRINT.enabled = false;
		ZyroxTheme.index = 1;
		ZyroxTheme.chromaHud = false;
		ZyroxTheme.cinematic = true;
		Cosmetics.hatId = Cosmetics.NOTHING;
		Cosmetics.aura = 0;
		var coordinates = (HudModule) find("coordinates");
		coordinates.x = 4;
		coordinates.y = 21;
		var fps = find("fps");
		var input = context.getInput();

		testMenuSplash(context);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			var connection = singleplayer.getConnection();
			var server = singleplayer.getServer();
			connection.waitForChunksRender();
			testMusic(context);
			testCloud(context);
			testWearables(context);
			scale = context.computeOnClient(client -> (double) client.getWindow().getGuiScale());
			server.runCommand("effect give @a speed 300 1");
			server.runCommand("item replace entity @a armor.chest with iron_chestplate");
			server.runCommand("item replace entity @a weapon.mainhand with diamond_sword");
			input.holdKey(options -> options.keyUp);
			context.waitTicks(10);
			context.takeScreenshot("zyrox-hud");
			input.releaseKey(options -> options.keyUp);

			float normalFov = context.computeOnClient(client -> client.gameRenderer.mainCamera().getFov());
			input.holdKey(ZyroxClient.zoomKey);
			context.waitTicks(15);
			float zoomedFov = context.computeOnClient(client -> client.gameRenderer.mainCamera().getFov());
			check(zoomedFov < normalFov * 0.5f, "zoom narrows the view, " + normalFov + " to " + zoomedFov);
			input.releaseKey(ZyroxClient.zoomKey);
			context.waitTicks(15);

			int[] middle = context.computeOnClient(client -> new int[] { client.getWindow().getGuiScaledWidth() * 2 / 3, client.getWindow().getGuiScaledHeight() / 2 });
			input.setCursorPos(middle[0] * scale, middle[1] * scale);
			input.pressKey(InputConstants.KEY_RSHIFT);
			context.waitTicks(6);
			check(screen() != null, "menu key opens the menu");
			check(context.computeOnClient(client -> client.options.getCameraType()) == CameraType.THIRD_PERSON_FRONT, "the camera leaves first person");
			input.setCursorPos(middle[0] * scale, middle[1] * scale);
			context.waitTicks(ANIMATION_TICKS);
			context.takeScreenshot("zyrox-carousel");
			check(screen().selectedId().equals("cosmetics"), "cosmetics starts in the middle");

			input.scroll(-1);
			context.waitTicks(2);
			context.takeScreenshot("zyrox-scrolling");
			context.waitTicks(ANIMATION_TICKS);
			check(screen().selectedId().equals("music"), "scrolling down selects the next card, now " + screen().selectedId());
			input.scroll(1);
			input.scroll(1);
			context.waitTicks(ANIMATION_TICKS);
			check(screen().selectedId().equals("hud"), "scrolling up selects earlier cards, now " + screen().selectedId());

			clickOn(ZyroxScreen::centerCard);
			check(screen().isPanelOpen(), "clicking the middle card opens its menu");
			context.waitTicks(ANIMATION_TICKS);
			context.takeScreenshot("zyrox-hud-panel");
			clickOn(screen -> screen.rowCenter("fps"));
			check(!fps.enabled, "clicking a row turns the module off");

			clickOn(screen -> screen.rowCenter("edit"));
			check(screen().isEditing(), "the button opens edit mode");
			context.waitTicks(ANIMATION_TICKS);
			check(context.computeOnClient(client -> client.options.getCameraType()) == CameraType.FIRST_PERSON, "edit mode shows the normal view");
			int startX = context.computeOnClient(client -> coordinates.left(client) + 5);
			int startY = context.computeOnClient(client -> coordinates.top(client) + 5);
			input.setCursorPos(startX * scale, startY * scale);
			context.waitTick();
			input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTick();
			input.moveCursor(100 * scale, 60 * scale);
			context.waitTick();
			context.takeScreenshot("zyrox-edit");
			input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTick();
			check(Math.abs(coordinates.x - 104) <= 5 && Math.abs(coordinates.y - 81) <= 5, "dragging moves the element, now at " + coordinates.x + ", " + coordinates.y);
			int movedX = coordinates.x;

			var keystrokes = (HudModule) find("keystrokes");
			keystrokes.setScale(1f);
			Cosmetics.hatId = Cosmetics.NOTHING;
			int fullWidth = context.computeOnClient(keystrokes::width);
			int[] grab = context.computeOnClient(client -> new int[] { keystrokes.left(client) + keystrokes.width(client) - 1, keystrokes.top(client) + keystrokes.height(client) - 1 });
			input.setCursorPos(grab[0] * scale, grab[1] * scale);
			context.waitTick();
			input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTick();
			input.moveCursor(-fullWidth * 0.4 * scale, 0);
			context.waitTick();
			input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTick();
			check(keystrokes.scale < 0.95f && context.computeOnClient(keystrokes::width) < fullWidth, "dragging the corner shrinks the element, now " + keystrokes.scale);
			float dragged = keystrokes.scale;
			int[] corner = context.computeOnClient(client -> new int[] { keystrokes.left(client) + 3, keystrokes.top(client) + 3 });
			input.setCursorPos(corner[0] * scale, corner[1] * scale);
			context.waitTick();
			input.scroll(1);
			context.waitTick();
			check(keystrokes.scale > dragged, "scrolling resizes the element, now " + keystrokes.scale);
			keystrokes.setScale(0.5f);
			context.waitTicks(2);
			context.takeScreenshot("zyrox-edit-small");
			input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTick();
			check(keystrokes.scale == 1f, "right click resets the size, now " + keystrokes.scale);
			keystrokes.setScale(0.5f);
			clickOn(ZyroxScreen::doneCenter);
			check(!screen().isEditing(), "done leaves edit mode");
			context.waitTicks(ANIMATION_TICKS);

			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
			check(!screen().isPanelOpen(), "escape goes back to the cards");

			input.pressKey(InputConstants.KEY_RIGHT);
			context.waitTicks(ANIMATION_TICKS);
			check(screen().selectedId().equals("cosmetics"), "the arrow key moves the cards");
			clickOn(ZyroxScreen::centerCard);
			context.waitTicks(4);
			clickOn(screen -> screen.tileCenter(1));
			check(Cosmetics.hatId.equals("party_hat"), "clicking a hat wears it, now " + Cosmetics.hatId);
			context.waitTicks(10);
			context.takeScreenshot("cosmetics-hats");
			clickOn(screen -> screen.cosmeticTabCenter(1));
			clickOn(screen -> screen.tileCenter(1));
			check(Cosmetics.aura == 1, "clicking an aura picks it, now " + Cosmetics.aura);
			context.waitTicks(10);
			context.takeScreenshot("cosmetics-auras");
			input.scroll(-3);
			context.waitTicks(10);
			context.takeScreenshot("cosmetics-auras-lower");
			clickOn(screen -> screen.cosmeticTabCenter(0));
			Cosmetics.aura = 0;
			context.waitTicks(ANIMATION_TICKS + 20);
			context.takeScreenshot("zyrox-cosmetics");
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(ANIMATION_TICKS);

			input.pressKey(InputConstants.KEY_RIGHT);
			context.waitTicks(ANIMATION_TICKS);
			check(screen().selectedId().equals("music"), "one step reaches the music card");
			clickOn(ZyroxScreen::centerCard);
			context.waitTicks(ANIMATION_TICKS);
			context.takeScreenshot("zyrox-music");
			clickOn(screen -> screen.rowCenterAt("lyrics-view", 0.75f));
			check(MusicManager.skyLyrics, "the switch turns on the 3D sky lyrics");
			clickOn(screen -> screen.rowCenterAt("lyrics-view", 0.25f));
			check(!MusicManager.skyLyrics, "the switch goes back to 2D");
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
			input.pressKey(InputConstants.KEY_RIGHT);
			input.pressKey(InputConstants.KEY_RIGHT);
			context.waitTicks(ANIMATION_TICKS);
			check(screen().selectedId().equals("settings"), "two more steps reach the settings card");
			clickOn(ZyroxScreen::centerCard);
			context.waitTicks(ANIMATION_TICKS);
			context.takeScreenshot("zyrox-settings");
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);

			input.pressKey(InputConstants.KEY_RSHIFT);
			context.waitTicks(ANIMATION_TICKS);
			check(context.computeOnClient(client -> client.gui.screen() == null), "menu key closes the menu");
			check(context.computeOnClient(client -> client.options.getCameraType()) == CameraType.FIRST_PERSON, "the camera returns to first person");

			fps.enabled = true;
			coordinates.x = 0;
			Cosmetics.hatId = Cosmetics.NOTHING;
			ZyroxConfig.load();
			check(!fps.enabled && coordinates.x == movedX && Cosmetics.hatId.equals("party_hat"), "closing saves the settings and they load back");
			check(keystrokes.scale == 0.5f, "the size is saved too, now " + keystrokes.scale);
			keystrokes.setScale(1f);
		}
	}

	private void testMenuSplash(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		var gist = new AtomicReference<>("Hello from the web");
		HttpServer web;
		try {
			web = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new AssertionError("the test web server does not start", e);
		}
		web.createContext("/", exchange -> {
			var body = gist.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (var out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		web.start();
		MenuSplash.url = "http://127.0.0.1:" + web.getAddress().getPort() + "/";
		MenuSplash.refreshSoon();
		context.waitFor(client -> MenuSplash.text().equals("Hello from the web"), 100);
		context.waitTicks(40);
		context.takeScreenshot("splash-web");

		gist.set("\n  \u00a7cChanged\u0007 text \nsecond line\n");
		MenuSplash.refreshSoon();
		context.waitFor(client -> MenuSplash.text().equals("cChanged text"), 100);
		context.waitTicks(2);
		context.takeScreenshot("splash-changed");

		gist.set("x".repeat(200));
		MenuSplash.refreshSoon();
		context.waitFor(client -> MenuSplash.text().equals("x".repeat(48)), 100);

		gist.set("Kept while offline");
		MenuSplash.refreshSoon();
		context.waitFor(client -> MenuSplash.text().equals("Kept while offline"), 100);
		web.stop(0);
		MenuSplash.refreshSoon();
		context.waitTicks(40);
		check(MenuSplash.text().equals("Kept while offline"), "a failed request keeps the text, got " + MenuSplash.text());

		var empty = new AtomicReference<>("");
		try {
			web = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new AssertionError("the test web server does not start", e);
		}
		web.createContext("/", exchange -> {
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		web.start();
		MenuSplash.url = "http://127.0.0.1:" + web.getAddress().getPort() + "/";
		MenuSplash.refreshSoon();
		context.waitFor(client -> MenuSplash.text().equals(empty.get()), 100);
		context.waitTicks(2);
		context.takeScreenshot("splash-empty");
		web.stop(0);
		MenuSplash.url = "";
	}

	private void testWearables(ClientGameTestContext context) {
		var worn = List.of("party_hat", "red_party_hat");
		for (var id : worn) {
			Cosmetics.hatId = id;
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(4);
			context.takeScreenshot("wear-" + id + "-front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(4);
			context.takeScreenshot("wear-" + id + "-back");
			check(de.sakulius.zyrox.module.WearableModel.of(id).texture() != null, "the model loads: " + id);
		}
		Cosmetics.hatId = Cosmetics.NOTHING;
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	private void testCloud(ClientGameTestContext context) {
		check(CloudCosmetics.status() == CloudCosmetics.Status.NOT_SET_UP, "without a server cosmetics stay local");
		var own = context.computeOnClient(client -> client.getUser().getProfileId());
		CloudCosmetics.serverUrl = DEV_SERVER;
		CloudCosmetics.prover = ZyroxClientTest::testProof;
		Cosmetics.hatId = "party_hat";
		context.waitFor(client -> CloudCosmetics.status() == CloudCosmetics.Status.SYNCED, 400);
		var saved = get("/v1/loadouts?uuids=" + own.toString().replace("-", ""));
		check(saved.contains("party_hat"), "the hat reaches the server, got " + saved);
		Cosmetics.hatId = "red_party_hat";
		context.waitTicks(100);
		saved = get("/v1/loadouts?uuids=" + own.toString().replace("-", ""));
		check(saved.contains("red_party_hat"), "a new hat reaches the server too, got " + saved);

		var other = UUID.fromString("11111111-2222-3333-4444-555555555555");
		var unknown = UUID.fromString("11111111-2222-3333-4444-666666666666");
		post("/__dev/seed", "{\"uuid\":\"" + other.toString().replace("-", "") + "\",\"slot\":\"hat\",\"item\":\"party_hat\"}");
		post("/__dev/seed", "{\"uuid\":\"" + unknown.toString().replace("-", "") + "\",\"slot\":\"hat\",\"item\":\"from_the_future\"}");
		CloudCosmetics.request(List.of(other, unknown));
		context.waitFor(client -> "from_the_future".equals(CloudCosmetics.item(unknown, "hat")) && "party_hat".equals(CloudCosmetics.item(other, "hat")), 400);

		check(Cosmetics.hat(other, false).id().equals("party_hat"), "other players show the hat from the server");
		check(Cosmetics.hat(unknown, false) == null, "unknown items are ignored");
		check(Cosmetics.hat(own, true).id().equals("red_party_hat"), "the own hat comes from the local choice");

		var nearby = UUID.fromString("11111111-2222-3333-4444-777777777777");
		var compactNearby = nearby.toString().replace("-", "");
		post("/__dev/seed", "{\"uuid\":\"" + compactNearby + "\",\"slot\":\"hat\",\"item\":\"party_hat\"}");
		context.runOnClient(client -> {
			var player = new RemotePlayer(client.level, new GameProfile(nearby, "Nearby"));
			player.setId(NEARBY_ENTITY);
			client.level.addEntity(player);
		});
		context.waitFor(client -> "party_hat".equals(CloudCosmetics.item(nearby, "hat")), 100);
		post("/__dev/seed", "{\"uuid\":\"" + compactNearby + "\",\"slot\":\"hat\",\"item\":\"red_party_hat\"}");
		context.waitFor(client -> "red_party_hat".equals(CloudCosmetics.item(nearby, "hat")), 160);
		check(Cosmetics.hat(nearby, false).id().equals("red_party_hat"), "the new hat replaces the old one");
		context.runOnClient(client -> client.level.removeEntity(NEARBY_ENTITY, Entity.RemovalReason.DISCARDED));

		CloudCosmetics.serverUrl = "http://127.0.0.1:9";
		Cosmetics.hatId = "party_hat";
		context.waitTicks(60);
		check(Cosmetics.hat(own, true).id().equals("party_hat"), "the hat still works without a server");
		CloudCosmetics.serverUrl = "";
		CloudCosmetics.prover = null;
		Cosmetics.hatId = Cosmetics.NOTHING;
	}

	private static CloudCosmetics.Proof testProof(String text) throws Exception {
		var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
		var signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(pair.getPrivate());
		signer.update(text.getBytes(StandardCharsets.UTF_8));
		var base64 = Base64.getEncoder();
		return new CloudCosmetics.Proof(base64.encodeToString(pair.getPublic().getEncoded()), System.currentTimeMillis() + 60_000,
			base64.encodeToString(new byte[8]), base64.encodeToString(signer.sign()));
	}

	private static String get(String path) {
		return http(HttpRequest.newBuilder(URI.create(DEV_SERVER + path)).GET().build());
	}

	private static void post(String path, String body) {
		http(HttpRequest.newBuilder(URI.create(DEV_SERVER + path)).POST(HttpRequest.BodyPublishers.ofString(body)).build());
	}

	private static String http(HttpRequest request) {
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
		} catch (Exception e) {
			throw new AssertionError("the dev server must be running", e);
		}
	}

	private void testMusic(ClientGameTestContext context) {
		var parsed = Lyrics.parse("[00:01.50]One\n[00:03.5][00:09.25]Two\nnot a line\n[00:05.123]\n");
		check(parsed.size() == 4 && parsed.get(0).timeMs() == 1500 && parsed.get(1).timeMs() == 3500 && parsed.get(3).timeMs() == 9250 && parsed.get(2).text().isBlank(), "parsing lyrics, got " + parsed);
		check(Lyrics.indexAt(parsed, 0) == -1 && Lyrics.indexAt(parsed, 3600) == 1 && Lyrics.indexAt(parsed, 99000) == 3, "finding the current line");

		var real = Lyrics.find("Rick Astley", "Never Gonna Give You Up - 2022 Remaster", "Unknown Album", 213000);
		System.out.println("ZYROXTEST lrclib lines: " + real.size());
		check(real.size() > 10, "searching lrclib finds a remastered title, got " + real.size());

		try (var session = new WindowsMediaSession()) {
			var snapshot = session.read(true);
			System.out.println("ZYROXTEST session ok, spotify found: " + (snapshot != null) + (snapshot == null ? "" : ", duration known: " + (snapshot.durationMs() > 0) + ", playing: " + snapshot.playing() + ", title length: " + snapshot.title().length()));
		} catch (Throwable t) {
			throw new AssertionError("the Windows media session must open", t);
		}

		var lines = List.of(
			new Lyrics.Line(0, "Walking through the blocks at night"),
			new Lyrics.Line(2500, "Every torch a little light"),
			new Lyrics.Line(600000, "The last line"));
		MusicManager.showForTest(new WindowsMediaSession.Snapshot("Spotify.exe", "Test Song With A Really Rather Long Title Indeed", "Test Artist", "Album", 700000, 3500, true), lines);
		Modules.NOW_PLAYING.enabled = true;
		context.waitTicks(60);
		context.takeScreenshot("zyrox-lyrics");
		MusicManager.skyLyrics = true;
		context.waitTicks(60);
		context.takeScreenshot("zyrox-sky-lyrics");
		context.getInput().holdKey(options -> options.keyLeft);
		context.waitTicks(5);
		context.getInput().releaseKey(options -> options.keyLeft);
		context.runOnClient(client -> client.player.setYRot(client.player.getYRot() + 70f));
		context.waitTicks(4);
		context.takeScreenshot("zyrox-sky-turning");
		context.waitTicks(60);
		context.takeScreenshot("zyrox-sky-settled");
		context.runOnClient(client -> client.player.setYRot(client.player.getYRot() - 70f));
		MusicManager.skyLyrics = false;
		context.waitTicks(20);
	}

	private ZyroxScreen screen() {
		return context.computeOnClient(client -> client.gui.screen() instanceof ZyroxScreen screen ? screen : null);
	}

	private void clickOn(Function<ZyroxScreen, int[]> target) {
		int[] position = context.computeOnClient(client -> target.apply((ZyroxScreen) client.gui.screen()));
		var input = context.getInput();
		input.setCursorPos(position[0] * scale, position[1] * scale);
		context.waitTick();
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static Module find(String id) {
		return Modules.ALL.stream().filter(module -> module.id.equals(id)).findFirst().orElseThrow();
	}

	private static void check(boolean passed, String what) {
		if (!passed)
			throw new AssertionError(what);
	}
}
