package de.sakulius.zyrox.module;

import net.minecraft.resources.Identifier;

public class PvpModule extends Module {
	public final Identifier icon;
	public final int iconSize;

	public PvpModule(String id, String name, String description, boolean enabled, Identifier icon, int iconSize) {
		super(id, name, description, Category.PVP, enabled);
		this.icon = icon;
		this.iconSize = iconSize;
	}
}
