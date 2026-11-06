package de.sakulius.zyrox.module;

import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.cloud.CloudCosmetics;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class ZyroxBadge {
	private static final Style STYLE = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "badge")));

	private ZyroxBadge() {
	}

	public static boolean shows(UUID player) {
		return Modules.BADGE.enabled && (player.equals(Minecraft.getInstance().getUser().getProfileId()) || CloudCosmetics.usesZyrox(player));
	}

	public static Component decorate(UUID player, Component name) {
		if (name == null || !shows(player))
			return name;
		return Component.empty().append(Component.literal("").withStyle(STYLE)).append(" ").append(name);
	}
}
