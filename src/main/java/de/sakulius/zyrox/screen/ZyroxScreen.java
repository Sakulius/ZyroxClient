package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.sakulius.zyrox.HudStyle;
import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.ZyroxConfig;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.cloud.CloudCosmetics;
import de.sakulius.zyrox.cloud.Friends;
import de.sakulius.zyrox.cloud.Roles;
import de.sakulius.zyrox.module.Cosmetics;
import de.sakulius.zyrox.module.HudModule;
import de.sakulius.zyrox.module.Module;
import de.sakulius.zyrox.module.Modules;
import de.sakulius.zyrox.module.NowPlayingModule;
import de.sakulius.zyrox.module.PvpModule;
import de.sakulius.zyrox.module.Waypoints;
import de.sakulius.zyrox.music.MusicManager;
import de.sakulius.zyrox.music.SkyLyrics;
import de.sakulius.zyrox.music.WindowsMediaSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

public class ZyroxScreen extends Screen {
	private record Page(String id, String title, String description, Identifier icon, float orbit, Supplier<List<Row>> rows) {
	}

	private record Tile(String key, String name, String detail, BooleanSupplier chosen, Runnable choose, TilePreview preview, BooleanSupplier locked, Runnable tryOn) {
		boolean cosmetic() {
			return tryOn != null;
		}
	}

	private interface TilePreview {
		void draw(GuiGraphicsExtractor graphics, int x, int y, int size, float alpha);
	}

	private abstract static class Row {
		final String id;
		final int height;
		String label;
		boolean keep;

		Row(String id, int height) {
			this.id = id;
			this.height = height;
		}

		abstract void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha);

		abstract void click(double mouseX, int x, int y, int w);
	}

	private static final float MAX_TILT = (float) Math.toRadians(32);
	private static final float PERSPECTIVE = 0.17f;
	private static final float SIDE_SCALE = 0.8f;
	private static final float SIDE_FADE = 0.55f;
	private static final float VISIBLE_RANGE = 1.6f;
	private static final float SCROLL_SPEED = 11f;
	private static final float CAROUSEL_X = 0.66f;
	private static final float CARD_RADIUS = 10f;
