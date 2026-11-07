package de.sakulius.zyrox.module;

import de.sakulius.zyrox.HudStyle;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.ZyroxScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public abstract class HudModule extends Module {
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2f;
	public static final float SCALE_STEP = 0.05f;

	public int x;
	public int y;
	public float scale = 1f;

	protected HudModule(String id, String name, String description, boolean enabled, int x, int y) {
		this(id, name, description, Category.HUD, enabled, x, y);
	}

	protected HudModule(String id, String name, String description, Category category, boolean enabled, int x, int y) {
		super(id, name, description, category, enabled);
		this.x = x;
		this.y = y;
	}

	public abstract int contentWidth(Minecraft client);

	public abstract int contentHeight(Minecraft client);

	protected abstract void render(GuiGraphics graphics, Minecraft client, int left, int top);

	public final void setScale(float value) {
		scale = Math.clamp(Math.round(value / SCALE_STEP) * SCALE_STEP, MIN_SCALE, MAX_SCALE);
	}

	public final int width(Minecraft client) {
		Draw.elementScale = scale;
		HudStyle.begin();
		int width = Math.round(contentWidth(client) * scale);
		HudStyle.end();
		Draw.elementScale = 1f;
		return width;
	}

	public final int height(Minecraft client) {
		Draw.elementScale = scale;
		HudStyle.begin();
		int height = Math.round(contentHeight(client) * scale);
		HudStyle.end();
		Draw.elementScale = 1f;
		return height;
	}

	public final void render(GuiGraphics graphics, Minecraft client) {
		int left = left(client);
		int top = top(client);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(left, top);
		pose.scale(scale, scale);
		pose.translate(-left, -top);
		Draw.elementScale = scale;
		HudStyle.begin();
		render(graphics, client, left, top);
		HudStyle.end();
		Draw.elementScale = 1f;
		pose.popMatrix();
	}

	protected static boolean editing(Minecraft client) {
		return client.screen instanceof ZyroxScreen;
	}

	public final int left(Minecraft client) {
		int free = Math.max(0, client.getWindow().getGuiScaledWidth() - width(client));
		return x < 0 ? free / 2 : Math.clamp(x, 0, free);
	}

	public final int top(Minecraft client) {
		return Math.clamp(y, 0, Math.max(0, client.getWindow().getGuiScaledHeight() - height(client)));
	}
}
