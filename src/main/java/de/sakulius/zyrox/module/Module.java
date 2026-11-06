package de.sakulius.zyrox.module;

public abstract class Module {
	public enum Category {
		HUD,
		MUSIC,
		UTILITY,
		PVP,
		WAYPOINTS
	}

	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	public boolean enabled;

	protected Module(String id, String name, String description, Category category, boolean enabled) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = enabled;
	}
}
