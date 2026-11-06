package de.sakulius.zyrox.module;

import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class KeystrokesModule extends HudModule {
	private static final int KEY = 20;
	private static final int GAP = 2;
	private static final int MOUSE_WIDTH = 31;
	private static final int SPACE_HEIGHT = 10;
	private static final int WIDTH = KEY * 3 + GAP * 2;
	private static final int HEIGHT = KEY * 3 + SPACE_HEIGHT + GAP * 3;
	private static final float RADIUS = 4f;
	private static final int TEXT = 0xFFFFFFFF;

	public KeystrokesModule(int x, int y) {
		super("keystrokes", "Keystrokes", "Keys you press", true, x, y);
	}

	@Override
	public int contentWidth(Minecraft client) {
		return WIDTH;
	}

	@Override
	public int contentHeight(Minecraft client) {
		return HEIGHT;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, Minecraft client, int left, int top) {
		var options = client.options;
		int step = KEY + GAP;
		key(graphics, client, options.keyUp, "W", left + step, top, KEY, KEY);
		key(graphics, client, options.keyLeft, "A", left, top + step, KEY, KEY);
		key(graphics, client, options.keyDown, "S", left + step, top + step, KEY, KEY);
		key(graphics, client, options.keyRight, "D", left + step * 2, top + step, KEY, KEY);
		key(graphics, client, options.keyAttack, "LMB", left, top + step * 2, MOUSE_WIDTH, KEY);
		key(graphics, client, options.keyUse, "RMB", left + MOUSE_WIDTH + GAP, top + step * 2, MOUSE_WIDTH, KEY);
		key(graphics, client, options.keyJump, "", left, top + step * 3, WIDTH, SPACE_HEIGHT);
	}

	private static void key(GuiGraphicsExtractor graphics, Minecraft client, KeyMapping mapping, String label, int x, int y, int width, int height) {
		Draw.roundRect(graphics, x, y, width, height, RADIUS, mapping.isDown() ? ZyroxTheme.hudAccent(x, y) : ZyroxTheme.HUD_BACKGROUND);
		Type.REGULAR.centered(graphics, client.font, label, x + width / 2, y + (height - client.font.lineHeight) / 2 + 1, TEXT);
	}
}
