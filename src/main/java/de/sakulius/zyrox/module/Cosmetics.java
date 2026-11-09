package de.sakulius.zyrox.module;

import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.cloud.CloudCosmetics;
import de.sakulius.zyrox.cloud.Roles;
import de.sakulius.zyrox.screen.CameraRig;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

public final class Cosmetics {
	public record Cape(String id, String name, ClientAsset.Texture texture) {
	}

	private record CachedSkin(PlayerSkin source, String cape, PlayerSkin skin) {
	}

	public record Aura(String name, ParticleOptions particle, int interval, Identifier icon, int iconSize) {
	}

	public record Wearable(String id, String name) {
	}

	public static final String DEFAULT_CAPE = "default";
	public static final String NOTHING = "none";
	public static final String CAPE_SLOT = "cape";
	public static final String HAT_SLOT = "hat";
	private static final double AURA_RADIUS = 0.7;
	private static final double AURA_SPIN = 0.35;
	private static final double AURA_RISE = 0.02;

	public static final List<Cape> CAPES = List.of(
		new Cape(DEFAULT_CAPE, "Your own", null),
		cape("Zyrox", "zyrox"),
		cape("Nebula", "nebula"),
		cape("Inferno", "inferno"),
		cape("Glacier", "glacier"),
		cape("Circuit", "circuit"));

	public static final List<Cape> LEGACY_CAPES = List.of(
		new Cape("legacy_zyrox", "Classic", legacyTexture("zyrox")),
		legacyCape("Aurora", "aurora"),
		legacyCape("Ember", "ember"),
		legacyCape("Void", "void"),
		legacyCape("Ocean", "ocean"),
		legacyCape("Sakura", "sakura"),
		legacyCape("Forest", "forest"),
		legacyCape("Royal", "royal"),
		legacyCape("Frost", "frost"),
		legacyCape("Carbon", "carbon"));

	private static Identifier particleIcon(String name) {
		return Identifier.withDefaultNamespace("textures/particle/" + name + ".png");
	}

	public static final List<Aura> AURAS = List.of(
		new Aura("Off", null, 0, null, 0),
		new Aura("Flame", ParticleTypes.FLAME, 2, particleIcon("flame"), 8),
		new Aura("Soul Fire", ParticleTypes.SOUL_FIRE_FLAME, 2, particleIcon("soul_fire_flame"), 8),
		new Aura("Hearts", ParticleTypes.HEART, 8, particleIcon("heart"), 8),
		new Aura("End Rod", ParticleTypes.END_ROD, 3, particleIcon("glitter_7"), 8),
		new Aura("Enchant", ParticleTypes.ENCHANT, 1, particleIcon("sga_a"), 8),
		new Aura("Cherry", ParticleTypes.CHERRY_LEAVES, 3, particleIcon("cherry_0"), 3));

	public static final List<Wearable> HATS = List.of(
		new Wearable(NOTHING, "Off"),
		new Wearable("party_hat", "Rainbow Hat"),
		new Wearable("red_party_hat", "Party Hat"),
		new Wearable("third_party_hat", "Striped Hat"),
		new Wearable("beanie", "Beanie"),
		new Wearable("traffic_cone", "Traffic Cone"));

	private static final Set<String> FREE = Set.of(DEFAULT_CAPE, NOTHING, "zyrox", "red_party_hat", "aura_off", "aura_flame");

	public static String capeId = DEFAULT_CAPE;
	public static String hatId = NOTHING;
	public static int aura;
	public static String tryCape;
	public static final Set<String> FAVORITES = new LinkedHashSet<>();
	public static final String[][] OUTFITS = new String[3][];
	public static String tryHat;

	private static final Map<UUID, CachedSkin> SKINS = new ConcurrentHashMap<>();
	private static volatile Set<String> owned = Set.of();
	private static long ticks;

	private Cosmetics() {
	}

