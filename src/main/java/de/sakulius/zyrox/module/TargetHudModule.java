package de.sakulius.zyrox.module;

import de.sakulius.zyrox.HudStyle;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Fx;
import de.sakulius.zyrox.screen.Type;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

public class TargetHudModule extends HudModule {
	private static final int WIDTH = 120;
	private static final int HEIGHT = 34;
	private static final int FACE = 24;
	private static final int BAR = 4;
	private static final int FULL = 0xFF57E05B;
	private static final int LOW = 0xFFF2504B;
	private static final int TRACK = 0x40FFFFFF;
	private static final int MUTED = 0xFFB4B8C0;

	private float shownHealth = -1f;
	private LivingEntity last;
	private long lastFrame = System.nanoTime();

	public TargetHudModule(int x, int y) {
		super("target_hud", "Target HUD", "Health of who you fight", Category.PVP, false, x, y);
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
		var target = PvpTracker.target();
		boolean preview = target == null && editing(client);
		if (target == null && !preview)
			return;
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		String name = preview ? "Player" : target.getName().getString();
		float health = preview ? 14f : target.getHealth() + target.getAbsorptionAmount();
		float max = preview ? 20f : Math.max(1f, target.getMaxHealth());
		if (target != last || shownHealth < 0f)
			shownHealth = health;
		last = target;
		shownHealth = Fx.approach(shownHealth, health, dt, 9f);

		HudStyle.panel(graphics, left, top, WIDTH, HEIGHT);
		int textLeft = left + 5;
		if (target instanceof AbstractClientPlayer player) {
			PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), left + 5, top + 5, FACE);
			textLeft = left + FACE + 10;
		}
		int accent = ZyroxTheme.hudText(left, top);
		Type.SEMIBOLD.draw(graphics, client.font, name, textLeft, top + 6, accent);
		var hearts = String.format(Locale.ROOT, "%.1f", health / 2f) + " ❤";
		if (!preview && client.player != null) {
			var distance = String.format(Locale.ROOT, "%.1f m", client.player.distanceTo(target));
			Type.REGULAR.draw(graphics, client.font, distance, left + WIDTH - 5 - Type.REGULAR.width(client.font, distance), top + 6, MUTED);
		}
		Type.REGULAR.draw(graphics, client.font, hearts, textLeft, top + 16, Fx.mix(LOW, FULL, Math.clamp(health / max, 0f, 1f)));
		int barWidth = left + WIDTH - 5 - textLeft;
		float fill = Math.clamp(shownHealth / max, 0f, 1f);
		Draw.roundRect(graphics, textLeft, top + HEIGHT - BAR - 4, barWidth, BAR, BAR / 2f, TRACK);
		Draw.roundRect(graphics, textLeft, top + HEIGHT - BAR - 4, Math.max(BAR, barWidth * fill), BAR, BAR / 2f, Fx.mix(LOW, FULL, Math.clamp(health / max, 0f, 1f)));
	}
}
