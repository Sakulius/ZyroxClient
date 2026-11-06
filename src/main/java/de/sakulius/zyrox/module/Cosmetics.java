package de.sakulius.zyrox.module;

import de.sakulius.zyrox.cloud.CloudCosmetics;
import de.sakulius.zyrox.screen.CameraRig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;

public final class Cosmetics {
	public record Aura(String name, ParticleOptions particle, int interval, Identifier icon, int iconSize) {
	}

	public record Wearable(String id, String name) {
	}

	public static final String NOTHING = "none";
	public static final String HAT_SLOT = "hat";
	private static final double AURA_RADIUS = 0.7;
	private static final double AURA_SPIN = 0.35;
	private static final double AURA_RISE = 0.02;

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
		new Wearable("third_party_hat", "Striped Hat"));

	public static String hatId = NOTHING;
	public static int aura;

	private static long ticks;

	private Cosmetics() {
	}

	public static Map<String, String> ownItems() {
		var items = new LinkedHashMap<String, String>();
		items.put(HAT_SLOT, hatId);
		return items;
	}

	public static Wearable hat(UUID player, boolean self) {
		var id = self ? hatId : CloudCosmetics.item(player, HAT_SLOT);
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