	private static Cape cape(String name, String texture) {
		return new Cape(texture, name, new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "cape/" + texture)));
	}

	private static ClientAsset.Texture legacyTexture(String texture) {
		return new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "cape/legacy/" + texture));
	}

	private static Cape legacyCape(String name, String texture) {
		return new Cape(texture, name, legacyTexture(texture));
	}

	public static String auraId(Aura aura) {
		return "aura_" + aura.name().toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	public static boolean owns(String id) {
		if (Roles.own() != null)
			return true;
		if (isLegacy(id))
			return false;
		return FREE.contains(id) || owned.contains(id);
	}

	public static boolean isLegacy(String id) {
		for (var cape : LEGACY_CAPES)
			if (cape.id().equals(id))
				return true;
		return false;
	}

	public static String hatName(String id) {
		for (var wearable : HATS)
			if (wearable.id().equals(id))
				return wearable.name();
		return null;
	}

	public static void setOwned(Collection<String> items) {
		owned = Set.copyOf(items);
		if (!owns(capeId))
			capeId = DEFAULT_CAPE;
		if (!owns(hatId))
			hatId = NOTHING;
		if (!owns(auraId(AURAS.get(aura))))
			aura = 0;
	}

	public static void wear(String[] outfit) {
		if (outfit == null)
			return;
		if (capeById(outfit[0]) != null && owns(outfit[0]))
			capeId = outfit[0];
		if (owns(outfit[1]) && (outfit[1].equals(NOTHING) || hatName(outfit[1]) != null))
			hatId = outfit[1];
		try {
			int index = Integer.parseInt(outfit[2]);
			if (index >= 0 && index < AURAS.size() && owns(auraId(AURAS.get(index))))
				aura = index;
		} catch (NumberFormatException ignored) {
		}
	}

	public static String[] outfit() {
		return new String[] { capeId, hatId, Integer.toString(aura) };
	}

	public static Cape capeById(String id) {
		for (var cape : CAPES)
			if (cape.id().equals(id))
				return cape;
		for (var cape : LEGACY_CAPES)
			if (cape.id().equals(id))
				return cape;
		return null;
	}

	public static PlayerSkin apply(UUID player, boolean self, PlayerSkin skin) {
		var id = self ? (tryCape != null ? tryCape : capeId) : CloudCosmetics.item(player, CAPE_SLOT);
		var cape = id == null ? null : capeById(id);
		if (cape == null || cape.texture() == null)
			return skin;
		var cached = SKINS.get(player);
		if (cached != null && cached.source() == skin && cached.cape().equals(id))
			return cached.skin();
		var result = new PlayerSkin(skin.body(), cape.texture(), skin.elytra(), skin.model(), skin.secure());
		SKINS.put(player, new CachedSkin(skin, id, result));
		return result;
	}

	public static Map<String, String> ownItems() {
		var items = new LinkedHashMap<String, String>();
		items.put(CAPE_SLOT, capeId);
		items.put(HAT_SLOT, hatId);
		return items;
	}

	public static Wearable hat(UUID player, boolean self) {
		var id = self ? (tryHat != null ? tryHat : hatId) : CloudCosmetics.item(player, HAT_SLOT);
		if (id == null || id.equals(NOTHING))
			return null;
		for (var wearable : HATS)
			if (wearable.id().equals(id))
				return wearable;
		return null;
	}

	public static void tick(Minecraft client) {
		ticks++;
		var selected = AURAS.get(aura);
		var player = client.player;
		if (selected.particle() == null || player == null || client.level == null)
			return;
		if (client.options.getCameraType().isFirstPerson() && !CameraRig.hidesHud())
			return;
		if (ticks % selected.interval() != 0)
			return;
		double angle = ticks * AURA_SPIN;
		double height = 0.2 + (Math.sin(ticks * 0.1) + 1) * 0.7;
		client.level.addParticle(selected.particle(),
			player.getX() + Math.cos(angle) * AURA_RADIUS, player.getY() + height, player.getZ() + Math.sin(angle) * AURA_RADIUS,
			0, AURA_RISE, 0);
	}
}