private static final float PANEL_RADIUS = 12f;
	private static final float ROW_RADIUS = 6f;
	private static final float MORPH_SPEED = 3.2f;
	private static final float CONTENT_SPEED = 2.6f;
	private static final float ROW_STAGGER = 0.05f;
	private static final int ICON_SIZE = 128;
	private static final int ROW_GAP = 2;
	private static final int ROW_HEIGHT = 30;
	private static final int PANEL_MARGIN = 14;
	private static final int PANEL_HEADER = 38;
	private static final int PANEL_MIN_WIDTH = 330;
	private static final int INFO_HEIGHT = 22;
	private static final int SEARCH_WIDTH = 120;
	private static final int SEARCH_HEIGHT = 18;
	private static final int OUTFIT_HEIGHT = 44;
	private static final int DONE_WIDTH = 60;
	private static final int DONE_HEIGHT = 18;
	private static final int SWATCH = 14;
	private static final int SWITCH_WIDTH = 22;
	private static final int SWITCH_HEIGHT = 12;
	private static final int SNAP = 4;
	private static final int GRID = 20;
	private static final int SCRIM_STRIPS = 32;
	private static final float OPEN_SPEED = 2.8f;
	private static final float CLOSE_SPEED = 5f;
	private static final float ORBIT_PER_PIXEL = 1.4f;
	private static final float COSMETICS_ORBIT = 155f;
	private static final int LYRICS_OFFSET_STEPS = 4;
	private static final int HANDLE_SIZE = 7;
	private static final int TAB_HEIGHT = 22;
	private static final int TILE_TOP = 4;
	private static final int TILE_MIN_WIDTH = 52;
	private static final int TILE_HEIGHT = 62;
	private static final int TILE_PREVIEW = 44;
	private static final int TILE_GAP = 5;
	private static final List<String> COSMETIC_TABS = List.of("Capes", "Hats", "Auras");
	private static final List<String> HUD_TABS = List.of("Elements", "Style");
	private static final int LYRICS_OFFSET_MS = 250;
	private static final int WAYPOINT_BUTTONS = 66;
	private static final int FRIEND_BUTTON = 44;
	private static final int FRIEND_BUTTONS = FRIEND_BUTTON * 2 + 12;
	private static final int ONLINE = 0xFF57E05B;
	private static final int WAYPOINT_EDIT = 40;
	private static final int WAYPOINT_DELETE = 16;

	private static final int PANEL_COLOR = 0xEE101116;
	private static final int PANEL_BORDER = 0x1EFFFFFF;
	private static final int HOVER = 0x0FFFFFFF;
	private static final int CARD_TOP = 0xF4202229;
	private static final int CARD_BOTTOM = 0xF4121318;
	private static final int CARD_BORDER = 0x26FFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFF8D929C;
	private static final int SWITCH_OFF = 0xFF34363E;
	private static final int SHADOW = 0x90000000;
	private static final int BLACK = 0xFF000000;

	private static final Random RANDOM = new Random();

	private static int cosmeticTab;
	private static boolean legacyCapes;
	private static int hudTab;

	private final Map<String, Float> toggleAnim = new HashMap<>();
	private final Map<String, Float> hoverAnim = new HashMap<>();
	private final List<Page> pages;

	private String search = "";
	private boolean searchFocused;
	private float contentAnim;
	private String contentKey = "";
	private String hoverKey;
	private String lastHoverKey;
	private Tile hoveredTile;
	private float morph;
	private float[] morphFrom;

	private long lastFrame = System.nanoTime();
	private float dt;
	private float age;
	private float open;
	private float closing;
	private boolean isClosing;
	private float position;
	private int selected;
	private Page panel;
	private Page shownPanel;
	private float panelAmount;
	private float panelScroll;
	private float panelScrollTarget;
	private float editAmount;
	private double clickY;
	private float viewSlide = MusicManager.skyLyrics ? 1f : 0f;
	private boolean editing;
	private boolean orbiting;
	private HudModule dragging;
	private HudModule resizing;
	private int resizeLeft;
	private int resizeTop;
	private int resizeWidth;
	private int resizeHeight;
	private float resizeScale;
	private int dragOffsetX;
	private int dragOffsetY;
	private int guideX = -1;
	private int guideY = -1;

	private Screen parent;
	private PlayerPreviewWidget preview;

	public static ZyroxScreen cosmeticsFrom(Screen parent) {
		return pageFrom(parent, "cosmetics");
	}

	public static ZyroxScreen pageFrom(Screen parent, String page) {
		var screen = new ZyroxScreen();
		screen.parent = parent;
		for (int i = 0; i < screen.pages.size(); i++)
			if (screen.pages.get(i).id().equals(page))
				screen.selected = i;
		screen.position = screen.selected;
		screen.panel = screen.pages.get(screen.selected);
		screen.shownPanel = screen.panel;
		return screen;
	}

	public boolean isFromTitle() {
		return parent != null;
	}

	public ZyroxScreen() {
		super(Component.literal("Zyrox Client"));
		var list = new ArrayList<>(List.of(
			new Page("hud", "HUD", "Widgets and layout", icon("hud"), 0f, this::hudRows),
			new Page("cosmetics", "Cosmetics", "Capes, hats and auras", icon("cosmetics"), COSMETICS_ORBIT, this::cosmeticRows),
			new Page("music", "Music", "Lyrics and controls", icon("music"), 0f, this::musicRows),
			new Page("utility", "Utility", "Zoom and helpers", icon("utility"), 0f, this::utilityRows),
			new Page("pvp", "PvP", "Fight helpers", icon("pvp"), 0f, this::pvpRows),
			new Page("waypoints", "Waypoints", "Mark your places", icon("waypoints"), 0f, this::waypointRows),
			new Page("friends", "Friends", "Online and joining", icon("friends"), 0f, this::friendRows),
			new Page("settings", "Settings", "Theme and camera", icon("settings"), 0f, this::settingRows)));
		if (Roles.isStaff())
			list.add(list.size() - 1, new Page("staff", "Staff", "Team and roles", icon("staff"), 0f, this::staffRows));
		pages = List.copyOf(list);
		selected = 1;
		position = 1;
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "textures/icon/" + name + ".png");
	}

	@Override
	protected void init() {
		if (ZyroxTheme.cinematic && !editing && !isClosing)
			CameraRig.setActive(true);
		if (minecraft.level == null) {
			int modelHeight = (int) (height * 0.58f);
			int modelWidth = modelHeight * 3 / 4;
			preview = new PlayerPreviewWidget((panelLeft() - modelWidth) / 2, (height - modelHeight) / 2, modelWidth, modelHeight);
			addRenderableWidget(preview);
		}
	}

	private void pill(GuiGraphicsExtractor graphics, int x, int y, float amount, int accent, float alpha) {
		Draw.roundRect(graphics, x, y, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2f, Fx.alpha(Fx.mix(SWITCH_OFF, accent, amount), alpha));
		float knobX = x + 2 + amount * (SWITCH_WIDTH - SWITCH_HEIGHT);
		Draw.roundRect(graphics, knobX, y + 2, SWITCH_HEIGHT - 4, SWITCH_HEIGHT - 4, (SWITCH_HEIGHT - 4) / 2f, Fx.alpha(TEXT, alpha));
	}

	private static Row labeled(Row row, String label) {
		row.label = label;
		return row;
	}

	private boolean hover(String key, boolean inside) {
		if (inside)
			hoverKey = key;
		return inside;
	}

	private Row toggle(String id, String name, String detail, BooleanSupplier on, Consumer<Boolean> set) {
		return labeled(new Row(id, ROW_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				float anim = Fx.approach(toggleAnim.getOrDefault(id, on.getAsBoolean() ? 1f : 0f), on.getAsBoolean() ? 1f : 0f, dt, 16f);
				toggleAnim.put(id, anim);
				if (hover(id, inside(mouseX, mouseY, x, y, w, height)))
					Draw.roundRect(graphics, x, y, w, height, ROW_RADIUS, Fx.alpha(HOVER, alpha));
				Type.SEMIBOLD.draw(graphics, font, name, x + 8, y + 6, Fx.alpha(TEXT, alpha));
				Type.REGULAR.draw(graphics, font, detail, x + 8, y + 17, Fx.alpha(MUTED, alpha));
				pill(graphics, x + w - SWITCH_WIDTH - 8, y + (height - SWITCH_HEIGHT) / 2, anim, ZyroxTheme.accent(), alpha);
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				boolean value = !on.getAsBoolean();
				set.accept(value);
				(value ? Sounds.TOGGLE_ON : Sounds.TOGGLE_OFF).play();
			}
		}, name + " " + detail);
	}

	private Row select(String id, String name, Supplier<List<String>> values, IntSupplier get, IntConsumer set) {
		return labeled(new Row(id, ROW_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				if (hover(id, inside(mouseX, mouseY, x, y, w, height)))
					Draw.roundRect(graphics, x, y, w, height, ROW_RADIUS, Fx.alpha(HOVER, alpha));
				Type.SEMIBOLD.draw(graphics, font, name, x + 8, y + 11, Fx.alpha(TEXT, alpha));
				Type.REGULAR.draw(graphics, font, "‹", x + w - 90, y + 11, Fx.alpha(MUTED, alpha));
				Type.REGULAR.draw(graphics, font, "›", x + w - 12, y + 11, Fx.alpha(MUTED, alpha));
				Type.REGULAR.centered(graphics, font, values.get().get(get.getAsInt()), x + w - 48, y + 11, Fx.alpha(TEXT, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				int count = values.get().size();
				int step = mouseX < x + w - 48 ? count - 1 : 1;
				set.accept((get.getAsInt() + step) % count);
				Sounds.TAB.play(step == 1 ? 1.1f : 0.9f);
			}
		}, name);
	}

	private Row button(String id, String name, Runnable action) {
		return labeled(new Row(id, 24) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				boolean hovered = hover(id, inside(mouseX, mouseY, x, y, w, height));
				float lift = hoverAmount(id, hovered);
				Draw.roundRect(graphics, x, y, w, height, ROW_RADIUS, Fx.alpha(Fx.mix(ZyroxTheme.accent(), TEXT, lift * 0.14f), alpha));
				Type.SEMIBOLD.centered(graphics, font, name, x + w / 2, y + 8, Fx.alpha(TEXT, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				action.run();
				Sounds.CLICK.play();
			}
		}, name);
	}

	private Row note(String text) {
		return new Row("note", 14) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				Type.REGULAR.draw(graphics, font, fitted(text, w - 16), x + 8, y + 3, Fx.alpha(MUTED, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
			}
		};
	}

	private Row swatches() {
		return labeled(new Row("theme", 40) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				Type.SEMIBOLD.draw(graphics, font, "Accent color", x + 8, y + 5, Fx.alpha(TEXT, alpha));
				Type.REGULAR.draw(graphics, font, ZyroxTheme.CHOICES.get(ZyroxTheme.index).name(), x + w - 8 - Type.REGULAR.width(font, ZyroxTheme.CHOICES.get(ZyroxTheme.index).name()), y + 5, Fx.alpha(MUTED, alpha));
				for (int i = 0; i < ZyroxTheme.CHOICES.size(); i++) {
					int sx = x + 8 + i * (SWATCH + 6);
					int sy = y + 19;
					int color = i == ZyroxTheme.CHOICES.size() - 1 ? ZyroxTheme.chroma(0) : ZyroxTheme.CHOICES.get(i).color();
					Draw.roundRect(graphics, sx, sy, SWATCH, SWATCH, SWATCH / 2f, Fx.alpha(color, alpha));
					if (i == ZyroxTheme.index)
						Draw.outline(graphics, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4, SWATCH / 2f + 2, Fx.alpha(TEXT, alpha));
				}
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				int index = (int) (mouseX - x - 8) / (SWATCH + 6);
				if (mouseX >= x + 8 && index < ZyroxTheme.CHOICES.size()) {
					ZyroxTheme.index = index;
					Sounds.TAB.play(1.2f);
				}
			}
		}, "Accent color theme");
	}

	private Row moduleToggle(Module module) {
		return toggle(module.id, module.name, module.description, () -> module.enabled, on -> module.enabled = on);
	}

	private List<Row> hudRows() {
		var rows = new ArrayList<Row>();
		rows.add(button("edit", "Edit HUD layout", this::enterEdit));
		var tiles = new ArrayList<Tile>();
		for (var module : Modules.ALL)
			if (module.category == Module.Category.HUD)
				tiles.add(moduleTile(module, icon("hud/" + module.id), ICON_SIZE));
		if (searching()) {
			rows.add(tileGrid(tiles));
			hudStyleRows(rows);
			return rows;
		}
		rows.add(tabs("hud-tabs", HUD_TABS, () -> hudTab, index -> hudTab = index));
		if (hudTab == 1) {
			hudStyleRows(rows);
			return rows;
		}
		long on = tiles.stream().filter(tile -> tile.chosen().getAsBoolean()).count();
		rows.add(countHeading("hud-count", "Elements", on + " on"));
		rows.add(tileGrid(tiles));
		rows.add(note("Click a tile to turn it on or off. Type to search."));
		return rows;
	}

	private void hudStyleRows(List<Row> rows) {
		rows.add(stylePreview());
		rows.add(select("hud-panels", "Panels", () -> HudStyle.PANEL_STYLES, () -> HudStyle.panelStyle, value -> HudStyle.panelStyle = value));
		if (HudStyle.panelStyle == HudStyle.GLASS)
			rows.add(select("hud-blur", "Blur", () -> HudStyle.BLURS, () -> HudStyle.blur, value -> HudStyle.blur = value));
		if (HudStyle.panelStyle != HudStyle.NO_PANEL) {
			rows.add(select("hud-opacity", "Panel opacity", () -> HudStyle.OPACITIES, () -> HudStyle.opacity, value -> HudStyle.opacity = value));
			rows.add(colorRow("hud-tint", "Panel color", HudStyle.TINTS, () -> HudStyle.tint, value -> HudStyle.tint = value));
			rows.add(select("hud-corners", "Corners", () -> HudStyle.CORNERS, () -> HudStyle.corners, value -> HudStyle.corners = value));
			rows.add(toggle("hud-border", "Border", "Thin line around panels", () -> HudStyle.border, on -> HudStyle.border = on));
			rows.add(toggle("hud-shadow", "Shadow", "Soft shadow behind panels", () -> HudStyle.shadow, on -> HudStyle.shadow = on));
		}
		rows.add(toggle("hud-two-tone", "Two-tone text", "Labels get their own color", () -> HudStyle.twoTone, on -> HudStyle.twoTone = on));
		if (HudStyle.twoTone)
			rows.add(colorRow("hud-label", "Label color", HudStyle.TEXT_COLORS, () -> HudStyle.labelColor, value -> HudStyle.labelColor = value));
		rows.add(colorRow("hud-value", "Text color", HudStyle.TEXT_COLORS, () -> HudStyle.valueColor, value -> HudStyle.valueColor = value));
		rows.add(toggle("chroma-hud", "Chroma HUD", "Rainbow HUD elements", () -> ZyroxTheme.chromaHud, on -> ZyroxTheme.chromaHud = on));
		rows.add(toggle("hud-text-shadow", "Text shadow", "Easier to read on bright ground", () -> HudStyle.textShadow, on -> HudStyle.textShadow = on));
		rows.add(select("hud-font", "Font", () -> HudStyle.FONTS, () -> HudStyle.font, value -> HudStyle.font = value));
		rows.add(toggle("hud-scoreboard", "Scoreboard panel", "Sidebar in the HUD style", () -> HudStyle.scoreboard, on -> HudStyle.scoreboard = on));
		rows.add(toggle("hud-hide-scores", "Hide score numbers", "Removes the red numbers", () -> HudStyle.hideScores, on -> HudStyle.hideScores = on));
		rows.add(toggle("hud-hotbar", "Hotbar panel", "Hotbar in the HUD style", () -> HudStyle.hotbar, on -> HudStyle.hotbar = on));
		rows.add(button("hud-reset", "Reset style", () -> {
			HudStyle.reset();
			ZyroxTheme.chromaHud = false;
		}));
	}

	private Row stylePreview() {
		return new Row("hud-preview", 34) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				HudStyle.begin();
				int left = x + 8;
				for (var text : List.of("FPS 573", "BPS 0.0")) {
					int width = Type.REGULAR.width(font, text) + 10;
					HudStyle.panel(graphics, left, y + 9, width, 15);
					HudStyle.text(graphics, font, text, left + 5, y + 13, left, y + 9);
					left += width + 6;
				}
				HudStyle.end();
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
			}
		};
	}

	private Row colorRow(String id, String name, List<HudStyle.Swatch> colors, IntSupplier get, IntConsumer set) {
		return labeled(new Row(id, 40) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				var chosen = colors.get(Math.clamp(get.getAsInt(), 0, colors.size() - 1)).name();
				Type.SEMIBOLD.draw(graphics, font, name, x + 8, y + 5, Fx.alpha(TEXT, alpha));
				Type.REGULAR.draw(graphics, font, chosen, x + w - 8 - Type.REGULAR.width(font, chosen), y + 5, Fx.alpha(MUTED, alpha));
				for (int i = 0; i < colors.size(); i++) {
					int sx = x + 8 + i * (SWATCH + 6);
					int sy = y + 19;
					Draw.roundRect(graphics, sx, sy, SWATCH, SWATCH, SWATCH / 2f, Fx.alpha(HudStyle.swatchColor(colors, i) | 0xFF000000, alpha));
					Draw.outline(graphics, sx, sy, SWATCH, SWATCH, SWATCH / 2f, Fx.alpha(0x30FFFFFF, alpha));
					if (i == get.getAsInt())
						Draw.outline(graphics, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4, SWATCH / 2f + 2, Fx.alpha(TEXT, alpha));
				}
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				int index = (int) (mouseX - x - 8) / (SWATCH + 6);
				if (mouseX >= x + 8 && index < colors.size()) {
					set.accept(index);
					Sounds.TAB.play(1.2f);
				}
			}
		}, name);
	}

	private Tile moduleTile(Module module, Identifier image, int imageSize) {
		return new Tile(module.id, module.name, module.description, () -> module.enabled, () -> {
			module.enabled = !module.enabled;
			(module.enabled ? Sounds.TOGGLE_ON : Sounds.TOGGLE_OFF).play();
		}, (graphics, x, y, size, alpha) -> {
			int shown = size * 3 / 4;
			graphics.blit(RenderPipelines.GUI_TEXTURED, image, x + (size - shown) / 2, y + (size - shown) / 2, 0f, 0f, shown, shown, imageSize, imageSize, imageSize, imageSize, Fx.alpha(TEXT, module.enabled ? alpha : alpha * 0.35f));
		}, () -> false, null);
	}

	private List<Tile> cosmeticTiles(int tab, boolean legacy) {
		return switch (tab) {
			case 0 -> capeTiles(legacy ? Cosmetics.LEGACY_CAPES : Cosmetics.CAPES);
			case 1 -> hatTiles();
			default -> auraTiles();
		};
	}

	private List<String> cosmeticTabLabels() {
		var labels = new ArrayList<String>();
		for (int i = 0; i < COSMETIC_TABS.size(); i++) {
			var tiles = cosmeticTiles(i, false);
			long owned = tiles.stream().filter(tile -> !tile.locked().getAsBoolean()).count();
			labels.add(COSMETIC_TABS.get(i) + "  " + owned + "/" + tiles.size());
		}
		return labels;
	}

	private List<Row> cosmeticRows() {
		var rows = new ArrayList<Row>();
		boolean team = Roles.own() != null;
		if (searching()) {
			var all = new ArrayList<Tile>();
			for (int i = 0; i < COSMETIC_TABS.size(); i++)
				all.addAll(cosmeticTiles(i, false));
			if (team)
				all.addAll(capeTiles(Cosmetics.LEGACY_CAPES));
			rows.add(wearing());
			rows.add(tileGrid("grid", sortedTiles(all, false)));
			rows.add(tileGrid("grid-locked", sortedTiles(all, true)));
			return rows;
		}
		boolean legacy = cosmeticTab == 0 && legacyCapes && team;
		rows.add(tabs("tabs", cosmeticTabLabels(), () -> cosmeticTab, index -> {
			cosmeticTab = index;
			legacyCapes = false;
		}));
		rows.add(wearing());
		rows.add(cosmeticActions());
		rows.add(outfits());
		if (cosmeticTab == 0 && team) {
			rows.add(button("legacy-capes", legacy ? "Back to capes" : "Legacy capes  \u00b7  Team only", () -> {
				legacyCapes = !legacyCapes;
				panelScroll = 0;
				panelScrollTarget = 0;
			}));
		}
		var tiles = cosmeticTiles(cosmeticTab, legacy);
		var favorites = tiles.stream().filter(tile -> Cosmetics.FAVORITES.contains(tile.key()) && !tile.locked().getAsBoolean()).toList();
		if (!favorites.isEmpty()) {
			rows.add(countHeading("favorites", "\u2605  Favorites", String.valueOf(favorites.size())));
			rows.add(tileGrid("grid-favorites", favorites));
		}
		var unlocked = sortedTiles(tiles, false);
		var locked = sortedTiles(tiles, true);
		rows.add(countHeading("unlocked", legacy ? "Legacy capes" : "Unlocked", unlocked.size() + " / " + tiles.size()));
		rows.add(tileGrid("grid", unlocked));
		if (!locked.isEmpty()) {
			rows.add(countHeading("locked", "Locked", String.valueOf(locked.size())));
			rows.add(tileGrid("grid-locked", locked));
		}
		rows.add(syncStatus());
		if (!team && !locked.isEmpty())
			rows.add(note("Locked cosmetics are given out by the Zyrox team."));
		if (cosmeticTab == 1)
			rows.add(note("Hats step aside while you wear a helmet."));
		else if (cosmeticTab == 2)
			rows.add(note("Auras only show for you."));
		rows.add(note("Hover anything to try it on, even locked ones."));
		rows.add(note("Right click a tile to add it to your favorites."));
		rows.add(note("Drag next to the player to look around."));
		return rows;
	}

	private Row cosmeticActions() {
		String[] labels = { "Randomize", "Take off all" };
		return new Row("cosmetic-actions", 22) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				int width = (w - 16 - TILE_GAP) / 2;
				for (int i = 0; i < labels.length; i++) {
					int bx = x + 8 + i * (width + TILE_GAP);
					boolean hovered = hover("cosmetic-action-" + i, inside(mouseX, mouseY, bx, y, width, height));
					float lift = hoverAmount("cosmetic-action-" + i, hovered);
					Draw.roundRect(graphics, bx, y, width, height, ROW_RADIUS, Fx.alpha(Fx.mix(0x1EFFFFFF, 0x38FFFFFF, lift), alpha));
					Type.SEMIBOLD.centered(graphics, font, labels[i], bx + width / 2, y + 7, Fx.alpha(TEXT, alpha));
				}
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				int width = (w - 16 - TILE_GAP) / 2;
				if (mouseX < x + 8 + width) {
					randomize();
					Sounds.EQUIP.play(1.1f);
				} else {
					Cosmetics.capeId = Cosmetics.DEFAULT_CAPE;
					Cosmetics.hatId = Cosmetics.NOTHING;
					Cosmetics.aura = 0;
					Sounds.CLOSE.play(1.2f);
				}
			}
		};
	}

	private void randomize() {
		var capes = Cosmetics.CAPES.stream().filter(cape -> Cosmetics.owns(cape.id())).toList();
		var hats = Cosmetics.HATS.stream().filter(hat -> Cosmetics.owns(hat.id())).toList();
		var auras = new ArrayList<Integer>();
		for (int i = 0; i < Cosmetics.AURAS.size(); i++)
			if (Cosmetics.owns(Cosmetics.auraId(Cosmetics.AURAS.get(i))))
				auras.add(i);
		if (!capes.isEmpty())
			Cosmetics.capeId = capes.get(RANDOM.nextInt(capes.size())).id();
		if (!hats.isEmpty())
			Cosmetics.hatId = hats.get(RANDOM.nextInt(hats.size())).id();
		if (!auras.isEmpty())
			Cosmetics.aura = auras.get(RANDOM.nextInt(auras.size()));
	}

	private String outfitSummary(String[] outfit) {
		if (outfit == null)
			return "Empty";
		var parts = new ArrayList<String>();
		var cape = Cosmetics.capeById(outfit[0]);
		if (cape != null && cape.texture() != null)
			parts.add(cape.name());
		var hat = Cosmetics.hatName(outfit[1]);
		if (hat != null && !outfit[1].equals(Cosmetics.NOTHING))
			parts.add(hat);
		return parts.isEmpty() ? "Nothing" : String.join(", ", parts);
	}

	private Row outfits() {
		return new Row("outfits", OUTFIT_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				int count = Cosmetics.OUTFITS.length;
				int width = (w - 16 - TILE_GAP * (count - 1)) / count;
				int half = (width - 15) / 2;
				for (int i = 0; i < count; i++) {
					var outfit = Cosmetics.OUTFITS[i];
					int bx = x + 8 + i * (width + TILE_GAP);
					boolean hovered = hover("outfit-" + i, inside(mouseX, mouseY, bx, y, width, height));
					float lift = hoverAmount("outfit-" + i, hovered);
					boolean worn = outfit != null && Arrays.equals(outfit, Cosmetics.outfit());
					Draw.roundRect(graphics, bx, y, width, height, ROW_RADIUS, Fx.alpha(Fx.mix(0x14FFFFFF, 0x2CFFFFFF, lift), alpha));
					if (worn)
						Draw.outline(graphics, bx - 1, y - 1, width + 2, height + 2, ROW_RADIUS + 1, Fx.alpha(ZyroxTheme.accent(), alpha));
					Type.SEMIBOLD.draw(graphics, font, "Outfit " + (i + 1), bx + 6, y + 5, Fx.alpha(TEXT, alpha));
					Type.REGULAR.draw(graphics, font, fitted(outfitSummary(outfit), width - 12), bx + 6, y + 16, Fx.alpha(MUTED, alpha));
					if (outfit == null) {
						smallButton(graphics, bx + 5, y + height - 16, width - 10, "Save current", 0x1EFFFFFF, mouseX, mouseY, alpha);
						continue;
					}
					smallButton(graphics, bx + 5, y + height - 16, half, "Wear", ZyroxTheme.accent(), mouseX, mouseY, alpha);
					smallButton(graphics, bx + 10 + half, y + height - 16, half, "Save", 0x1EFFFFFF, mouseX, mouseY, alpha);
				}
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				int count = Cosmetics.OUTFITS.length;
				int width = (w - 16 - TILE_GAP * (count - 1)) / count;
				int index = (int) ((mouseX - x - 8) / (width + TILE_GAP));
				if (mouseX < x + 8 || index >= count || clickY < y + height - 16)
					return;
				int bx = x + 8 + index * (width + TILE_GAP);
				boolean save = Cosmetics.OUTFITS[index] == null || mouseX >= bx + 10 + (width - 15) / 2;
				if (save) {
					Cosmetics.OUTFITS[index] = Cosmetics.outfit();
					Sounds.TOGGLE_ON.play();
				} else {
					Cosmetics.wear(Cosmetics.OUTFITS[index]);
					Sounds.EQUIP.play();
				}
			}
		};
	}

	private List<Tile> sortedTiles(List<Tile> tiles, boolean locked) {
		var first = tiles.getFirst();
		return tiles.stream().filter(tile -> tile.locked().getAsBoolean() == locked)
			.sorted(Comparator.comparing((Tile tile) -> tile != first).thenComparing(Tile::name)).toList();
	}

	private Row countHeading(String id, String text, String count) {
		return new Row(id, 18) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				Type.SEMIBOLD.draw(graphics, font, text, x + 8, y + 7, Fx.alpha(TEXT, alpha));
				Type.REGULAR.draw(graphics, font, count, x + w - 8 - Type.REGULAR.width(font, count), y + 7, Fx.alpha(MUTED, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
			}
		};
	}

	private Row wearing() {
		return new Row("wearing", 32) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				Draw.roundRect(graphics, x + 8, y, w - 16, height, ROW_RADIUS, Fx.alpha(0x14FFFFFF, alpha));
				Draw.roundRect(graphics, x + 8, y + 6, 2, height - 12, 1f, Fx.alpha(ZyroxTheme.accent(), alpha));
				var worn = new ArrayList<String>();
				var cape = Cosmetics.capeById(Cosmetics.capeId);
				if (cape != null && cape.texture() != null)
					worn.add(cape.name() + " Cape");
				var hat = Cosmetics.hatName(Cosmetics.hatId);
				if (hat != null && !Cosmetics.hatId.equals(Cosmetics.NOTHING))
					worn.add(hat);
				if (Cosmetics.aura > 0)
					worn.add(Cosmetics.AURAS.get(Cosmetics.aura).name() + " Aura");
				Type.REGULAR.draw(graphics, font, "Wearing", x + 16, y + 5, Fx.alpha(MUTED, alpha));
				Type.SEMIBOLD.draw(graphics, font, fitted(worn.isEmpty() ? "Nothing yet" : String.join("  \u00b7  ", worn), w - 32), x + 16, y + 17, Fx.alpha(TEXT, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
			}
		};
	}

	private Row tabs(String id, List<String> labels, IntSupplier get, IntConsumer set) {
		var row = new Row(id, TAB_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				int tabWidth = tabWidth(w, labels.size());
				Draw.roundRect(graphics, x + 6, y - 2, w - 12, height + 4, ROW_RADIUS + 2, Fx.alpha(0x10FFFFFF, alpha));
				float slide = Fx.approach(hoverAnim.getOrDefault("tab-slide-" + id, (float) get.getAsInt()), get.getAsInt(), dt, 14f);
				hoverAnim.put("tab-slide-" + id, slide);
				Draw.roundRect(graphics, x + 8 + slide * (tabWidth + TILE_GAP), y, tabWidth, height, ROW_RADIUS, Fx.alpha(ZyroxTheme.accent(), alpha));
				for (int i = 0; i < labels.size(); i++) {
					int tabX = tabX(x, w, labels.size(), i);
					boolean hovered = hover(id + "-" + i, inside(mouseX, mouseY, tabX, y, tabWidth, height));
					if (hovered && i != get.getAsInt())
						Draw.roundRect(graphics, tabX, y, tabWidth, height, ROW_RADIUS, Fx.alpha(HOVER, alpha));
					Type.SEMIBOLD.centered(graphics, font, labels.get(i), tabX + tabWidth / 2, y + (height - 8) / 2, Fx.alpha(i == get.getAsInt() || hovered ? TEXT : MUTED, alpha));
				}
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				for (int i = 0; i < labels.size(); i++) {
					if (mouseX >= tabX(x, w, labels.size(), i) && mouseX < tabX(x, w, labels.size(), i) + tabWidth(w, labels.size())) {
						if (i != get.getAsInt())
							Sounds.TAB.play();
						set.accept(i);
						panelScroll = 0;
						panelScrollTarget = 0;
						return;
					}
				}
			}
		};
		row.keep = true;
		return row;
	}

	private int tabWidth(int w, int count) {
		return (w - 16 - TILE_GAP * (count - 1)) / count;
	}

	private int tabX(int x, int w, int count, int index) {
		return x + 8 + index * (tabWidth(w, count) + TILE_GAP);
	}

	private List<Tile> capeTiles(List<Cosmetics.Cape> capes) {
		var tiles = new ArrayList<Tile>();
		for (var cape : capes) {
			tiles.add(new Tile(cape.id(), cape.name(), "Cape", () -> cape.id().equals(Cosmetics.capeId), () -> Cosmetics.capeId = cape.id(), (graphics, x, y, size, alpha) -> {
				int capeWidth = size * 10 / 16;
				if (cape.texture() != null)
					graphics.blit(RenderPipelines.GUI_TEXTURED, cape.texture().texturePath(), x + (size - capeWidth) / 2, y, 1f, 1f, capeWidth, size, 10, 16, 64, 32, Fx.alpha(TEXT, alpha));
				else
					Type.REGULAR.centered(graphics, font, "Own", x + size / 2, y + size / 2 - 4, Fx.alpha(MUTED, alpha));
			}, () -> !Cosmetics.owns(cape.id()), () -> Cosmetics.tryCape = cape.id()));
		}
		return tiles;
	}

	private List<Tile> hatTiles() {
		var tiles = new ArrayList<Tile>();
		for (var wearable : Cosmetics.HATS) {
			tiles.add(new Tile(wearable.id(), wearable.name(), "Hat", () -> wearable.id().equals(Cosmetics.hatId), () -> Cosmetics.hatId = wearable.id(), (graphics, x, y, size, alpha) -> {
				var preview = wearable.id().equals(Cosmetics.NOTHING) ? null : ModelPreview.of(wearable.id());
				if (preview != null)
					graphics.blit(RenderPipelines.GUI_TEXTURED, preview, x, y, 0f, 0f, size, size, ModelPreview.SIZE, ModelPreview.SIZE, ModelPreview.SIZE, ModelPreview.SIZE, Fx.alpha(TEXT, alpha));
				else
					nothingIcon(graphics, x, y, size, alpha);
			}, () -> !Cosmetics.owns(wearable.id()), () -> Cosmetics.tryHat = wearable.id()));
		}
		return tiles;
	}

	private List<Tile> auraTiles() {
		var tiles = new ArrayList<Tile>();
		for (int i = 0; i < Cosmetics.AURAS.size(); i++) {
			int index = i;
			var aura = Cosmetics.AURAS.get(i);
			tiles.add(new Tile(Cosmetics.auraId(aura), aura.name(), "Aura", () -> Cosmetics.aura == index, () -> Cosmetics.aura = index, (graphics, x, y, size, alpha) -> {
				if (aura.particle() == null) {
					nothingIcon(graphics, x, y, size, alpha);
					return;
				}
				int icon = size * 3 / 4;
				graphics.blit(RenderPipelines.GUI_TEXTURED, aura.icon(), x + (size - icon) / 2, y + (size - icon) / 2, 0f, 0f, icon, icon, aura.iconSize(), aura.iconSize(), aura.iconSize(), aura.iconSize(), Fx.alpha(TEXT, alpha));
			}, () -> !Cosmetics.owns(Cosmetics.auraId(aura)), () -> {
			}));
		}
		return tiles;
	}

	private void lockIcon(GuiGraphicsExtractor graphics, int x, int y, float alpha) {
		Draw.outline(graphics, x + 1.5f, y, 5, 7, 2.5f, Fx.alpha(TEXT, alpha));
		Draw.roundRect(graphics, x, y + 3.5f, 8, 5.5f, 1.5f, Fx.alpha(TEXT, alpha));
	}

	private void nothingIcon(GuiGraphicsExtractor graphics, int x, int y, int size, float alpha) {
		int ring = size / 2;
		Draw.outline(graphics, x + (size - ring) / 2f, y + (size - ring) / 2f, ring, ring, ring / 2f, Fx.alpha(MUTED, alpha));
	}

	private int tileColumns(int w) {
		return Math.max(1, (w - 16 + TILE_GAP) / (TILE_MIN_WIDTH + TILE_GAP));
	}

	private int tileWidth(int w) {
		int columns = tileColumns(w);
		return (w - 16 - TILE_GAP * (columns - 1)) / columns;
	}

	private int tileX(int rowX, int w, int index) {
		return rowX + 8 + index % tileColumns(w) * (tileWidth(w) + TILE_GAP);
	}

	private int tileY(int rowY, int w, int index) {
		return rowY + TILE_TOP + index / tileColumns(w) * (TILE_HEIGHT + TILE_GAP);
	}

	private String fitted(String text, int width) {
		if (Type.REGULAR.width(font, text) <= width)
			return text;
		var shorter = text;
		while (!shorter.isEmpty() && Type.REGULAR.width(font, shorter + "...") > width)
			shorter = shorter.substring(0, shorter.length() - 1);
		return shorter + "...";
	}

	private abstract static class TileRow extends Row {
		final List<Tile> tiles;

		TileRow(String id, int height, List<Tile> tiles) {
			super(id, height);
			this.tiles = tiles;
		}
	}

	private Row tileGrid(List<Tile> tiles) {
		return tileGrid("grid", tiles);
	}

	private Row tileGrid(String id, List<Tile> tiles) {
		int w = panelRight() - panelLeft() - 14;
		int rows = (tiles.size() + tileColumns(w) - 1) / tileColumns(w);
		return new TileRow(id, TILE_TOP + rows * (TILE_HEIGHT + TILE_GAP), tiles) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				int tileWidth = tileWidth(w);
				int accent = ZyroxTheme.accent();
				for (int i = 0; i < tiles.size(); i++) {
					var tile = tiles.get(i);
					int tx = tileX(x, w, i);
					int ty = tileY(y, w, i);
					String key = id + ":" + tile.key();
					boolean hovered = hover(key, inside(mouseX, mouseY, tx, ty, tileWidth, TILE_HEIGHT) && mouseY >= rowsTop() && mouseY < rowsBottom());
					if (hovered)
						hoveredTile = tile;
					float lift = hoverAmount(key, hovered);
					boolean chosen = tile.chosen().getAsBoolean();
					float pick = Fx.approach(toggleAnim.getOrDefault(key, chosen ? 1f : 0f), chosen ? 1f : 0f, dt, 12f);
					toggleAnim.put(key, pick);
					boolean locked = tile.locked().getAsBoolean();
					float rise = lift * 2f;
					if (pick > 0.01f)
						Draw.glow(graphics, tx, ty - rise, tileWidth, TILE_HEIGHT, 6f, 5f, Fx.alpha(accent, 0.35f * pick * alpha));
					Draw.roundRect(graphics, tx, ty - rise, tileWidth, TILE_HEIGHT, 6f, Fx.alpha(Fx.mix(Fx.mix(0x14FFFFFF, 0x30FFFFFF, lift), Fx.alpha(accent, 0.22f), pick * 0.6f), alpha));
					tile.preview().draw(graphics, tx + (tileWidth - TILE_PREVIEW) / 2, (int) (ty + 3 - rise), TILE_PREVIEW, locked ? alpha * (0.3f + lift * 0.4f) : alpha);
					Type.REGULAR.centered(graphics, font, fitted(tile.name(), tileWidth - 4), tx + tileWidth / 2, (int) (ty + TILE_PREVIEW + 6 - rise), Fx.alpha(chosen || lift > 0.5f ? TEXT : MUTED, locked ? alpha * 0.6f : alpha));
					if (locked)
						lockIcon(graphics, tx + tileWidth - 12, (int) (ty + 4 - rise), alpha);
					if (tile.cosmetic() && Cosmetics.FAVORITES.contains(tile.key()))
						Type.REGULAR.draw(graphics, font, "\u2605", tx + 4, (int) (ty + 3 - rise), Fx.alpha(0xFFFFD25A, alpha));
					if (!tile.cosmetic())
						Draw.roundRect(graphics, tx + tileWidth - 9, ty + 4 - rise, 5, 5, 2.5f, Fx.alpha(Fx.mix(0x40FFFFFF, accent, pick), alpha));
					if (pick > 0.01f)
						Draw.outline(graphics, tx - 1, ty - 1 - rise, tileWidth + 2, TILE_HEIGHT + 2, 7f, Fx.alpha(accent, alpha * pick));
				}
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				int tileWidth = tileWidth(w);
				for (int i = 0; i < tiles.size(); i++) {
					if (inside(mouseX, clickY, tileX(x, w, i), tileY(y, w, i), tileWidth, TILE_HEIGHT)) {
						var tile = tiles.get(i);
						if (tile.locked().getAsBoolean()) {
							Sounds.LOCKED.play();
							return;
						}
						tile.choose().run();
						if (tile.cosmetic())
							Sounds.EQUIP.play();
						return;
					}
				}
			}
		};
	}

	private Row syncStatus() {
		return new Row("sync", 14) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				var text = switch (CloudCosmetics.status()) {
					case NOT_SET_UP -> "Zyrox server offline, only you see them.";
					case WAITING -> "Syncs once you are in a world.";
					case SYNCING -> "Syncing...";
					case SYNCED -> "Other Zyrox players can see these.";
					case FAILED -> "Sync failed, only you see them.";
				};
				Type.REGULAR.draw(graphics, font, fitted(text, w - 16), x + 8, y + 3, Fx.alpha(MUTED, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
			}
		};
	}

	private Row nowPlayingCard() {
		return new Row("track", 44) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				Draw.roundRect(graphics, x, y, w, height, ROW_RADIUS, Fx.alpha(HOVER, alpha));
				var track = MusicManager.track();
				if (track == null) {
					Type.REGULAR.draw(graphics, font, MusicManager.onlySpotify ? "Spotify is not playing anything" : "Nothing is playing", x + 8, y + 18, Fx.alpha(MUTED, alpha));
					return;
				}
				int available = w - 16;
				Type.SEMIBOLD.draw(graphics, font, NowPlayingModule.shorten(font, Type.SEMIBOLD, track.title(), available), x + 8, y + 7, Fx.alpha(TEXT, alpha));
				Type.REGULAR.draw(graphics, font, NowPlayingModule.shorten(font, Type.REGULAR, track.artist(), available), x + 8, y + 19, Fx.alpha(MUTED, alpha));
				Draw.roundRect(graphics, x + 8, y + 34, available, 2, 1f, Fx.alpha(0x40FFFFFF, alpha));
				if (track.durationMs() > 0) {
					float progress = Math.clamp((float) MusicManager.positionMs() / track.durationMs(), 0f, 1f);
					Draw.roundRect(graphics, x + 8, y + 34, Math.max(2f, available * progress), 2, 1f, Fx.alpha(ZyroxTheme.accent(), alpha));
				}
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
			}
		};
	}

	private Row playerControls() {
		String[] labels = { "Previous", "Play / Pause", "Next" };
		var controls = new WindowsMediaSession.Control[] { WindowsMediaSession.Control.PREVIOUS, WindowsMediaSession.Control.TOGGLE, WindowsMediaSession.Control.NEXT };
		return new Row("controls", 24) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				int width = (w - 8) / labels.length;
				for (int i = 0; i < labels.length; i++) {
					int bx = x + i * (width + 4);
					boolean hovered = inside(mouseX, mouseY, bx, y, width, height);
					int base = i == 1 ? ZyroxTheme.accent() : 0x1EFFFFFF;
					Draw.roundRect(graphics, bx, y, width, height, ROW_RADIUS, Fx.alpha(Fx.mix(base, TEXT, hovered ? 0.12f : 0f), alpha));
					Type.SEMIBOLD.centered(graphics, font, labels[i], bx + width / 2, y + 8, Fx.alpha(TEXT, alpha));
				}
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				int index = Math.clamp((int) (mouseX - x) / ((w - 8) / labels.length + 4), 0, labels.length - 1);
				MusicManager.send(controls[index]);
				playClick(1.2f);
			}
		};
	}

	private Row lyricsView() {
		String[] labels = { "2D on screen", "3D in the sky" };
		return new Row("lyrics-view", 42) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				Type.SEMIBOLD.draw(graphics, font, "Lyrics view", x + 8, y + 4, Fx.alpha(TEXT, alpha));
				int top = y + 16;
				int inner = w - 16;
				Draw.roundRect(graphics, x + 8, top, inner, 22, ROW_RADIUS, Fx.alpha(0x1EFFFFFF, alpha));
				viewSlide = Fx.approach(viewSlide, MusicManager.skyLyrics ? 1f : 0f, dt, 16f);
				Draw.roundRect(graphics, x + 10 + viewSlide * (inner / 2f), top + 2, inner / 2f - 4, 18, ROW_RADIUS - 2, Fx.alpha(ZyroxTheme.accent(), alpha));
				for (int i = 0; i < labels.length; i++)
					Type.SEMIBOLD.centered(graphics, font, labels[i], x + 8 + inner / 4 + i * inner / 2, top + 7, Fx.alpha(TEXT, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				MusicManager.skyLyrics = mouseX >= x + w / 2.0;
				playClick(MusicManager.skyLyrics ? 1.5f : 1.1f);
			}
		};
	}

	private List<Row> musicRows() {
		var offsets = new ArrayList<String>();
		for (int i = -LYRICS_OFFSET_STEPS; i <= LYRICS_OFFSET_STEPS; i++)
			offsets.add(i == 0 ? "On time" : "%+.2f s".formatted(i * LYRICS_OFFSET_MS / 1000f));
		var rows = new ArrayList<Row>();
		rows.add(nowPlayingCard());
		rows.add(playerControls());
		rows.add(moduleToggle(Modules.LYRICS));
		rows.add(lyricsView());
		rows.add(select("sky-size", "Sky lyrics size", () -> SkyLyrics.SIZES, () -> Math.clamp(MusicManager.skySize, 0, SkyLyrics.SIZES.size() - 1), value -> MusicManager.skySize = value));
		rows.add(moduleToggle(Modules.NOW_PLAYING));
		rows.add(toggle("karaoke", "Karaoke fill", "Sung words light up", () -> MusicManager.karaoke, on -> MusicManager.karaoke = on));
		rows.add(toggle("only-spotify", "Only Spotify", "Off follows any media app", () -> MusicManager.onlySpotify, on -> MusicManager.onlySpotify = on));
		rows.add(select("offset", "Lyrics timing", () -> offsets, () -> Math.clamp(MusicManager.offsetMs / LYRICS_OFFSET_MS + LYRICS_OFFSET_STEPS, 0, LYRICS_OFFSET_STEPS * 2), value -> MusicManager.offsetMs = (value - LYRICS_OFFSET_STEPS) * LYRICS_OFFSET_MS));
		rows.add(note("Lyrics from lrclib.net, which receives the song name."));
		rows.add(note("Based on Spotify Lyrics by Alon Goldberger (MIT)."));
		return rows;
	}

	private List<Row> utilityRows() {
		var rows = new ArrayList<Row>();
		for (var module : Modules.ALL)
			if (module.category == Module.Category.UTILITY)
				rows.add(moduleToggle(module));
		rows.add(select("utility-motion-blur", "Motion blur", () -> Effects.MOTION_BLUR, () -> Effects.motionBlur, value -> Effects.motionBlur = value));
		return rows;
	}

	private List<Row> settingRows() {
		return List.of(
			heading("look", "Look"),
			swatches(),
			toggle("cinematic", "Camera move", "Swing out in front of you", () -> ZyroxTheme.cinematic, on -> {
				ZyroxTheme.cinematic = on;
				CameraRig.setActive(on);
			}),
			heading("effects", "Effects"),
			select("menu-blur", "Menu blur", () -> Effects.MENU_BLUR, () -> Effects.menuBlur, value -> Effects.menuBlur = value),
			select("motion-blur", "Motion blur", () -> Effects.MOTION_BLUR, () -> Effects.motionBlur, value -> Effects.motionBlur = value),
			note("Motion blur smooths fast camera turns in game."),
			heading("sound", "Sound"),
			select("menu-sounds", "Menu sounds", () -> Sounds.VOLUMES, () -> Sounds.volume, value -> Sounds.volume = value),
			toggle("classic-sounds", "Classic clicks", "Use the Minecraft button sound", () -> Sounds.classic, on -> Sounds.classic = on));
	}

	private List<Row> pvpRows() {
		var tiles = new ArrayList<Tile>();
		for (var module : Modules.ALL) {
			if (module instanceof PvpModule pvp)
				tiles.add(moduleTile(pvp, pvp.icon, pvp.iconSize));
			else if (module.category == Module.Category.PVP)
				tiles.add(moduleTile(module, icon("pvp/" + module.id), ICON_SIZE));
		}
		var rows = new ArrayList<Row>();
		long on = tiles.stream().filter(tile -> tile.chosen().getAsBoolean()).count();
		if (!searching())
			rows.add(countHeading("pvp-count", "Fight helpers", on + " on"));
		rows.add(tileGrid(tiles));
		rows.add(button("pvp-edit", "Edit HUD layout", this::enterEdit));
		rows.add(note("Click a tile to turn it on or off. Type to search."));
		return rows;
	}

	private List<Row> waypointRows() {
		var rows = new ArrayList<Row>();
		rows.add(moduleToggle(Modules.WAYPOINTS));
		if (!Waypoints.canAdd(minecraft)) {
			rows.add(note("Join a world to use waypoints."));
			return rows;
		}
		rows.add(button("add-waypoint", "Add waypoint here", () -> minecraft.gui.setScreen(new NameScreen(this, "New waypoint", Waypoints.nextName(minecraft), name -> Waypoints.add(minecraft, name)))));
		var list = Waypoints.here(minecraft);
		for (int i = 0; i < list.size(); i++)
			rows.add(waypointRow("waypoint-" + i, list.get(i)));
		rows.add(note("Press B in game for a new waypoint."));
		return rows;
	}

	private final Map<UUID, Supplier<PlayerSkin>> faces = new HashMap<>();

	private Row heading(String id, String text) {
		return new Row(id, 18) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				Type.SEMIBOLD.draw(graphics, font, text, x + 8, y + 7, Fx.alpha(TEXT, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
			}
		};
	}

	private void face(GuiGraphicsExtractor graphics, UUID id, String name, int x, int y, int size, float alpha) {
		var skin = faces.computeIfAbsent(id, key -> minecraft.getSkinManager().createLookup(new GameProfile(key, name.isEmpty() ? "Player" : name), false));
		PlayerFaceExtractor.extractRenderState(graphics, skin.get(), x, y, size, Fx.alpha(TEXT, alpha));
	}

	private List<Row> friendRows() {
		Friends.watch();
		var rows = new ArrayList<Row>();
		if (!Friends.available()) {
			rows.add(note("The Zyrox server is offline right now."));
			return rows;
		}
		rows.add(button("add-friend", "Add friend", () -> minecraft.gui.setScreen(new NameScreen(this, "Add a friend by Minecraft name", "", name -> Friends.add(minecraft, name)))));
		if (!Friends.message().isEmpty())
			rows.add(note(Friends.message()));
		var incoming = Friends.incoming();
		if (!incoming.isEmpty()) {
			rows.add(heading("requests", "Requests (" + incoming.size() + ")"));
			for (int i = 0; i < incoming.size(); i++)
				rows.add(requestRow("request-" + i, incoming.get(i)));
		}
		var friends = Friends.friends();
		long online = friends.stream().filter(Friends.Friend::online).count();
		rows.add(heading("friends-heading", "Friends (" + online + " of " + friends.size() + " online)"));
		if (!Friends.loaded())
			rows.add(note(Friends.failed() ? "Could not load your friends." : "Loading..."));
		else if (friends.isEmpty())
			rows.add(note("No friends yet. Add someone who uses Zyrox."));
		for (int i = 0; i < friends.size(); i++)
			rows.add(friendRow("friend-" + i, friends.get(i)));
		var outgoing = Friends.outgoing();
		if (!outgoing.isEmpty()) {
			rows.add(heading("sent", "Sent requests"));
			for (int i = 0; i < outgoing.size(); i++)
				rows.add(sentRow("sent-" + i, outgoing.get(i)));
		}
		rows.add(toggle("appear-online", "Appear online", "Friends see when you play", () -> Friends.appearOnline, on -> Friends.appearOnline = on));
		rows.add(toggle("share-server", "Share my server", "Friends can join you", () -> Friends.shareServer, on -> Friends.shareServer = on));
		return rows;
	}

	private Row requestRow(String id, Friends.Request request) {
		return new Row(id, ROW_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				if (inside(mouseX, mouseY, x, y, w, height))
					Draw.roundRect(graphics, x, y, w, height, ROW_RADIUS, Fx.alpha(HOVER, alpha));
				face(graphics, request.id(), request.name(), x + 7, y + 7, 16, alpha);
				var name = request.name().isEmpty() ? "Unknown player" : request.name();
				Type.SEMIBOLD.draw(graphics, font, fitted(name, w - FRIEND_BUTTONS - 34), x + 29, y + 11, Fx.alpha(TEXT, alpha));
				smallButton(graphics, x + w - FRIEND_BUTTONS, y + 7, FRIEND_BUTTON, "Accept", ZyroxTheme.accent(), mouseX, mouseY, alpha);
				smallButton(graphics, x + w - FRIEND_BUTTON - 6, y + 7, FRIEND_BUTTON, "Decline", 0x1EFFFFFF, mouseX, mouseY, alpha);
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				if (mouseX >= x + w - FRIEND_BUTTONS && mouseX < x + w - FRIEND_BUTTONS + FRIEND_BUTTON)
					Friends.accept(minecraft, request);
				else if (mouseX >= x + w - FRIEND_BUTTON - 6 && mouseX < x + w - 6)
					Friends.remove(minecraft, request.id(), request.name());
				else
					return;
				playClick(1.2f);
			}
		};
	}

	private Row friendRow(String id, Friends.Friend friend) {
		return new Row(id, ROW_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				if (inside(mouseX, mouseY, x, y, w, height))
					Draw.roundRect(graphics, x, y, w, height, ROW_RADIUS, Fx.alpha(HOVER, alpha));
				face(graphics, friend.id(), friend.name(), x + 7, y + 7, 16, friend.online() ? alpha : alpha * 0.45f);
				Draw.roundRect(graphics, x + 19, y + 19, 6, 6, 3f, Fx.alpha(friend.online() ? ONLINE : 0xFF6F747E, alpha));
				boolean join = Friends.canJoin(minecraft, friend);
				int textWidth = w - (join ? FRIEND_BUTTONS : FRIEND_BUTTON) - 40;
				var name = friend.name().isEmpty() ? "Unknown player" : friend.name();
				Type.SEMIBOLD.draw(graphics, font, fitted(name, textWidth), x + 31, y + 6, Fx.alpha(friend.online() ? TEXT : MUTED, alpha));
				var status = !friend.online() ? "Offline" : friend.server().isEmpty() ? "Online" : "On " + friend.server();
				Type.REGULAR.draw(graphics, font, fitted(status, textWidth), x + 31, y + 17, Fx.alpha(MUTED, alpha));
				if (join)
					smallButton(graphics, x + w - FRIEND_BUTTONS, y + 7, FRIEND_BUTTON, "Join", ZyroxTheme.accent(), mouseX, mouseY, alpha);
				Type.REGULAR.centered(graphics, font, "x", x + w - 12, y + 11, Fx.alpha(0xFFFF6B6B, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				if (mouseX >= x + w - 22) {
					Friends.remove(minecraft, friend.id(), friend.name());
				} else if (Friends.canJoin(minecraft, friend) && mouseX >= x + w - FRIEND_BUTTONS && mouseX < x + w - FRIEND_BUTTONS + FRIEND_BUTTON) {
					onClose();
					Friends.join(minecraft, friend);
				} else {
					return;
				}
				playClick(1.2f);
			}
		};
	}

	private Row sentRow(String id, Friends.Request request) {
		return new Row(id, ROW_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				face(graphics, request.id(), request.name(), x + 7, y + 7, 16, alpha * 0.6f);
				var name = request.name().isEmpty() ? "Waiting for an answer" : request.name();
				Type.REGULAR.draw(graphics, font, fitted(name, w - FRIEND_BUTTON - 40), x + 29, y + 11, Fx.alpha(MUTED, alpha));
				smallButton(graphics, x + w - FRIEND_BUTTON - 6, y + 7, FRIEND_BUTTON, "Cancel", 0x1EFFFFFF, mouseX, mouseY, alpha);
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				if (mouseX >= x + w - FRIEND_BUTTON - 6 && mouseX < x + w - 6) {
					Friends.remove(minecraft, request.id(), request.name());
					playClick(1.1f);
				}
			}
		};
	}

	private List<Row> staffRows() {
		Roles.watch();
		var rows = new ArrayList<Row>();
		var own = Roles.own();
		rows.add(heading("staff-role", own == null ? "You are not in the team" : "You are " + own.label));
		if (Roles.isOwner()) {
			rows.add(button("add-staff", "Add staff", () -> minecraft.gui.setScreen(new NameScreen(this, "Add staff by Minecraft name", "", name -> Roles.set(minecraft, name, Roles.Role.STAFF, null)))));
			rows.add(button("add-tester", "Add tester", () -> minecraft.gui.setScreen(new NameScreen(this, "Add a tester by Minecraft name", "", name -> Roles.set(minecraft, name, Roles.Role.TESTER, null)))));
		}
		if (!Roles.message().isEmpty())
			rows.add(note(Roles.message()));
		var team = Roles.team();
		rows.add(heading("team", "Team (" + team.size() + ")"));
		for (int i = 0; i < team.size(); i++)
			rows.add(memberRow("member-" + i, team.get(i)));
		if (Roles.isOwner())
			rows.add(note("In chat: /zyrox role add <name> staff"));
		return rows;
	}

	private Row memberRow(String id, Roles.Member member) {
		boolean removable = Roles.isOwner() && member.role() != Roles.Role.OWNER && !member.name().isEmpty();
		return new Row(id, ROW_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				if (inside(mouseX, mouseY, x, y, w, height))
					Draw.roundRect(graphics, x, y, w, height, ROW_RADIUS, Fx.alpha(HOVER, alpha));
				face(graphics, member.id(), member.name(), x + 7, y + 7, 16, alpha);
				int textWidth = w - 60;
				Type.SEMIBOLD.draw(graphics, font, fitted(member.name().isEmpty() ? "Unknown player" : member.name(), textWidth), x + 31, y + 6, Fx.alpha(TEXT, alpha));
				Type.REGULAR.draw(graphics, font, member.role().label, x + 31, y + 17, Fx.alpha(member.role().color, alpha));
				if (removable)
					Type.REGULAR.centered(graphics, font, "x", x + w - 12, y + 11, Fx.alpha(0xFFFF6B6B, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				if (removable && mouseX >= x + w - 22) {
					Roles.set(minecraft, member.name(), null, null);
					playClick(1.2f);
				}
			}
		};
	}

	private void smallButton(GuiGraphicsExtractor graphics, int x, int y, int w, String label, int color, int mouseX, int mouseY, float alpha) {
		boolean hovered = inside(mouseX, mouseY, x, y, w, 16);
		Draw.roundRect(graphics, x, y, w, 16, 5f, Fx.alpha(Fx.mix(color, TEXT, hovered ? 0.15f : 0f), alpha));
		Type.SEMIBOLD.centered(graphics, font, label, x + w / 2, y + 4, Fx.alpha(TEXT, alpha));
	}

	private Row waypointRow(String id, Waypoints.Waypoint waypoint) {
		return new Row(id, ROW_HEIGHT) {
			@Override
			void draw(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, float alpha) {
				if (inside(mouseX, mouseY, x, y, w, height))
					Draw.roundRect(graphics, x, y, w, height, ROW_RADIUS, Fx.alpha(HOVER, alpha));
				Draw.roundRect(graphics, x + 7, y + 7, 6, 6, 3f, Fx.alpha(waypoint.color, waypoint.visible ? alpha : alpha * 0.35f));
				int textWidth = w - WAYPOINT_BUTTONS - 18;
				Type.SEMIBOLD.draw(graphics, font, fitted(waypoint.name, textWidth), x + 17, y + 6, Fx.alpha(waypoint.visible ? TEXT : MUTED, alpha));
				Type.REGULAR.draw(graphics, font, fitted("%d, %d, %d  %d m".formatted(waypoint.x, waypoint.y, waypoint.z, Waypoints.distance(minecraft, waypoint)), textWidth), x + 17, y + 17, Fx.alpha(MUTED, alpha));
				pill(graphics, x + w - WAYPOINT_BUTTONS, y + (height - SWITCH_HEIGHT) / 2, waypoint.visible ? 1f : 0f, ZyroxTheme.accent(), alpha);
				Type.REGULAR.draw(graphics, font, "Edit", x + w - WAYPOINT_EDIT, y + 11, Fx.alpha(TEXT, alpha));
				Type.REGULAR.centered(graphics, font, "x", x + w - WAYPOINT_DELETE / 2, y + 11, Fx.alpha(0xFFFF6B6B, alpha));
			}

			@Override
			void click(double mouseX, int x, int y, int w) {
				double from = x + w - mouseX;
				if (from <= WAYPOINT_DELETE) {
					Waypoints.remove(minecraft, waypoint);
				} else if (from <= WAYPOINT_EDIT) {
					minecraft.gui.setScreen(new NameScreen(ZyroxScreen.this, "Rename waypoint", waypoint.name, name -> {
						waypoint.name = name;
						Waypoints.save();
					}));
				} else if (from <= WAYPOINT_BUTTONS) {
					waypoint.visible = !waypoint.visible;
					Waypoints.save();
				} else {
					return;
				}
				playClick(1.2f);
			}
		};
	}

	private int cardHeight() {
		return (int) Math.min(height * 0.68f, 260f);
	}

	private int cardWidth() {
		return (int) (cardHeight() * 0.68f);
	}

	private float carouselX() {
		return width * CAROUSEL_X - Fx.easeInOutCubic(panelAmount) * width * 0.35f;
	}

	private float[] cardRect(float rel, float centerX) {
		float side = Math.clamp(rel, -1f, 1f);
		float amount = Math.abs(side);
		float excess = Math.abs(rel) - amount;
		float scale = 1f - (1f - SIDE_SCALE) * amount;
		float w = cardWidth() * scale * (float) Math.cos(side * MAX_TILT);
		float h = cardHeight() * scale;
		float x = centerX + side * cardWidth() * 0.86f + Math.signum(rel) * excess * cardWidth() * 0.35f;
		return new float[] { x - w / 2f, carouselY() - h / 2f, w, h };
	}

	private float[] panelRect() {
		return new float[] { panelLeft(), panelTop(), panelRight() - panelLeft(), panelBottom() - panelTop() };
	}

	private int carouselY() {
		return height / 2;
	}

	private float relative(int index) {
		float count = pages.size();
		float rel = (index - position) % count;
		if (rel < -count / 2)
			rel += count;
		else if (rel >= count / 2)
			rel -= count;
		return rel;
	}

	private int panelLeft() {
		return Math.max(PANEL_MARGIN, Math.min((int) (width * 0.4f), width - PANEL_MARGIN - PANEL_MIN_WIDTH));
	}

	private int panelRight() {
		return width - PANEL_MARGIN;
	}

	private int panelTop() {
		return PANEL_MARGIN;
	}

	private int panelBottom() {
		return height - PANEL_MARGIN;
	}

	private int rowsTop() {
		return panelTop() + PANEL_HEADER;
	}

	private int rowsBottom() {
		return panelBottom() - INFO_HEIGHT - 4;
	}

	private int searchLeft() {
		return panelRight() - SEARCH_WIDTH - 10;
	}

	private int searchTop() {
		return panelTop() + (PANEL_HEADER - SEARCH_HEIGHT) / 2;
	}

	private boolean searching() {
		return !search.isBlank();
	}

	private boolean matches(String text) {
		return text != null && text.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
	}

	private float hoverAmount(String key, boolean hovered) {
		float value = Fx.approach(hoverAnim.getOrDefault(key, 0f), hovered ? 1f : 0f, dt, 14f);
		hoverAnim.put(key, value);
		return value;
	}

	private List<Row> shownRows() {
		var rows = shownPanel.rows().get();
		if (!searching())
			return rows;
		var out = new ArrayList<Row>();
		boolean found = false;
		for (var row : rows) {
			if (row instanceof TileRow grid) {
				var hits = grid.tiles.stream().filter(tile -> matches(tile.name()) || matches(tile.detail())).toList();
				if (!hits.isEmpty()) {
					out.add(tileGrid(grid.id, hits));
					found = true;
				}
			} else if (row.label != null && matches(row.label)) {
				out.add(row);
				found = true;
			} else if (row.keep) {
				out.add(row);
			}
		}
		if (!found)
			out.add(note("Nothing found for \"" + search.trim() + "\""));
		return out;
	}

	private int rowsHeight(List<Row> rows) {
		int total = 0;
		for (var row : rows)
			total += row.height + ROW_GAP;
		return total - ROW_GAP;
	}

	private int maxPanelScroll() {
		if (panel == null)
			return 0;
		return Math.max(0, rowsHeight(shownRows()) - (rowsBottom() - rowsTop()));
	}

	private int doneLeft() {
		return (width - DONE_WIDTH) / 2;
	}

	private int doneTop() {
		return 30;
	}

	public List<String> pageIds() {
		return pages.stream().map(Page::id).toList();
	}

	public String selectedId() {
		return pages.get(selected).id();
	}

	public boolean isPanelOpen() {
		return panel != null;
	}

	public boolean isEditing() {
		return editing;
	}

	public int[] centerCard() {
		return new int[] { (int) (width * CAROUSEL_X), carouselY() };
	}

	public int[] searchCenter() {
		return new int[] { searchLeft() + SEARCH_WIDTH / 2, searchTop() + SEARCH_HEIGHT / 2 };
	}

	public int[] doneCenter() {
		return new int[] { doneLeft() + DONE_WIDTH / 2, doneTop() + DONE_HEIGHT / 2 };
	}

	public int[] cosmeticTabCenter(int index) {
		int x = panelLeft() + 6;
		int w = panelRight() - panelLeft() - 14;
		return new int[] { tabX(x, w, COSMETIC_TABS.size(), index) + tabWidth(w, COSMETIC_TABS.size()) / 2, rowCenter("tabs")[1] };
	}

	public int[] hudTabCenter(int index) {
		int x = panelLeft() + 6;
		int w = panelRight() - panelLeft() - 14;
		return new int[] { tabX(x, w, HUD_TABS.size(), index) + tabWidth(w, HUD_TABS.size()) / 2, rowCenter("hud-tabs")[1] };
	}

	public int[] tileCenter(int index) {
		int x = panelLeft() + 6;
		int w = panelRight() - panelLeft() - 14;
		int rowHeight = shownRows().stream().filter(entry -> entry.id.equals("grid")).findFirst().orElseThrow().height;
		int rowY = rowCenter("grid")[1] - rowHeight / 2;
		return new int[] { tileX(x, w, index) + tileWidth(w) / 2, tileY(rowY, w, index) + TILE_HEIGHT / 2 };
	}

	public int[] tileNamed(String name) {
		int x = panelLeft() + 6;
		int w = panelRight() - panelLeft() - 14;
		int y = rowsTop() - (int) panelScroll;
		for (var row : shownRows()) {
			if (row instanceof TileRow grid) {
				for (int i = 0; i < grid.tiles.size(); i++)
					if (grid.tiles.get(i).name().equals(name))
						return new int[] { tileX(x, w, i) + tileWidth(w) / 2, tileY(y, w, i) + TILE_HEIGHT / 2 };
			}
			y += row.height + ROW_GAP;
		}
		throw new IllegalArgumentException(name);
	}

	public void revealTile(String name) {
		reveal(tileNamed(name)[1] - TILE_HEIGHT / 2, TILE_HEIGHT);
	}

	public void revealRow(String id) {
		reveal(rowCenter(id)[1] - 12, 24);
	}

	private void reveal(int top, int size) {
		if (top >= rowsTop() && top + size <= rowsBottom())
			return;
		panelScroll = Math.clamp(panelScroll + top - rowsTop() - 8, 0, maxPanelScroll());
		panelScrollTarget = panelScroll;
	}

	public boolean hasRow(String id) {
		return shownRows().stream().anyMatch(row -> row.id.equals(id));
	}

	public int[] rowCenterAt(String id, float across) {
		int[] center = rowCenter(id);
		center[0] = panelLeft() + (int) ((panelRight() - panelLeft()) * across);
		return center;
	}

	public int[] rowCenter(String id) {
		int y = rowsTop() - (int) panelScroll;
		for (var row : shownRows()) {
			if (row.id.equals(id))
				return new int[] { (panelLeft() + panelRight()) / 2, y + row.height / 2 };
			y += row.height + ROW_GAP;
		}
		throw new IllegalArgumentException(id);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (minecraft.level == null)
			super.extractBackground(graphics, mouseX, mouseY, delta);
		long now = System.nanoTime();
		dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		age += dt;
		open = Math.min(1f, open + dt * OPEN_SPEED);
		if (isClosing) {
			closing = Math.min(1f, closing + dt * CLOSE_SPEED);
			if (closing >= 1f && minecraft.gui.screen() == this) {
				if (parent != null)
					minecraft.gui.setScreen(parent);
				else
					super.onClose();
				return;
			}
		}
		position = Fx.approach(position, closestTarget(), dt, SCROLL_SPEED);
		float morphTarget = panel != null ? 1f : 0f;
		panelAmount = morphTarget > panelAmount ? Math.min(morphTarget, panelAmount + dt * MORPH_SPEED) : Math.max(morphTarget, panelAmount - dt * MORPH_SPEED * 1.3f);
		if (panel != null)
			shownPanel = panel;
		if (shownPanel != null) {
			var key = shownPanel.id() + "/" + cosmeticTab + "/" + hudTab + "/" + legacyCapes + "/" + searching();
			if (!key.equals(contentKey)) {
				contentKey = key;
				contentAnim = 0f;
			}
		}
		contentAnim = Math.min(1f, contentAnim + dt * CONTENT_SPEED);
		panelScroll = Fx.approach(panelScroll, panelScrollTarget, dt, 16f);
		editAmount = Fx.approach(editAmount, editing ? 1f : 0f, dt, 12f);
		boolean blurs = panel != null && !panel.id().equals("cosmetics") && !editing && !isClosing;
		Effects.menuAmount = Fx.approach(Effects.menuAmount, blurs ? Fx.easeInOutCubic(panelAmount) : 0f, dt, 10f);
		if (!editing)
			CameraRig.look((float) mouseX / width, (float) mouseY / height);

		float fade = Fx.easeOutCubic(open) * (1 - closing) * (1 - editAmount);
		if (ZyroxTheme.cinematic) {
			float start = width * 0.3f;
			float step = (width - start) / SCRIM_STRIPS;
			for (int i = 0; i < SCRIM_STRIPS; i++) {
				float t = (float) (i + 1) / SCRIM_STRIPS;
				int color = Fx.alpha(0x98000000, t * t * fade);
				Fx.quad(graphics, start + i * step, 0, start + (i + 1) * step, height, color, color);
			}
		} else {
			graphics.fill(0, 0, width, height, Fx.alpha(0xB0000000, fade));
		}
		if (editAmount > 0.01f)
			grid(graphics);
	}

	private float closestTarget() {
		float count = pages.size();
		float delta = (selected - position) % count;
		if (delta < -count / 2)
			delta += count;
		else if (delta >= count / 2)
			delta -= count;
		return position + delta;
	}

	private void grid(GuiGraphicsExtractor graphics) {
		int color = Fx.alpha(0x26FFFFFF, editAmount);
		for (int x = 0; x < width; x += GRID)
			graphics.fill(x, 0, x + 1, height, color);
		for (int y = 0; y < height; y += GRID)
			graphics.fill(0, y, width, y + 1, color);
		int center = Fx.alpha(ZyroxTheme.accent(), 0.5f * editAmount);
		graphics.fill(width / 2, 0, width / 2 + 1, height, center);
		graphics.fill(0, height / 2, width, height / 2 + 1, center);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (editAmount > 0.01f) {
			graphics.nextStratum();
			editOverlay(graphics, mouseX, mouseY);
		}
		float fade = Fx.easeOutCubic(open) * (1 - closing) * (1 - editAmount);
		if (fade < 0.02f)
			return;
		if (preview != null)
			preview.extractRenderState(graphics, mouseX, mouseY, delta);
		int menuX = editing ? -1000 : mouseX;
		int menuY = editing ? -1000 : mouseY;
		hoverKey = null;
		hoveredTile = null;
		float carouselAlpha = fade * (1 - Fx.easeInOutCubic(Math.min(1f, panelAmount * 1.4f)));
		if (carouselAlpha > 0.02f)
			carousel(graphics, panel == null && panelAmount < 0.05f ? menuX : -1000, menuY, carouselAlpha);
		if (panelAmount > 0.001f && shownPanel != null)
			panel(graphics, menuX, menuY, fade);
		if (hoverKey != null && !hoverKey.equals(lastHoverKey))
			Sounds.HOVER.play(1f + (hoverKey.hashCode() % 5) * 0.02f);
		lastHoverKey = hoverKey;
		updateTryOn();
	}

	private void updateTryOn() {
		var tile = panel != null && !editing ? hoveredTile : null;
		Cosmetics.tryCape = null;
		Cosmetics.tryHat = null;
		if (tile != null && tile.cosmetic() && !tile.chosen().getAsBoolean())
			tile.tryOn().run();
	}

	private void carousel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
		var order = new ArrayList<Integer>();
		for (int i = 0; i < pages.size(); i++)
			if (Math.abs(relative(i)) < VISIBLE_RANGE && !(i == selected && panelAmount > 0.001f))
				order.add(i);
		order.sort(Comparator.comparing((Integer i) -> Math.abs(relative(i))).reversed());
		for (int index : order) {
			graphics.nextStratum();
			card(graphics, pages.get(index), relative(index), mouseX, mouseY, alpha);
		}

		graphics.nextStratum();
		int cx = (int) carouselX();
		int dotY = carouselY() + cardHeight() / 2 + 9;
		int dotStart = cx - (pages.size() * 10) / 2;
		for (int i = 0; i < pages.size(); i++) {
			float near = Math.max(0f, 1f - Math.abs(relative(i)));
			Draw.roundRect(graphics, dotStart + i * 10, dotY, 5, 5, 2.5f, Fx.alpha(Fx.mix(0x50FFFFFF, TEXT, near), alpha));
		}
	}

	private void card(GuiGraphicsExtractor graphics, Page page, float rel, int mouseX, int mouseY, float alpha) {
		float side = Math.clamp(rel, -1f, 1f);
		float amount = Math.abs(side);
		float excess = Math.abs(rel) - amount;
		float rise = Math.clamp((age - amount * 0.1f) * OPEN_SPEED, 0f, 1f);
		float cardAlpha = alpha * Math.clamp(1f - excess / (VISIBLE_RANGE - 1f), 0f, 1f) * rise;
		if (cardAlpha < 0.02f)
			return;

		float scale = 1f - (1f - SIDE_SCALE) * amount;
		float tilt = side * MAX_TILT;
		float cos = (float) Math.cos(tilt);
		float taper = (float) Math.sin(Math.abs(tilt)) * PERSPECTIVE * Math.signum(side);
		var rect = cardRect(rel, carouselX());
		float cardW = rect[2];
		float cardH = rect[3];
		float centerX = rect[0] + cardW / 2f;
		boolean hovered = hover("card-" + page.id(), inside(mouseX, mouseY, (int) rect[0], (int) rect[1], (int) cardW, (int) cardH));
		float lift = hoverAmount("card-" + page.id(), hovered);
		float centerY = carouselY() + (1f - Fx.easeOutCubic(rise)) * 16f - lift * 3f;
		float dim = Math.min(1f, (1f - SIDE_FADE) * amount * (1f - lift * 0.5f));
		cardFace(graphics, page, centerX, centerY, cardW, cardH, scale, cos, taper, 1f - amount, dim, cardAlpha);
	}

	private void cardFace(GuiGraphicsExtractor graphics, Page page, float centerX, float centerY, float cardW, float cardH, float scale, float cos, float taper, float focus, float dim, float alpha) {
		int accent = ZyroxTheme.accent();
		if (focus > 0.05f) {
			Draw.glow(graphics, centerX - cardW / 2, centerY - cardH / 2, cardW, cardH, CARD_RADIUS, 9f, Fx.alpha(SHADOW, alpha * focus));
			Draw.glow(graphics, centerX - cardW / 2, centerY - cardH / 2, cardW, cardH, CARD_RADIUS, 4f, Fx.alpha(accent, 0.4f * alpha * focus));
		}
		int top = Fx.alpha(Fx.mix(Fx.mix(CARD_TOP, 0xFF000000 | accent, 0.14f * focus), BLACK, dim), alpha);
		int bottom = Fx.alpha(Fx.mix(CARD_BOTTOM, BLACK, dim), alpha);
		int border = Fx.alpha(Fx.mix(CARD_BORDER, Fx.alpha(accent, 0.85f), focus), alpha);
		Draw.card(graphics, centerX, centerY, cardW, cardH, CARD_RADIUS * scale, taper, top, bottom, border);

		var pose = graphics.pose();
		float iconSize = cardHeight() * 0.28f * scale;
		float iconScale = iconSize / ICON_SIZE;
		pose.pushMatrix();
		pose.translate(centerX, centerY - cardH * 0.12f);
		pose.scale(iconScale * cos, iconScale);
		pose.translate(-ICON_SIZE / 2f, -ICON_SIZE / 2f);
		int iconColor = Fx.alpha(Fx.mix(Fx.mix(TEXT, accent, focus * 0.25f), BLACK, dim), alpha);
		graphics.blit(RenderPipelines.GUI_TEXTURED, page.icon(), 0, 0, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, iconColor);
		pose.popMatrix();

		float labelY = cardH * 0.26f;
		float shear = -taper * 2f * labelY / Math.max(1f, cardW);
		pose.pushMatrix();
		pose.translate(centerX, centerY + labelY);
		pose.mul(new Matrix3x2f(1f, shear, 0f, 1f, 0f, 0f));
		pose.scale(scale * cos, scale);
		Type.LARGE.centered(graphics, font, page.title(), 0, -7, Fx.alpha(Fx.mix(TEXT, BLACK, dim), alpha));
		Type.REGULAR.centered(graphics, font, page.description(), 0, 9, Fx.alpha(Fx.mix(MUTED, BLACK, dim), alpha));
		pose.popMatrix();
	}

	private void panel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
		graphics.nextStratum();
		float t = Fx.easeInOutCubic(panelAmount);
		var to = panelRect();
		if (morphFrom == null || panel == null && parent != null)
			morphFrom = new float[] { to[0] + 40f, to[1], to[2], to[3] };
		float x = Fx.lerp(morphFrom[0], to[0], t);
		float y = Fx.lerp(morphFrom[1], to[1], t);
		float w = Fx.lerp(morphFrom[2], to[2], t);
		float h = Fx.lerp(morphFrom[3], to[3], t);
		float radius = Fx.lerp(CARD_RADIUS, PANEL_RADIUS, t);
		float shell = alpha * Math.min(1f, panelAmount * 4f);
		Draw.glow(graphics, x, y, w, h, radius, 12f, Fx.alpha(SHADOW, shell));
		Draw.roundRect(graphics, x, y, w, h, radius, Fx.alpha(Fx.mix(CARD_TOP, PANEL_COLOR, t), shell));
		Draw.outline(graphics, x, y, w, h, radius, Fx.alpha(Fx.mix(Fx.alpha(ZyroxTheme.accent(), 0.85f), PANEL_BORDER, t), shell));
		float faceAlpha = 1f - Math.clamp(panelAmount * 2.5f, 0f, 1f);
		if (faceAlpha > 0.02f) {
			graphics.nextStratum();
			cardFace(graphics, shownPanel, x + w / 2f, y + h / 2f, w, h, 1f, 1f, 0f, 1f, 0f, alpha * faceAlpha);
		}
		float content = alpha * Math.clamp((panelAmount - 0.55f) / 0.45f, 0f, 1f);
		if (content < 0.02f)
			return;

		int left = panelLeft();
		int right = panelRight();
		int top = panelTop();
		int bottom = panelBottom();
		int width = right - left;

		boolean backHovered = hover("back", inside(mouseX, mouseY, left + 9, top + 10, 18, 18));
		float back = hoverAmount("back", backHovered);
		Draw.roundRect(graphics, left + 9, top + 10, 18, 18, 9f, Fx.alpha(Fx.mix(0x14FFFFFF, 0x34FFFFFF, back), content));
		Type.SEMIBOLD.centered(graphics, font, "\u2039", left + 18 - (int) (back * 1.5f), top + 14, Fx.alpha(TEXT, content));
		Type.LARGE.draw(graphics, font, shownPanel.title(), left + 34, top + 14, Fx.alpha(TEXT, content));
		searchBox(graphics, mouseX, mouseY, content);
		graphics.fill(left + 10, rowsTop() - 3, right - 10, rowsTop() - 2, Fx.alpha(0x14FFFFFF, content));

		graphics.enableScissor(left + 1, rowsTop(), right - 1, rowsBottom());
		int rowX = left + 6;
		int rowWidth = width - 14;
		int rowY = rowsTop() - (int) panelScroll;
		var rows = shownRows();
		for (int i = 0; i < rows.size(); i++) {
			var row = rows.get(i);
			float appear = Math.clamp((contentAnim - i * ROW_STAGGER) * 3f, 0f, 1f);
			if (appear > 0.01f && rowY + row.height >= rowsTop() && rowY <= rowsBottom()) {
				var pose = graphics.pose();
				pose.pushMatrix();
				pose.translate(0, (1f - Fx.easeOutCubic(appear)) * 10f);
				row.draw(graphics, rowX, rowY, rowWidth, mouseX, mouseY, content * appear);
				pose.popMatrix();
			}
			rowY += row.height + ROW_GAP;
		}
		graphics.disableScissor();

		int overflow = maxPanelScroll();
		if (overflow > 0) {
			int track = rowsBottom() - rowsTop();
			int bar = Math.max(14, track * track / (track + overflow));
			int barTop = rowsTop() + (int) ((track - bar) * panelScroll / overflow);
			Draw.roundRect(graphics, right - 5, barTop, 2, bar, 1f, Fx.alpha(0x50FFFFFF, content));
		}
		infoBar(graphics, left, bottom, width, content);
	}

	private void searchBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
		int x = searchLeft();
		int y = searchTop();
		boolean hovered = hover("search", inside(mouseX, mouseY, x, y, SEARCH_WIDTH, SEARCH_HEIGHT));
		float focus = hoverAmount("search-focus", searchFocused || searching());
		float lift = hoverAmount("search", hovered);
		Draw.roundRect(graphics, x, y, SEARCH_WIDTH, SEARCH_HEIGHT, SEARCH_HEIGHT / 2f, Fx.alpha(Fx.mix(0x12FFFFFF, 0x22FFFFFF, Math.max(lift, focus)), alpha));
		Draw.outline(graphics, x, y, SEARCH_WIDTH, SEARCH_HEIGHT, SEARCH_HEIGHT / 2f, Fx.alpha(Fx.mix(0x18FFFFFF, ZyroxTheme.accent(), focus), alpha));
		Draw.outline(graphics, x + 7, y + 5, 6, 6, 3f, Fx.alpha(MUTED, alpha));
		Fx.line(graphics, x + 12, y + 10, x + 15, y + 13, Fx.alpha(MUTED, alpha));
		var shown = searching() ? fitted(search, SEARCH_WIDTH - 40) : "Search";
		Type.REGULAR.draw(graphics, font, shown, x + 19, y + 5, Fx.alpha(searching() ? TEXT : MUTED, alpha));
		if (searchFocused && (age * 2f) % 2f < 1.2f)
			graphics.fill(x + 20 + (searching() ? Type.REGULAR.width(font, shown) : 0), y + 4, x + 21 + (searching() ? Type.REGULAR.width(font, shown) : 0), y + 14, Fx.alpha(TEXT, alpha));
		if (searching())
			Type.REGULAR.centered(graphics, font, "\u00d7", x + SEARCH_WIDTH - 9, y + 5, Fx.alpha(MUTED, alpha));
	}

	private String infoText() {
		var tile = hoveredTile;
		if (tile != null) {
			if (!tile.cosmetic())
				return tile.name() + "  \u00b7  " + (tile.chosen().getAsBoolean() ? "On" : "Off") + "  \u00b7  " + tile.detail();
			if (tile.locked().getAsBoolean())
				return tile.name() + "  \u00b7  Locked, you are trying it on";
			if (tile.chosen().getAsBoolean())
				return tile.name() + "  \u00b7  Wearing  \u00b7  Right click to " + (Cosmetics.FAVORITES.contains(tile.key()) ? "unfavorite" : "favorite");
			return tile.name() + "  \u00b7  Click to wear  \u00b7  Right click to " + (Cosmetics.FAVORITES.contains(tile.key()) ? "unfavorite" : "favorite");
		}
		if (searching())
			return "Esc clears the search";
		return shownPanel.description() + "  \u00b7  Type to search, Esc to go back";
	}

	private void infoBar(GuiGraphicsExtractor graphics, int left, int bottom, int width, float alpha) {
		int y = bottom - INFO_HEIGHT - 4;
		graphics.fill(left + 10, y, left + width - 10, y + 1, Fx.alpha(0x14FFFFFF, alpha));
		var text = fitted(infoText(), width - 28);
		boolean active = hoveredTile != null;
		if (active)
			Draw.roundRect(graphics, left + 10, y + 8, 4, 4, 2f, Fx.alpha(ZyroxTheme.accent(), alpha));
		Type.REGULAR.draw(graphics, font, text, left + (active ? 18 : 10), y + 7, Fx.alpha(active ? TEXT : MUTED, alpha));
	}

	private void editOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		for (var module : Modules.ALL) {
			if (!module.enabled || !(module instanceof HudModule hud))
				continue;
			int left = hud.left(minecraft);
			int top = hud.top(minecraft);
			int w = hud.width(minecraft);
			int h = hud.height(minecraft);
			boolean active = hud == dragging || hud == resizing || inside(mouseX, mouseY, left, top, w, h);
			Draw.outline(graphics, left - 1, top - 1, w + 2, h + 2, 5f, Fx.alpha(active ? ZyroxTheme.accent() : 0x80FFFFFF, editAmount));
			Draw.roundRect(graphics, left + w - 3, top + h - 3, 5f, 5f, 2f, Fx.alpha(active ? ZyroxTheme.accent() : TEXT, editAmount));
			if (active) {
				String percent = Math.round(hud.scale * 100) + "%";
				int labelWidth = Type.REGULAR.width(font, percent);
				int labelY = top >= 13 ? top - 12 : top + h + 3;
				Draw.roundRect(graphics, left - 1, labelY, labelWidth + 6, 10, 3f, Fx.alpha(PANEL_COLOR, editAmount));
				Type.REGULAR.draw(graphics, font, percent, left + 2, labelY + 1, Fx.alpha(TEXT, editAmount));
			}
		}
		if (guideX >= 0)
			graphics.fill(guideX, 0, guideX + 1, height, Fx.alpha(ZyroxTheme.accent(), editAmount));
		if (guideY >= 0)
			graphics.fill(0, guideY, width, guideY + 1, Fx.alpha(ZyroxTheme.accent(), editAmount));

		String hint = "Drag to move, Shift turns off snapping. Scroll or drag the corner to resize, right click resets.";
		int hintWidth = Type.REGULAR.width(font, hint);
		int hintX = (width - hintWidth) / 2;
		Draw.roundRect(graphics, hintX - 8, 8, hintWidth + 16, 16, 8f, Fx.alpha(PANEL_COLOR, editAmount));
		Type.REGULAR.draw(graphics, font, hint, hintX, 12, Fx.alpha(TEXT, editAmount));

		boolean hovered = inside(mouseX, mouseY, doneLeft(), doneTop(), DONE_WIDTH, DONE_HEIGHT);
		Draw.roundRect(graphics, doneLeft(), doneTop(), DONE_WIDTH, DONE_HEIGHT, 9f, Fx.alpha(Fx.mix(ZyroxTheme.accent(), TEXT, hovered ? 0.12f : 0f), editAmount));
		Type.SEMIBOLD.centered(graphics, font, "Done", width / 2, doneTop() + 5, Fx.alpha(TEXT, editAmount));
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private void playClick(float pitch) {
		Sounds.CLICK.play(0.9f + (pitch - 1f) * 0.25f);
	}

	private void move(int direction) {
		selected = Math.floorMod(selected + direction, pages.size());
		Sounds.SLIDE.play(direction > 0 ? 1.05f : 0.95f);
	}

	private void openSelected() {
		morphFrom = cardRect(0f, width * CAROUSEL_X);
		panel = pages.get(selected);
		panelScroll = 0;
		panelScrollTarget = 0;
		search = "";
		searchFocused = false;
		contentKey = "";
		CameraRig.setOrbit(panel.orbit());
		Sounds.OPEN.play();
	}

	private void closePanel() {
		if (parent != null) {
			onClose();
			return;
		}
		morphFrom = cardRect(0f, width * CAROUSEL_X);
		panel = null;
		search = "";
		searchFocused = false;
		CameraRig.setOrbit(0f);
		Sounds.CLOSE.play();
	}

	private void enterEdit() {
		editing = true;
		CameraRig.setActive(false);
	}

	private void leaveEdit() {
		editing = false;
		dragging = null;
		resizing = null;
		if (ZyroxTheme.cinematic)
			CameraRig.setActive(true);
	}

	private HudModule hudAt(double mouseX, double mouseY) {
		for (var module : Modules.ALL) {
			if (!module.enabled || !(module instanceof HudModule hud))
				continue;
			if (inside(mouseX, mouseY, hud.left(minecraft), hud.top(minecraft), hud.width(minecraft), hud.height(minecraft)))
				return hud;
		}
		return null;
	}

	private HudModule handleAt(double mouseX, double mouseY) {
		for (var module : Modules.ALL) {
			if (!module.enabled || !(module instanceof HudModule hud))
				continue;
			int right = hud.left(minecraft) + hud.width(minecraft);
			int bottom = hud.top(minecraft) + hud.height(minecraft);
			if (inside(mouseX, mouseY, right - HANDLE_SIZE + 2, bottom - HANDLE_SIZE + 2, HANDLE_SIZE, HANDLE_SIZE))
				return hud;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (editing && !isClosing && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			var hud = hudAt(event.x(), event.y());
			if (hud != null)
				hud.setScale(1f);
			return hud != null;
		}
		if (!editing && !isClosing && panel != null && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			var tile = hoveredTile;
			if (tile == null || !tile.cosmetic())
				return false;
			if (!Cosmetics.FAVORITES.remove(tile.key()))
				Cosmetics.FAVORITES.add(tile.key());
			Sounds.TOGGLE_ON.play(Cosmetics.FAVORITES.contains(tile.key()) ? 1.2f : 0.8f);
			return true;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || isClosing)
			return false;
		double mouseX = event.x();
		double mouseY = event.y();
		if (editing)
			return clickEditing(mouseX, mouseY);
		if (panel != null)
			return clickPanel(mouseX, mouseY);
		return clickCarousel(mouseX, mouseY);
	}

	private boolean clickEditing(double mouseX, double mouseY) {
		if (inside(mouseX, mouseY, doneLeft(), doneTop(), DONE_WIDTH, DONE_HEIGHT)) {
			leaveEdit();
			playClick(1f);
			return true;
		}
		var handle = handleAt(mouseX, mouseY);
		if (handle != null) {
			resizing = handle;
			resizeLeft = handle.left(minecraft);
			resizeTop = handle.top(minecraft);
			resizeWidth = Math.max(1, handle.width(minecraft));
			resizeHeight = Math.max(1, handle.height(minecraft));
			resizeScale = handle.scale;
			handle.x = resizeLeft;
			handle.y = resizeTop;
			return true;
		}
		var hud = hudAt(mouseX, mouseY);
		if (hud == null)
			return false;
		dragging = hud;
		dragOffsetX = (int) mouseX - hud.left(minecraft);
		dragOffsetY = (int) mouseY - hud.top(minecraft);
		return true;
	}

	private boolean clickPanel(double mouseX, double mouseY) {
		if (!inside(mouseX, mouseY, panelLeft(), panelTop(), panelRight() - panelLeft(), panelBottom() - panelTop())) {
			searchFocused = false;
			orbiting = true;
			return true;
		}
		if (panelAmount < 0.6f)
			return true;
		if (inside(mouseX, mouseY, panelLeft() + 9, panelTop() + 10, 18, 18)) {
			closePanel();
			return true;
		}
		if (inside(mouseX, mouseY, searchLeft(), searchTop(), SEARCH_WIDTH, SEARCH_HEIGHT)) {
			if (searching() && mouseX >= searchLeft() + SEARCH_WIDTH - 16)
				setSearch("");
			searchFocused = true;
			Sounds.TAB.play(1.3f);
			return true;
		}
		searchFocused = false;
		if (mouseY < rowsTop() || mouseY >= rowsBottom())
			return true;
		int x = panelLeft() + 6;
		int w = panelRight() - panelLeft() - 14;
		int y = rowsTop() - (int) panelScroll;
		for (var row : shownRows()) {
			if (inside(mouseX, mouseY, x, y, w, row.height)) {
				clickY = mouseY;
				row.click(mouseX, x, y, w);
				return true;
			}
			y += row.height + ROW_GAP;
		}
		return true;
	}

	private boolean clickCarousel(double mouseX, double mouseY) {
		if (panelAmount > 0.05f)
			return true;
		int centerX = (int) (width * CAROUSEL_X);
		int halfWidth = cardWidth() / 2;
		int halfHeight = cardHeight() / 2;
		if (Math.abs(mouseY - carouselY()) > halfHeight || mouseX < centerX - cardWidth() * 1.3f) {
			orbiting = true;
			return true;
		}
		if (Math.abs(mouseX - centerX) <= halfWidth)
			openSelected();
		else
			move(mouseX < centerX ? -1 : 1);
		return true;
	}

	private void setSearch(String value) {
		search = value;
		panelScroll = 0;
		panelScrollTarget = 0;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (panel == null || editing || isClosing || !event.isAllowedChatCharacter())
			return false;
		if (search.length() >= 32)
			return true;
		var text = event.codepointAsString();
		if (search.isEmpty() && text.isBlank())
			return true;
		searchFocused = true;
		setSearch(search + text);
		Sounds.TYPE.play(0.95f + RANDOM.nextFloat() * 0.1f);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (orbiting) {
			CameraRig.addOrbit((float) deltaX * ORBIT_PER_PIXEL);
			return true;
		}
		if (resizing != null) {
			float moved = (float) ((event.x() - resizeLeft - resizeWidth) * resizeWidth + (event.y() - resizeTop - resizeHeight) * resizeHeight);
			resizing.setScale(resizeScale * (1f + moved / (resizeWidth * resizeWidth + resizeHeight * resizeHeight)));
			return true;
		}
		if (dragging == null)
			return false;
		int w = dragging.width(minecraft);
		int h = dragging.height(minecraft);
		int maxX = Math.max(0, width - w);
		int maxY = Math.max(0, height - h);
		int x = Math.clamp((int) event.x() - dragOffsetX, 0, maxX);
		int y = Math.clamp((int) event.y() - dragOffsetY, 0, maxY);
		guideX = -1;
		guideY = -1;
		if (!event.hasShiftDown()) {
			int[] snappedX = snap(x, w, width, true);
			int[] snappedY = snap(y, h, height, false);
			x = Math.clamp(snappedX[0], 0, maxX);
			y = Math.clamp(snappedY[0], 0, maxY);
			guideX = snappedX[1];
			guideY = snappedY[1];
		}
		dragging.x = x;
		dragging.y = y;
		return true;
	}

	private int[] snap(int position, int size, int screen, boolean horizontal) {
		int best = position;
		int guide = -1;
		int bestDistance = SNAP + 1;
		var targets = new ArrayList<Integer>(List.of(0, screen / 2, screen));
		for (var module : Modules.ALL) {
			if (module == dragging || !module.enabled || !(module instanceof HudModule other))
				continue;
			int start = horizontal ? other.left(minecraft) : other.top(minecraft);
			int length = horizontal ? other.width(minecraft) : other.height(minecraft);
			targets.add(start);
			targets.add(start + length);
		}
		int[] offsets = { 0, size / 2, size };
		for (int target : targets) {
			for (int offset : offsets) {
				int distance = Math.abs(position + offset - target);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = target - offset;
					guide = Math.min(target, screen - 1);
				}
			}
		}
		return new int[] { best, guide };
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		resizing = null;
		orbiting = false;
		guideX = -1;
		guideY = -1;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (isClosing || vertical == 0)
			return false;
		if (editing) {
			var hud = hudAt(mouseX, mouseY);
			if (hud != null)
				hud.setScale(hud.scale + (vertical > 0 ? HudModule.SCALE_STEP : -HudModule.SCALE_STEP));
			return hud != null;
		}
		if (panel != null)
			panelScrollTarget = Math.clamp(panelScrollTarget - (float) vertical * 18f, 0f, maxPanelScroll());
		else
			move(vertical < 0 ? 1 : -1);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isClosing)
			return true;
		if (ZyroxClient.menuKey.matches(event)) {
			onClose();
			return true;
		}
		int key = event.key();
		if (panel != null && !editing && searching() && key == InputConstants.KEY_BACKSPACE) {
			setSearch(event.hasControlDown() ? "" : search.substring(0, search.length() - 1));
			Sounds.TYPE.play(0.85f);
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE && panel != null && !editing && (searching() || searchFocused)) {
			setSearch("");
			searchFocused = false;
			Sounds.CLOSE.play(1.3f);
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE) {
			if (editing)
				leaveEdit();
			else if (panel != null)
				closePanel();
			else
				onClose();
			return true;
		}
		if (editing || panel != null)
			return super.keyPressed(event);
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_A) {
			move(-1);
			return true;
		}
		if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_D) {
			move(1);
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) {
			openSelected();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (isClosing)
			return;
		isClosing = true;
		panel = null;
		CameraRig.setActive(false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		Cosmetics.tryCape = null;
		Cosmetics.tryHat = null;
		Effects.menuAmount = 0f;
		CameraRig.setActive(false);
		ZyroxConfig.save();
	}
}
