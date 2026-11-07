package de.sakulius.zyrox.screen;

import de.sakulius.zyrox.ZyroxTheme;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public class ZyroxMenuButton extends AbstractButton {
	private static final int BACK = 0x8C101116;
	private static final int BORDER = 0x1EFFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFF6F747E;
	private static final float RADIUS = 5f;
	private static final float SLIDE = 4f;
	private static final float SPEED = 16f;

	private final Consumer<InputWithModifiers> action;
	private final boolean centered;
	private long lastFrame = System.nanoTime();
	private float hover;

	public ZyroxMenuButton(Button original, int x, int y, int width, int height) {
		this(original.getMessage(), x, y, width, height, original::onPress, false);
		this.active = original.active;
	}

	public ZyroxMenuButton(Component message, int x, int y, int width, int height, Consumer<InputWithModifiers> action, boolean centered) {
		super(x, y, width, height, message);
		this.action = action;
		this.centered = centered;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.accept(input);
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		hover = Fx.approach(hover, active && isHoveredOrFocused() ? 1f : 0f, dt, SPEED);

		int accent = ZyroxTheme.accent();
		float left = getX() + hover * SLIDE;
		Draw.roundRect(graphics, left, getY(), getWidth(), getHeight(), RADIUS, Fx.alpha(Fx.mix(BACK, accent, hover), alpha));
		Draw.outline(graphics, left, getY(), getWidth(), getHeight(), RADIUS, Fx.alpha(BORDER, alpha));
		var font = Minecraft.getInstance().font;
		var label = getMessage().getString();
		int textX = centered ? Math.round(left) + (getWidth() - Type.SEMIBOLD.width(font, label)) / 2 : Math.round(left) + 10;
		Type.SEMIBOLD.draw(graphics, font, label, textX, getY() + (getHeight() - 8) / 2, Fx.alpha(active ? TEXT : MUTED, alpha));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
