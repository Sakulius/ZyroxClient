package de.sakulius.zyrox.module;

import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;

public class EffectsModule extends HudModule {
	private static final String[] LEVELS = { "", " II", " III", " IV", " V" };
	private static final String EMPTY = "No effects";
	private static final int PADDING = 5;
	private static final float RADIUS = 4f;
	private static final int TICKS_PER_SECOND = 20;

	public EffectsModule(int x, int y) {
		super("effects", "Effects", "Potion effects", false, x, y);
	}

	private static String line(MobEffectInstance effect) {
		int amplifier = effect.getAmplifier();
		String level = amplifier < LEVELS.length ? LEVELS[amplifier] : " " + (amplifier + 1);
		String name = effect.getEffect().value().getDisplayName().getString();
		if (effect.isInfiniteDuration())
			return name + level;
		int seconds = effect.getDuration() / TICKS_PER_SECOND;
		return "%s%s  %d:%02d".formatted(name, level, seconds / 60, seconds % 60);
	}

	private List<String> lines(Minecraft client) {
		var lines = new ArrayList<String>();
		if (client.player != null)
			for (var effect : client.player.getActiveEffects())
				lines.add(line(effect));
		if (lines.isEmpty() && editing(client))
			lines.add(EMPTY);
		return lines;
	}

	@Override
	public int contentWidth(Minecraft client) {
		int width = 0;
		for (var line : lines(client))
			width = Math.max(width, Type.REGULAR.width(client.font, line));
		return width + PADDING * 2;
	}

	@Override
	public int contentHeight(Minecraft client) {
		return lines(client).size() * (client.font.lineHeight + 1) + PADDING * 2;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, Minecraft client, int left, int top) {
		var lines = lines(client);
		if (lines.isEmpty())
			return;
		int bottom = top + contentHeight(client);
		Draw.roundRect(graphics, left, top, contentWidth(client), bottom - top, RADIUS, ZyroxTheme.HUD_BACKGROUND);
		for (int i = 0; i < lines.size(); i++)
			Type.REGULAR.draw(graphics, client.font, lines.get(i), left + PADDING, top + PADDING + i * (client.font.lineHeight + 1), ZyroxTheme.hudText(left, top + i * 10));
	}
}
