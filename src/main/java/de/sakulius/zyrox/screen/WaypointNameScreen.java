package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.sakulius.zyrox.ZyroxTheme;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class WaypointNameScreen extends Screen {
	private static final int CARD_WIDTH = 220;
	private static final int CARD_HEIGHT = 70;
	private static final int MAX_NAME = 32;
	private static final int PANEL_COLOR = 0xEE101116;
	private static final int PANEL_BORDER = 0x1EFFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFF8D929C;

	private final Screen parent;
	private final String title;
	private final String startName;
	private final Consumer<String> done;
	private EditBox input;

	public WaypointNameScreen(Screen parent, String title, String startName, Consumer<String> done) {
		super(Component.literal(title));
		this.parent = parent;
		this.title = title;
		this.startName = startName;
		this.done = done;
	}

	private int left() {
		return (width - CARD_WIDTH) / 2;
	}

	private int top() {
		return (height - CARD_HEIGHT) / 2;
	}

	@Override
	protected void init() {
		input = new EditBox(font, left() + 10, top() + 26, CARD_WIDTH - 20, 18, Component.literal("Name"));
		input.setMaxLength(MAX_NAME);
		input.setValue(startName);
		addRenderableWidget(input);
		setInitialFocus(input);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			var name = input.getValue().strip();
			if (!name.isEmpty()) {
				done.accept(name);
				onClose();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		Draw.roundRect(graphics, left(), top(), CARD_WIDTH, CARD_HEIGHT, 8f, PANEL_COLOR);
		Draw.outline(graphics, left(), top(), CARD_WIDTH, CARD_HEIGHT, 8f, Fx.alpha(ZyroxTheme.accent(), 0.8f));
		Draw.outline(graphics, left() + 1, top() + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 7f, PANEL_BORDER);
		Type.SEMIBOLD.draw(graphics, font, title, left() + 10, top() + 10, TEXT);
		Type.REGULAR.draw(graphics, font, "Enter saves, Esc cancels", left() + 10, top() + 52, MUTED);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}
}
