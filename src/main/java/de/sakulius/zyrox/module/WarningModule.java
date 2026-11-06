package de.sakulius.zyrox.module;

import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Fx;
import de.sakulius.zyrox.screen.Type;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

public class WarningModule extends HudModule {
	private static final int PADDING_X = 6;
	private static final int HEIGHT = 15;
	private static final int TEXT_Y = 4;
	private static final float RADIUS = 4f;
	private static final int BACK_DIM = 0xB8701A1A;
	private static final int BACK_BRIGHT = 0xE8D93636;
	private static final int TEXT = 0xFFFFFFFF;
	private static final float PULSE_SPEED = 0.006f;

	private final Function<Minecraft, String> warning;
	private final String preview;

	public WarningModule(String id, String name, String description, int x, int y, String preview, Function<Minecraft, String> warning) {
		super(id, name, description, Category.PVP, false, x, y);
		this.preview = preview;
		this.warning = warning;
	}

	private String shown(Minecraft client) {
		var text = client.player == null ? null : warning.apply(client);
		return text != null ? text : preview;
	}

	@Override
	public int contentWidth(Minecraft client) {
		return Type.SEMIBOLD.width(client.font, shown(client)) + PADDING_X * 2;
	}

	@Override
	public int contentHeight(Minecraft client) {
		return HEIGHT;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, Minecraft client, int left, int top) {
		var text = client.player == null ? null : warning.apply(client);
		if (text == null && !editing(client))
			return;
		float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() * PULSE_SPEED);
		Draw.roundRect(graphics, left, top, contentWidth(client), HEIGHT, RADIUS, Fx.mix(BACK_DIM, BACK_BRIGHT, pulse));
		Type.SEMIBOLD.draw(graphics, client.font, shown(client), left + PADDING_X, top + TEXT_Y, TEXT);
	}
}
