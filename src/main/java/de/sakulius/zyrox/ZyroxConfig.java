package de.sakulius.zyrox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import de.sakulius.zyrox.cloud.Friends;
import de.sakulius.zyrox.module.Cosmetics;
import de.sakulius.zyrox.module.HudModule;
import de.sakulius.zyrox.module.Modules;
import de.sakulius.zyrox.music.MusicManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZyroxConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String SETTINGS = "settings";

	private ZyroxConfig() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("zyrox.json");
	}

	private static int intOr(JsonObject object, String key, int fallback) {
		return object.has(key) ? object.get(key).getAsInt() : fallback;
	}

	private static boolean boolOr(JsonObject object, String key, boolean fallback) {
		return object.has(key) ? object.get(key).getAsBoolean() : fallback;
	}

	public static void load() {
		var path = path();
		if (!Files.exists(path))
			return;
		try {
			var root = GSON.fromJson(Files.readString(path), JsonObject.class);
			if (root != null && root.has(SETTINGS)) {
				var settings = root.getAsJsonObject(SETTINGS);
				ZyroxTheme.index = Math.clamp(intOr(settings, "theme", ZyroxTheme.index), 0, ZyroxTheme.CHOICES.size() - 1);
				ZyroxTheme.chromaHud = boolOr(settings, "chromaHud", ZyroxTheme.chromaHud);
				ZyroxTheme.cinematic = boolOr(settings, "cinematic", ZyroxTheme.cinematic);
				if (settings.has("cape") && settings.get("cape").isJsonPrimitive() && Cosmetics.capeById(settings.get("cape").getAsString()) != null)
					Cosmetics.capeId = settings.get("cape").getAsString();
				if (settings.has("hat"))
					Cosmetics.hatId = settings.get("hat").getAsString();
				Cosmetics.aura = Math.clamp(intOr(settings, "aura", Cosmetics.aura), 0, Cosmetics.AURAS.size() - 1);
				HudStyle.panelStyle = Math.clamp(intOr(settings, "hudPanels", HudStyle.panelStyle), 0, HudStyle.PANEL_STYLES.size() - 1);
				HudStyle.blur = Math.clamp(intOr(settings, "hudBlur", HudStyle.blur), 0, HudStyle.BLURS.size() - 1);
				HudStyle.opacity = Math.clamp(intOr(settings, "hudOpacity", HudStyle.opacity), 0, HudStyle.OPACITIES.size() - 1);
				HudStyle.tint = Math.clamp(intOr(settings, "hudTint", HudStyle.tint), 0, HudStyle.TINTS.size() - 1);
				HudStyle.corners = Math.clamp(intOr(settings, "hudCorners", HudStyle.corners), 0, HudStyle.CORNERS.size() - 1);
				HudStyle.border = boolOr(settings, "hudBorder", HudStyle.border);
				HudStyle.shadow = boolOr(settings, "hudShadow", HudStyle.shadow);
				HudStyle.twoTone = boolOr(settings, "hudTwoTone", HudStyle.twoTone);
				HudStyle.labelColor = Math.clamp(intOr(settings, "hudLabelColor", HudStyle.labelColor), 0, HudStyle.TEXT_COLORS.size() - 1);
				HudStyle.valueColor = Math.clamp(intOr(settings, "hudValueColor", HudStyle.valueColor), 0, HudStyle.TEXT_COLORS.size() - 1);
				HudStyle.textShadow = boolOr(settings, "hudTextShadow", HudStyle.textShadow);
				HudStyle.font = Math.clamp(intOr(settings, "hudFont", HudStyle.font), 0, HudStyle.FONTS.size() - 1);
				HudStyle.scoreboard = boolOr(settings, "hudScoreboard", HudStyle.scoreboard);
				HudStyle.hideScores = boolOr(settings, "hudHideScores", HudStyle.hideScores);
				HudStyle.hotbar = boolOr(settings, "hudHotbar", HudStyle.hotbar);
				Friends.appearOnline = boolOr(settings, "appearOnline", Friends.appearOnline);
				Friends.shareServer = boolOr(settings, "shareServer", Friends.shareServer);
				MusicManager.onlySpotify = boolOr(settings, "onlySpotify", MusicManager.onlySpotify);
				MusicManager.karaoke = boolOr(settings, "karaoke", MusicManager.karaoke);
				MusicManager.offsetMs = intOr(settings, "lyricsOffsetMs", MusicManager.offsetMs);
				MusicManager.skyLyrics = boolOr(settings, "skyLyrics", MusicManager.skyLyrics);
				MusicManager.skySize = intOr(settings, "skySize", MusicManager.skySize);
			}
			for (var module : Modules.ALL) {
				if (root == null || !root.has(module.id))
					continue;
				var saved = root.getAsJsonObject(module.id);
				module.enabled = saved.get("enabled").getAsBoolean();
				if (module instanceof HudModule hud) {
					hud.x = saved.get("x").getAsInt();
					hud.y = saved.get("y").getAsInt();
					if (saved.has("scale"))
						hud.setScale(saved.get("scale").getAsFloat());
				}
			}
		} catch (IOException | JsonParseException | IllegalStateException | NullPointerException e) {
			LOGGER.warn("Could not read {}, some settings use their defaults", path, e);
		}
	}

	public static void save() {
		var root = new JsonObject();
		var settings = new JsonObject();
		settings.addProperty("theme", ZyroxTheme.index);
		settings.addProperty("chromaHud", ZyroxTheme.chromaHud);
		settings.addProperty("cinematic", ZyroxTheme.cinematic);
		settings.addProperty("cape", Cosmetics.capeId);
		settings.addProperty("hat", Cosmetics.hatId);
		settings.addProperty("aura", Cosmetics.aura);
		settings.addProperty("hudPanels", HudStyle.panelStyle);
		settings.addProperty("hudBlur", HudStyle.blur);
		settings.addProperty("hudOpacity", HudStyle.opacity);
		settings.addProperty("hudTint", HudStyle.tint);
		settings.addProperty("hudCorners", HudStyle.corners);
		settings.addProperty("hudBorder", HudStyle.border);
		settings.addProperty("hudShadow", HudStyle.shadow);
		settings.addProperty("hudTwoTone", HudStyle.twoTone);
		settings.addProperty("hudLabelColor", HudStyle.labelColor);
		settings.addProperty("hudValueColor", HudStyle.valueColor);
		settings.addProperty("hudTextShadow", HudStyle.textShadow);
		settings.addProperty("hudFont", HudStyle.font);
		settings.addProperty("hudScoreboard", HudStyle.scoreboard);
		settings.addProperty("hudHideScores", HudStyle.hideScores);
		settings.addProperty("hudHotbar", HudStyle.hotbar);
		settings.addProperty("appearOnline", Friends.appearOnline);
		settings.addProperty("shareServer", Friends.shareServer);
		settings.addProperty("onlySpotify", MusicManager.onlySpotify);
		settings.addProperty("karaoke", MusicManager.karaoke);
		settings.addProperty("lyricsOffsetMs", MusicManager.offsetMs);
		settings.addProperty("skyLyrics", MusicManager.skyLyrics);
		settings.addProperty("skySize", MusicManager.skySize);
		root.add(SETTINGS, settings);
		for (var module : Modules.ALL) {
			var saved = new JsonObject();
			saved.addProperty("enabled", module.enabled);
			if (module instanceof HudModule hud) {
				saved.addProperty("x", hud.x);
				saved.addProperty("y", hud.y);
				saved.addProperty("scale", hud.scale);
			}
			root.add(module.id, saved);
		}
		var path = path();
		var temp = path.resolveSibling("zyrox.json.tmp");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(temp, GSON.toJson(root));
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn("Could not save {}", path, e);
		}
	}
}
