package de.sakulius.zyrox;

import com.mojang.blaze3d.platform.InputConstants;
import de.sakulius.zyrox.cloud.CloudCosmetics;
import de.sakulius.zyrox.cloud.Friends;
import de.sakulius.zyrox.cloud.RoleCommand;
import de.sakulius.zyrox.module.Cosmetics;
import de.sakulius.zyrox.module.HudModule;
import de.sakulius.zyrox.module.MenuSplash;
import de.sakulius.zyrox.module.Modules;
import de.sakulius.zyrox.module.News;
import de.sakulius.zyrox.module.PvpTracker;
import de.sakulius.zyrox.module.Waypoints;
import de.sakulius.zyrox.module.WearableLayer;
import de.sakulius.zyrox.music.MusicManager;
import de.sakulius.zyrox.music.SkyLyrics;
import de.sakulius.zyrox.music.WindowsMediaSession;
import de.sakulius.zyrox.screen.PlayerPreviewRenderer;
import de.sakulius.zyrox.screen.NameScreen;
import de.sakulius.zyrox.screen.ZyroxScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

public class ZyroxClient implements ClientModInitializer {
	public static final String MOD_ID = "zyrox";
	public static KeyMapping menuKey;
	public static KeyMapping zoomKey;
	public static KeyMapping playPauseKey;
	public static KeyMapping nextKey;
	public static KeyMapping previousKey;
	public static KeyMapping waypointKey;

	private static boolean sprintHeld;

	@Override
	public void onInitializeClient() {
		ZyroxConfig.load();

		var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zyrox.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zyrox.zoom", InputConstants.Type.KEYSYM, InputConstants.KEY_C, category));

		playPauseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zyrox.playpause", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		nextKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zyrox.next", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		previousKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zyrox.previous", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		waypointKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.zyrox.waypoint", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> RoleCommand.register(dispatcher));
		SpecialGuiElementRegistry.register(context -> new PlayerPreviewRenderer(context.vertexConsumers()));
		MusicManager.start();
		WorldRenderEvents.AFTER_ENTITIES.register(SkyLyrics::render);

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> level.isClientSide() ? PvpTracker.onAttack(player, entity) : InteractionResult.PASS);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			MenuSplash.tick(client);
			News.tick(client);
			while (playPauseKey.consumeClick())
				MusicManager.send(WindowsMediaSession.Control.TOGGLE);
			while (nextKey.consumeClick())
				MusicManager.send(WindowsMediaSession.Control.NEXT);
			while (previousKey.consumeClick())
				MusicManager.send(WindowsMediaSession.Control.PREVIOUS);
			while (menuKey.consumeClick())
				client.setScreen(new ZyroxScreen());
			while (waypointKey.consumeClick())
				if (Waypoints.canAdd(client))
					client.setScreen(new NameScreen(null, "New waypoint", Waypoints.nextName(client), name -> Waypoints.add(client, name)));
			Friends.tick(client);
			updateAutoSprint(client);
			PvpTracker.tick(client);
			Cosmetics.tick(client);
			CloudCosmetics.tick(client);
		});

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar)
				helper.register(new WearableLayer(avatar));
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (graphics, delta) -> {
			var client = Minecraft.getInstance();
			for (var module : Modules.ALL)
				if (module.enabled && module instanceof HudModule hud)
					hud.render(graphics, client);
			Waypoints.render(graphics, client);
		});
	}

	private static void updateAutoSprint(Minecraft client) {
		boolean active = Modules.AUTO_SPRINT.enabled && client.player != null && client.screen == null;
		if (active)
			client.options.keySprint.setDown(true);
		else if (sprintHeld)
			client.options.keySprint.setDown(false);
		sprintHeld = active;
	}
}
