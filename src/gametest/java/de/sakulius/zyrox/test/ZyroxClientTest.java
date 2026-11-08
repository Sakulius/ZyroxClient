package de.sakulius.zyrox.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.sun.net.httpserver.HttpServer;
import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.HudStyle;
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
import de.sakulius.zyrox.cloud.Friends;
import de.sakulius.zyrox.cloud.Roles;
import de.sakulius.zyrox.module.News;
import de.sakulius.zyrox.module.PvpTracker;
import de.sakulius.zyrox.module.TntTimer;
import de.sakulius.zyrox.module.Waypoints;
import de.sakulius.zyrox.screen.Glass;
import de.sakulius.zyrox.screen.NewsCard;
import de.sakulius.zyrox.screen.NewsScreen;
import de.sakulius.zyrox.screen.PlayerPreviewWidget;
import de.sakulius.zyrox.screen.NameScreen;
import de.sakulius.zyrox.screen.ZyroxMenuButton;
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
import net.minecraft.network.chat.Component;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
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
		Cosmetics.capeId = Cosmetics.DEFAULT_CAPE;
		Cosmetics.hatId = Cosmetics.NOTHING;
		Cosmetics.aura = 0;
		var coordinates = (HudModule) find("coordinates");
		coordinates.x = 4;
		coordinates.y = 21;
		var fps = find("fps");
		var input = context.getInput();

		testMenuSplash(context);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			var connection = singleplayer.getClientWorld();
			var server = singleplayer.getServer();
			connection.waitForChunksRender();
			testMusic(context);
			testCloud(context);
			testWearables(context);
			testWaypoints(context, server);
			testBadge(context);
			testTntTimer(context, server);
			testPvpModules(context, server);
			testHudStyle(context, server);
			scale = context.computeOnClient(client -> (double) client.getWindow().getGuiScale());
			server.runCommand("effect give @a speed 300 1");
			server.runCommand("item replace entity @a armor.chest with iron_chestplate");
			server.runCommand("item replace entity @a weapon.mainhand with diamond_sword");
			input.holdKey(options -> options.keyUp);
			context.waitTicks(10);
			context.takeScreenshot("zyrox-hud");
			input.releaseKey(options -> options.keyUp);

			float normalFov = context.computeOnClient(client -> ((de.sakulius.zyrox.mixin.GameRendererAccessor) client.gameRenderer).zyrox$fov(client.gameRenderer.getMainCamera(), 1f, true));
			input.holdKey(ZyroxClient.zoomKey);
			context.waitTicks(15);
			float zoomedFov = context.computeOnClient(client -> ((de.sakulius.zyrox.mixin.GameRendererAccessor) client.gameRenderer).zyrox$fov(client.gameRenderer.getMainCamera(), 1f, true));
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
			clickOn(screen -> screen.tileCenter(0));
			check(!fps.enabled, "clicking a tile turns the module off");
			context.waitTicks(10);
			context.takeScreenshot("zyrox-hud-grid");
			input.scroll(-6);
			context.waitTicks(15);
			context.takeScreenshot("zyrox-hud-grid-lower");
			input.scroll(6);
			context.waitTicks(15);
			clickOn(screen -> screen.hudTabCenter(1));
			context.waitTicks(10);
			context.takeScreenshot("zyrox-hud-style");
			input.scroll(-4);
			context.waitTicks(15);
			context.takeScreenshot("zyrox-hud-style-lower");
			input.scroll(4);
			context.waitTicks(15);
			for (int corners = 0; corners < HudStyle.CORNERS.size(); corners++) {
				HudStyle.corners = corners;
				context.waitTick();
			}
			HudStyle.corners = 2;
			clickOn(screen -> screen.rowCenterAt("hud-panels", 0.9f));
			check(HudStyle.panelStyle == HudStyle.SOLID, "the arrow changes the panels, now " + HudStyle.panelStyle);
			HudStyle.panelStyle = HudStyle.GLASS;
			clickOn(screen -> screen.hudTabCenter(0));
			context.waitTicks(10);

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
			clickOn(screen -> screen.cosmeticTabCenter(0));
			clickOn(screen -> screen.tileCenter(2));
			check(Cosmetics.capeId.equals(Cosmetics.DEFAULT_CAPE), "a locked cape cannot be picked, now " + Cosmetics.capeId);
			context.waitTicks(10);
			context.takeScreenshot("cosmetics-locked");
			Cosmetics.setOwned(List.of("aurora", "nebula", "party_hat", "glacier"));
			clickOn(screen -> screen.rowCenter("legacy-capes"));
			context.waitTicks(10);
			clickOn(screen -> screen.tileCenter(1));
			check(Cosmetics.capeId.equals("aurora"), "the legacy button shows the old capes, now " + Cosmetics.capeId);
			check(context.computeOnClient(client -> client.player.getSkin().cape()) == Cosmetics.capeById("aurora").texture(), "an old cape can be worn");
			context.waitTicks(10);
			context.takeScreenshot("cosmetics-legacy");
			input.scroll(-6);
			context.waitTicks(15);
			context.takeScreenshot("cosmetics-legacy-lower");
			input.scroll(6);
			context.waitTicks(15);
			clickOn(screen -> screen.rowCenter("legacy-capes"));
			context.waitTicks(10);
			clickOn(screen -> screen.tileCenter(2));
			check(Cosmetics.capeId.equals("nebula"), "clicking a tile selects the cape, now " + Cosmetics.capeId);
			check(context.computeOnClient(client -> client.player.getSkin().cape()) == Cosmetics.capeById("nebula").texture(), "the own cape changes right away");
			context.waitTicks(10);
			context.takeScreenshot("cosmetics-capes");
			clickOn(screen -> screen.cosmeticTabCenter(1));
			clickOn(screen -> screen.tileCenter(1));
			check(Cosmetics.hatId.equals("party_hat"), "clicking a hat wears it, now " + Cosmetics.hatId);
			context.waitTicks(10);
			context.takeScreenshot("cosmetics-hats");
			clickOn(screen -> screen.cosmeticTabCenter(2));
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
			check(screen().selectedId().equals("pvp"), "two more steps reach the pvp card");
			context.takeScreenshot("zyrox-pvp-card");
			clickOn(ZyroxScreen::centerCard);
			context.waitTicks(ANIMATION_TICKS);
			context.takeScreenshot("zyrox-pvp");
			clickOn(screen -> screen.tileCenter(0));
			check(!Modules.TNT_TIMER.enabled, "a click on the tile turns the tnt timer off");
			clickOn(screen -> screen.tileCenter(0));
			check(Modules.TNT_TIMER.enabled, "a second click turns it on again");
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
			input.pressKey(InputConstants.KEY_RIGHT);
			context.waitTicks(ANIMATION_TICKS);
			check(screen().selectedId().equals("waypoints"), "the next card holds the waypoints");
			clickOn(ZyroxScreen::centerCard);
			context.waitTicks(ANIMATION_TICKS);
			context.takeScreenshot("zyrox-waypoints");
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);
			input.pressKey(InputConstants.KEY_RIGHT);
			context.waitTicks(ANIMATION_TICKS);
			check(screen().selectedId().equals("friends"), "the next card holds the friends");
			input.pressKey(InputConstants.KEY_RIGHT);
			context.waitTicks(ANIMATION_TICKS);
			check(screen().selectedId().equals("settings"), "one more step reaches the settings card");
			clickOn(ZyroxScreen::centerCard);
			context.waitTicks(ANIMATION_TICKS);
			context.takeScreenshot("zyrox-settings");
			input.pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);

			input.pressKey(InputConstants.KEY_RSHIFT);
			context.waitTicks(ANIMATION_TICKS);
			check(context.computeOnClient(client -> client.screen == null), "menu key closes the menu");
			check(context.computeOnClient(client -> client.options.getCameraType()) == CameraType.FIRST_PERSON, "the camera returns to first person");

			fps.enabled = true;
			coordinates.x = 0;
			Cosmetics.hatId = Cosmetics.NOTHING;
			ZyroxConfig.load();
			check(!fps.enabled && coordinates.x == movedX && Cosmetics.hatId.equals("party_hat"), "closing saves the settings and they load back");
			check(keystrokes.scale == 0.5f, "the size is saved too, now " + keystrokes.scale);
			check(Cosmetics.capeId.equals("nebula"), "the cape is saved too, now " + Cosmetics.capeId);
			keystrokes.setScale(1f);
			Cosmetics.capeId = Cosmetics.DEFAULT_CAPE;
			Cosmetics.hatId = Cosmetics.NOTHING;
		}
	}

	private void testWaypoints(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		var input = context.getInput();
		Modules.WAYPOINTS.enabled = true;
		int before = context.computeOnClient(client -> Waypoints.here(client).size());
		input.pressKey(ZyroxClient.waypointKey);
		context.waitTicks(4);
		check(context.computeOnClient(client -> client.screen instanceof NameScreen), "the waypoint key asks for a name");
		context.takeScreenshot("waypoint-name");
		input.pressKey(InputConstants.KEY_END);
		for (int i = 0; i < 12; i++)
			input.pressKey(InputConstants.KEY_BACKSPACE);
		input.typeChars("Home");
		input.pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(4);
		check(context.computeOnClient(client -> client.screen == null), "enter closes the name box");
		check(context.computeOnClient(client -> Waypoints.here(client).size()) == before + 1, "the waypoint is saved");
		check(context.computeOnClient(client -> Waypoints.here(client).getLast().name).equals("Home"), "the waypoint has the typed name");
		server.runCommand("tp @a ~ ~ ~6");
		context.waitTicks(10);
		context.takeScreenshot("waypoint-world");
		float[] middle = context.computeOnClient(client -> {
			var look = client.player.getLookAngle();
			var eye = client.player.getEyePosition();
			return Waypoints.project(client, eye.x + look.x * 8, eye.y + look.y * 8, eye.z + look.z * 8, 400, 200);
		});
		check(Math.abs(middle[0] - 200) < 2 && Math.abs(middle[1] - 100) < 2, "a point straight ahead lands in the middle, got " + middle[0] + ", " + middle[1]);
		float[] behind = context.computeOnClient(client -> {
			var look = client.player.getLookAngle();
			var eye = client.player.getEyePosition();
			return Waypoints.project(client, eye.x - look.x * 8 + 1, eye.y, eye.z - look.z * 8, 400, 200);
		});
		check(behind[0] >= 0 && behind[0] <= 400 && behind[1] >= 0 && behind[1] <= 200, "a point behind stays on the screen");
		context.runOnClient(client -> Waypoints.remove(client, Waypoints.here(client).getLast()));
	}

	private void testBadge(ClientGameTestContext context) {
		var own = context.computeOnClient(client -> client.getUser().getProfileId());
		Modules.BADGE.enabled = true;
		check(context.computeOnClient(client -> de.sakulius.zyrox.module.ZyroxBadge.shows(own)), "the own name has the badge");
		Modules.BADGE.enabled = false;
		check(context.computeOnClient(client -> !de.sakulius.zyrox.module.ZyroxBadge.shows(own)), "the switch turns the badge off");
		Modules.BADGE.enabled = true;
		check(context.computeOnClient(client -> client.gui.getTabList().getNameForDisplay(client.getConnection().getPlayerInfo(own)).getString().contains("")), "the tab list shows the badge");
		var friend = UUID.fromString("11111111-2222-3333-4444-555555555555");
		context.runOnClient(client -> {
			var player = new RemotePlayer(client.level, new GameProfile(friend, "ZyroxFriend"));
			player.setId(NEARBY_ENTITY);
			var look = client.player.getLookAngle();
			player.setPos(client.player.getX() + look.x * 4, client.player.getY(), client.player.getZ() + look.z * 4);
			client.level.addEntity(player);
		});
		context.waitTicks(6);
		context.takeScreenshot("badge-name");
		context.runOnClient(client -> client.level.removeEntity(NEARBY_ENTITY, Entity.RemovalReason.DISCARDED));
	}

	private void testTntTimer(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		check(TntTimer.seconds(80f).equals("4.0s"), "80 ticks are four seconds, got " + TntTimer.seconds(80f));
		check(TntTimer.color(80f) != TntTimer.color(2f), "the label turns red near the end");
		Modules.TNT_TIMER.enabled = true;
		server.runCommand("execute at @a run summon tnt ^ ^1 ^5 {fuse:120}");
		context.waitTicks(10);
		context.takeScreenshot("tnt-timer");
		server.runCommand("kill @e[type=tnt]");
		context.waitTicks(2);
	}

	private void testPvpModules(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		var pvp = Modules.ALL.stream().filter(module -> module.category == Module.Category.PVP).toList();
		check(pvp.size() == 7, "the PvP page has seven modules, got " + pvp.size());
		pvp.forEach(module -> module.enabled = true);
		server.runCommand("time set 1000");
		server.runCommand("kill @e[type=!player]");
		server.runCommand("execute at @a run summon husk ^ ^ ^2.5 {NoAI:1b,Silent:1b,Health:20f}");
		server.runCommand("give @a ender_pearl 16");
		server.runCommand("give @a totem_of_undying 2");
		server.runCommand("give @a golden_apple 5");
		server.runCommand("give @a arrow 32");
		server.runCommand("give @a splash_potion 3");
		server.runCommand("give @a cobblestone 40");
		server.runCommand("item replace entity @a armor.feet with iron_boots[damage=190]");
		context.waitTicks(10);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(14);
		check(PvpTracker.combo() == 1, "a landed hit counts for the combo, got " + PvpTracker.combo());
		check(PvpTracker.reach() > 0.5f && PvpTracker.reach() < 3.5f, "the reach is measured, got " + PvpTracker.reach());
		var counter = (HudModule) find("block_counter");
		check(context.computeOnClient(client -> counter.width(client)) > 0, "the block counter has a size");
		context.takeScreenshot("pvp-modules");
		server.runCommand("damage @p 16");
		context.waitTicks(4);
		check(PvpTracker.combo() == 0, "getting hit ends the combo, got " + PvpTracker.combo());
		context.takeScreenshot("pvp-warnings");
		server.runCommand("effect give @a instant_health 1 5");
		server.runCommand("kill @e[type=husk]");
		server.runCommand("clear @a ender_pearl");
		server.runCommand("clear @a totem_of_undying");
		server.runCommand("clear @a golden_apple");
		server.runCommand("clear @a arrow");
		server.runCommand("clear @a splash_potion");
		server.runCommand("clear @a cobblestone");
		server.runCommand("item replace entity @a armor.feet with air");
		pvp.stream().filter(module -> module != Modules.TNT_TIMER).forEach(module -> module.enabled = false);
		context.waitTicks(4);
	}

	private void testHudStyle(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		server.runCommand("scoreboard objectives add zyroxtest dummy \"EU Practice\"");
		server.runCommand("scoreboard objectives setdisplay sidebar zyroxtest");
		server.runCommand("scoreboard players set Wins zyroxtest 3");
		server.runCommand("scoreboard players set Streak zyroxtest 2");
		server.runCommand("scoreboard players set Online zyroxtest 1");
		server.runCommand("item replace entity @a weapon.offhand with shield");
		server.runCommand("execute at @a run fill ^-1 ^2 ^2 ^1 ^3 ^2 minecraft:gold_block");
		server.runCommand("execute at @a run fill ^-6 ^ ^3 ^6 ^3 ^3 minecraft:lime_wool");
		boolean nowPlaying = Modules.NOW_PLAYING.enabled;
		Modules.NOW_PLAYING.enabled = false;
		HudStyle.reset();
		context.waitTicks(200);
		context.takeScreenshot("style-glass");
		check(context.computeOnClient(client -> Glass.isReady()), "the blur is ready");
		for (int style = 0; style < HudStyle.PANEL_STYLES.size(); style++) {
			for (int corners = 0; corners < HudStyle.CORNERS.size(); corners++) {
				HudStyle.panelStyle = style;
				HudStyle.corners = corners;
				HudStyle.border = true;
				HudStyle.shadow = true;
				context.waitTick();
			}
		}
		HudStyle.reset();
		HudStyle.hideScores = true;
		HudStyle.tint = 2;
		HudStyle.corners = 4;
		HudStyle.font = 1;
		HudStyle.textShadow = true;
		HudStyle.blur = 2;
		context.waitTicks(4);
		context.takeScreenshot("style-custom");
		HudStyle.reset();
		HudStyle.panelStyle = HudStyle.SOLID;
		context.waitTicks(4);
		context.takeScreenshot("style-solid");
		HudStyle.reset();
		HudStyle.scoreboard = false;
		HudStyle.hotbar = false;
		context.waitTicks(4);
		context.takeScreenshot("style-vanilla-bars");
		HudStyle.reset();
		Modules.NOW_PLAYING.enabled = nowPlaying;
		server.runCommand("scoreboard objectives remove zyroxtest");
		server.runCommand("item replace entity @a weapon.offhand with air");
		server.runCommand("execute at @a run fill ^-6 ^ ^3 ^6 ^3 ^3 minecraft:air");
		server.runCommand("execute at @a run fill ^-1 ^2 ^2 ^1 ^3 ^2 minecraft:air");
		context.waitTicks(2);
	}

	private static String signInAs(String uuid) throws Exception {
		var challenge = com.google.gson.JsonParser.parseString(http(HttpRequest.newBuilder(URI.create(DEV_SERVER + "/v1/auth/start")).POST(HttpRequest.BodyPublishers.noBody()).build())).getAsJsonObject().get("challenge").getAsString();
		var proof = testProof("zyrox-auth:" + challenge);
		var body = new com.google.gson.JsonObject();
		body.addProperty("challenge", challenge);
		body.addProperty("uuid", uuid);
		body.addProperty("publicKey", proof.publicKey());
		body.addProperty("expiresAt", proof.expiresAt());
		body.addProperty("keySignature", proof.keySignature());
		body.addProperty("signature", proof.signature());
		var answer = http(HttpRequest.newBuilder(URI.create(DEV_SERVER + "/v1/auth/finish")).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
		return com.google.gson.JsonParser.parseString(answer).getAsJsonObject().get("token").getAsString();
	}

	private static String as(String token, String method, String path, String body) {
		return http(HttpRequest.newBuilder(URI.create(DEV_SERVER + path)).header("Authorization", "Bearer " + token)
			.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build());
	}

	private void testFriends(ClientGameTestContext context) {
		var own = context.computeOnClient(client -> client.getUser().getProfileId()).toString().replace("-", "");
		var buddy = "22222222333344445555666666666666";
		var pal = "22222222333344445555777777777777";
		String buddyToken;
		String palToken;
		try {
			buddyToken = signInAs(buddy);
			palToken = signInAs(pal);
		} catch (Exception e) {
			throw new AssertionError("test friends could not sign in", e);
		}
		as(buddyToken, "PUT", "/v1/presence", "{\"name\":\"Buddy\",\"server\":\"mc.example.net\"}");
		as(palToken, "PUT", "/v1/presence", "{\"name\":\"Pal\",\"server\":\"\"}");
		as(buddyToken, "POST", "/v1/friends/request", "{\"uuid\":\"" + own + "\"}");
		Friends.resolver = name -> name.equalsIgnoreCase("Pal") ? UUID.fromString("22222222-3333-4444-5555-777777777777") : null;
		Friends.refreshSoon();
		context.waitFor(client -> Friends.incoming().size() == 1, 200);
		check(Friends.incoming().getFirst().name().equals("Buddy"), "the request shows the name, got " + Friends.incoming().getFirst().name());
		context.runOnClient(client -> Friends.accept(client, Friends.incoming().getFirst()));
		context.waitFor(client -> Friends.friends().size() == 1 && Friends.incoming().isEmpty(), 200);
		var friend = Friends.friends().getFirst();
		check(friend.online() && friend.server().equals("mc.example.net"), "a friend shows online with the server, got " + friend);
		check(context.computeOnClient(client -> Friends.canJoin(client, friend)), "the friend can be joined");
		context.runOnClient(client -> Friends.add(client, "Nobody"));
		context.waitFor(client -> Friends.message().startsWith("There is no player"), 100);
		context.runOnClient(client -> Friends.add(client, "Pal"));
		context.waitFor(client -> Friends.outgoing().size() == 1, 200);
		as(palToken, "POST", "/v1/friends/accept", "{\"uuid\":\"" + own + "\"}");
		Friends.refreshSoon();
		context.waitFor(client -> Friends.friends().size() == 2, 200);
		context.waitFor(client -> as(buddyToken, "GET", "/v1/friends", null).contains(own), 100);
		check(as(buddyToken, "GET", "/v1/friends", null).contains("\"online\":true"), "friends see you online too");
		context.runOnClient(client -> client.setScreen(ZyroxScreen.pageFrom(null, "friends")));
		context.waitTicks(ANIMATION_TICKS);
		context.takeScreenshot("friends");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(ANIMATION_TICKS);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(ANIMATION_TICKS);
		check(context.computeOnClient(client -> client.screen == null), "the friends page closes");
		context.runOnClient(client -> Friends.remove(client, UUID.fromString("22222222-3333-4444-5555-777777777777"), "Pal"));
		context.waitFor(client -> Friends.friends().size() == 1, 200);
	}

	private void testRoles(ClientGameTestContext context) {
		var ownId = context.computeOnClient(client -> client.getUser().getProfileId());
		var own = ownId.toString().replace("-", "");
		var pal = UUID.fromString("22222222-3333-4444-5555-777777777777");
		Roles.refreshSoon();
		context.waitTicks(40);
		check(!Roles.isStaff(), "a normal player is not staff");
		check(!context.computeOnClient(client -> new ZyroxScreen().pageIds().contains("staff")), "normal players have no staff page");
		post("/__dev/role", "{\"uuid\":\"" + own + "\",\"role\":\"owner\",\"name\":\"Tester\"}");
		Roles.refreshSoon();
		context.waitFor(client -> Roles.isOwner(), 200);
		check(context.computeOnClient(client -> de.sakulius.zyrox.module.ZyroxBadge.decorate(ownId, Component.literal("Me")).getString().contains("")), "the owner gets the crown");
		Friends.resolver = name -> name.equalsIgnoreCase("Pal") ? pal : null;
		context.runOnClient(client -> Roles.set(client, "Pal", Roles.Role.STAFF, null));
		context.waitFor(client -> Roles.team().stream().anyMatch(member -> member.id().equals(pal) && member.role() == Roles.Role.STAFF), 200);
		CloudCosmetics.request(List.of(pal));
		context.waitFor(client -> Roles.of(pal) == Roles.Role.STAFF, 200);
		check(context.computeOnClient(client -> de.sakulius.zyrox.module.ZyroxBadge.decorate(pal, Component.literal("Pal")).getString().contains("")), "staff get the shield in tab");
		String palToken;
		try {
			palToken = signInAs(pal.toString().replace("-", ""));
		} catch (Exception e) {
			throw new AssertionError("pal could not sign in", e);
		}
		check(as(palToken, "POST", "/v1/roles", "{\"uuid\":\"" + own + "\",\"role\":\"\"}").contains("forbidden"), "staff cannot change roles");
		context.runOnClient(client -> client.setScreen(ZyroxScreen.pageFrom(null, "staff")));
		context.waitTicks(ANIMATION_TICKS);
		check(context.computeOnClient(client -> client.screen instanceof ZyroxScreen screen && screen.pageIds().contains("staff")), "the owner has a staff page");
		context.takeScreenshot("staff");
		context.runOnClient(client -> client.setScreen(null));
		context.runOnClient(client -> Roles.set(client, "Pal", null, null));
		context.waitFor(client -> Roles.team().stream().noneMatch(member -> member.id().equals(pal)), 200);
		check(context.computeOnClient(client -> Roles.of(pal) == null), "removing takes the role away");
		post("/__dev/role", "{\"uuid\":\"" + own + "\",\"role\":\"none\"}");
		Roles.refreshSoon();
		context.waitFor(client -> !Roles.isStaff(), 200);
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
		byte[] newsPicture;
		try (var stream = ZyroxClientTest.class.getResourceAsStream("/assets/zyrox/textures/gui/logo.png")) {
			newsPicture = stream.readAllBytes();
		} catch (IOException e) {
			throw new AssertionError("the test picture is missing", e);
		}
		var newsBody = "{\"title\":\"Glass HUD is here\",\"date\":\"2026-11-07\",\"image\":\"http://127.0.0.1:" + web.getAddress().getPort() + "/news.png\",\"changelog\":[\"# New\",\"- Frosted glass behind the HUD, scoreboard and hotbar\",\"- News on the main menu, this is a really long line that has to wrap onto a second line inside the frame\",\"\",\"# Fixes\",\"- Sharp corners no longer crash the game\",\"- A\",\"- B\",\"- C\",\"- D\",\"- E\",\"- F\",\"- G\",\"- H\",\"- I\",\"- J\",\"- K\"]}";
		web.createContext("/news.json", exchange -> {
			var body = newsBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (var out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		web.createContext("/news.png", exchange -> {
			exchange.sendResponseHeaders(200, newsPicture.length);
			try (var out = exchange.getResponseBody()) {
				out.write(newsPicture);
			}
		});
		News.url = "http://127.0.0.1:" + web.getAddress().getPort() + "/news.json";
		News.refreshSoon();
		context.waitFor(client -> News.item() != null && News.item().title().equals("Glass HUD is here") && News.picture() != null, 200);
		check(News.item().title().equals("Glass HUD is here"), "the news title comes from the gist, got " + News.item().title());
		MenuSplash.refreshSoon();
		context.waitFor(client -> MenuSplash.text().equals("Hello from the web"), 100);
		context.waitTicks(40);
		context.takeScreenshot("splash-web");
		int[] card = context.computeOnClient(client -> {
			for (var child : client.screen.children())
				if (child instanceof NewsCard news)
					return new int[] { news.getX() + news.getWidth() / 2, news.getY() + news.getHeight() / 2, (int) client.getWindow().getGuiScale() };
			throw new AssertionError("the main menu has no news card");
		});
		context.getInput().setCursorPos(card[0] * card[2], card[1] * card[2]);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.screen instanceof NewsScreen), "clicking the news opens the changelog");
		context.takeScreenshot("news-changelog");
		context.getInput().scroll(-10);
		context.waitTicks(15);
		context.takeScreenshot("news-changelog-scrolled");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);
		int[] cosmetics = context.computeOnClient(client -> {
			for (var child : client.screen.children())
				if (child instanceof ZyroxMenuButton button && button.getMessage().getString().equals("Cosmetics"))
					return new int[] { button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2, (int) client.getWindow().getGuiScale() };
			throw new AssertionError("the main menu has no cosmetics button");
		});
		check(context.computeOnClient(client -> client.screen.children().stream().anyMatch(child -> child instanceof PlayerPreviewWidget)), "the main menu shows the player");
		Cosmetics.hatId = "party_hat";
		Cosmetics.capeId = "nebula";
		context.waitTicks(4);
		context.takeScreenshot("title-player");
		int[] model = context.computeOnClient(client -> {
			for (var child : client.screen.children())
				if (child instanceof PlayerPreviewWidget widget)
					return new int[] { widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2, (int) client.getWindow().getGuiScale() };
			throw new AssertionError("no player preview");
		});
		context.getInput().setCursorPos(model[0] * model[2], model[1] * model[2]);
		context.waitTick();
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		context.getInput().moveCursor(60 * model[2], 0);
		context.waitTick();
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(4);
		context.takeScreenshot("title-player-back");
		context.getInput().setCursorPos(cosmetics[0] * cosmetics[2], cosmetics[1] * cosmetics[2]);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(25);
		check(context.computeOnClient(client -> client.screen instanceof ZyroxScreen screen && screen.isFromTitle() && screen.isPanelOpen()), "the cosmetics button opens the cosmetics");
		context.takeScreenshot("title-cosmetics");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);
		Cosmetics.hatId = Cosmetics.NOTHING;
		Cosmetics.capeId = Cosmetics.DEFAULT_CAPE;
		int[] options = context.computeOnClient(client -> {
			for (var child : client.screen.children())
				if (child instanceof ZyroxMenuButton button && button.getMessage().getString().startsWith("Options"))
					return new int[] { button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2, (int) client.getWindow().getGuiScale() };
			throw new AssertionError("the menu has no restyled options button");
		});
		context.getInput().setCursorPos(options[0] * options[2], options[1] * options[2]);
		context.waitTicks(10);
		context.takeScreenshot("title-hover");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		check(context.computeOnClient(client -> client.screen instanceof OptionsScreen), "the restyled buttons still work");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);

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
		var worn = List.of("party_hat", "red_party_hat", "third_party_hat");
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
		var skin = context.computeOnClient(client -> client.player.getSkin());
		post("/__dev/own", "{\"uuid\":\"" + own.toString().replace("-", "") + "\",\"items\":[\"party_hat\",\"glacier\"]}");
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
		Cosmetics.capeId = "glacier";
		context.waitTicks(100);
		saved = get("/v1/loadouts?uuids=" + own.toString().replace("-", ""));
		check(saved.contains("glacier"), "the cape reaches the server, got " + saved);
		check(Cosmetics.apply(own, true, skin).cape() == Cosmetics.capeById("glacier").texture(), "the own cape comes from the local choice");
		Cosmetics.capeId = Cosmetics.DEFAULT_CAPE;

		var other = UUID.fromString("11111111-2222-3333-4444-555555555555");
		var unknown = UUID.fromString("11111111-2222-3333-4444-666666666666");
		post("/__dev/seed", "{\"uuid\":\"" + other.toString().replace("-", "") + "\",\"slot\":\"hat\",\"item\":\"party_hat\"}");
		post("/__dev/seed", "{\"uuid\":\"" + unknown.toString().replace("-", "") + "\",\"slot\":\"hat\",\"item\":\"from_the_future\"}");
		CloudCosmetics.request(List.of(other, unknown));
		context.waitFor(client -> "from_the_future".equals(CloudCosmetics.item(unknown, "hat")) && "party_hat".equals(CloudCosmetics.item(other, "hat")), 400);

		check(Cosmetics.hat(other, false).id().equals("party_hat"), "other players show the hat from the server");
		check(CloudCosmetics.usesZyrox(other), "a player with a loadout counts as a Zyrox player");
		check(context.computeOnClient(client -> de.sakulius.zyrox.module.ZyroxBadge.decorate(other, net.minecraft.network.chat.Component.literal("Other")).getString().contains("\ue000")), "Zyrox players get the badge");
		check(context.computeOnClient(client -> !de.sakulius.zyrox.module.ZyroxBadge.decorate(UUID.fromString("11111111-2222-3333-4444-888888888888"), net.minecraft.network.chat.Component.literal("Stranger")).getString().contains("\ue000")), "other players do not get the badge");
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

		testFriends(context);
		testRoles(context);

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

		if (WindowsMediaSession.SUPPORTED) try (var session = new WindowsMediaSession()) {
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
		return context.computeOnClient(client -> client.screen instanceof ZyroxScreen screen ? screen : null);
	}

	private void clickOn(Function<ZyroxScreen, int[]> target) {
		int[] position = context.computeOnClient(client -> target.apply((ZyroxScreen) client.screen));
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
