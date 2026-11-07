package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.sakulius.zyrox.HudStyle;
import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.emote.Emote;
import de.sakulius.zyrox.emote.Emotes;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class EmoteWheelScreen extends Screen {
	public static final int SLOTS = 8;
	private static final int RADIUS = 78;
	private static final int SLOT_SIZE = 46;
	private static final int CENTER_SIZE = 40;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFF9AA0AA;
	private static final int SLOT_BACK = 0xC8101116;
	private static final float SPEED = 14f;

	private final List<Emote> emotes;
	private final float[] hover = new float[SLOTS + 1];
	private long lastFrame = System.nanoTime();
	private float open;

	public EmoteWheelScreen() {
		super(Component.literal("Emotes"));
		emotes = Emotes.all().stream().limit(SLOTS).toList();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int centerX() {
		return width / 2;
	}

	private int centerY() {
		return height / 2;
	}

	public int[] slotCenter(int index) {
		double angle = Math.toRadians(-90 + index * 360.0 / SLOTS);
		return new int[] { centerX() + (int) Math.round(Math.cos(angle) * RADIUS), centerY() + (int) Math.round(Math.sin(angle) * RADIUS) };
	}

	public int hovered(double mouseX, double mouseY) {
		double dx = mouseX - centerX();
		double dy = mouseY - centerY();
		if (Math.hypot(dx, dy) < CENTER_SIZE / 2.0 + 4)
			return SLOTS;
		double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 180.0 / SLOTS;
		int slot = (int) Math.floor(((angle % 360) + 360) % 360 / (360.0 / SLOTS));
		return slot < emotes.size() ? slot : -1;
	}

	private void choose(int slot) {
		if (slot == SLOTS)
			Emotes.stop(minecraft);
		else if (slot >= 0 && slot < emotes.size())
			Emotes.play(minecraft, emotes.get(slot));
		onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		open = Math.min(1f, open + dt * 6f);
		float grow = Fx.easeOutBack(open);
		int chosen = hovered(mouseX, mouseY);
		var font = this.font;
		HudStyle.begin();
		for (int i = 0; i < SLOTS; i++) {
			hover[i] = Fx.approach(hover[i], i == chosen ? 1f : 0f, dt, SPEED);
			int[] center = slotCenter(i);
			int x = centerX() + Math.round((center[0] - centerX()) * grow);
			int y = centerY() + Math.round((center[1] - centerY()) * grow);
			float size = SLOT_SIZE * (1f + 0.12f * hover[i]);
			boolean filled = i < emotes.size();
			Draw.roundRect(graphics, x - size / 2f, y - size / 2f, size, size, size / 2f, Fx.alpha(Fx.mix(SLOT_BACK, ZyroxTheme.accent(), hover[i] * 0.85f), open));
			Draw.outline(graphics, x - size / 2f, y - size / 2f, size, size, size / 2f, Fx.alpha(0x30FFFFFF, open));
			if (filled) {
				var name = NewsCard.fitted(font, Type.SEMIBOLD, emotes.get(i).name(), SLOT_SIZE + 24);
				Type.SEMIBOLD.centered(graphics, font, name, x, y - 4, Fx.alpha(TEXT, open));
			} else {
				Type.REGULAR.centered(graphics, font, "-", x, y - 4, Fx.alpha(MUTED, open));
			}
		}
		hover[SLOTS] = Fx.approach(hover[SLOTS], chosen == SLOTS ? 1f : 0f, dt, SPEED);
		float center = CENTER_SIZE * (1f + 0.1f * hover[SLOTS]);
		Draw.roundRect(graphics, centerX() - center / 2f, centerY() - center / 2f, center, center, center / 2f, Fx.alpha(Fx.mix(SLOT_BACK, 0xFFD94848, hover[SLOTS] * 0.8f), open));
		Type.SEMIBOLD.centered(graphics, font, "Stop", centerX(), centerY() - 4, Fx.alpha(TEXT, open));
		HudStyle.end();
		var hint = emotes.isEmpty() ? "No emotes yet. Put Blockbench animations into config/zyrox/emotes" : "Release the key over an emote to play it";
		Type.REGULAR.centered(graphics, font, hint, centerX(), centerY() + RADIUS + SLOT_SIZE / 2 + 12, Fx.alpha(MUTED, open));
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (ZyroxClient.emoteKey.matches(event)) {
			var mouse = minecraft.mouseHandler;
			double scale = minecraft.getWindow().getGuiScale();
			int slot = hovered(mouse.xpos() / scale, mouse.ypos() / scale);
			if (slot == SLOTS)
				onClose();
			else
				choose(slot);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		choose(hovered(event.x(), event.y()));
		return true;
	}
}
