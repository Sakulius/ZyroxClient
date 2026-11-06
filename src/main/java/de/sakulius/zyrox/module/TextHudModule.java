package de.sakulius.zyrox.module;

import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Type;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class TextHudModule extends HudModule {
	private static final int PADDING_X = 5;
	private static final int HEIGHT = 15;
	private static final int TEXT_Y = 4;
	private static final float RADIUS = 4f;

	private final Function<Minecraft, String> text;

	public TextHudModule(String id, String name, String description, boolean enabled, int x, int y, Function<Minecraft, String> text) {
		super(id, name, description, enabled, x, y);
		this.text = text;
	}

	@Override
	public int contentWidth(Minecraft client) {
		return Type.REGULAR.width(client.font, text.apply(client)) + PADDING_X * 2;
	}

	@Override
	public int contentHeight(Minecraft client) {
		return HEIGHT;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, Minecraft client, int left, int top) {
		Draw.roundRect(graphics, left, top, contentWidth(client), HEIGHT, RADIUS, ZyroxTheme.HUD_BACKGROUND);
		Type.REGULAR.draw(graphics, client.font, text.apply(client), left + PADDING_X, top + TEXT_Y, ZyroxTheme.hudText(left, top));
	}
}
