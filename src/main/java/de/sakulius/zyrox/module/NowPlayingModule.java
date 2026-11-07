package de.sakulius.zyrox.module;

import de.sakulius.zyrox.HudStyle;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.music.MusicManager;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Fx;
import de.sakulius.zyrox.screen.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class NowPlayingModule extends HudModule {
	private static final int WIDTH = 150;
	private static final int HEIGHT = 40;
	private static final int PADDING = 7;
	private static final int BAR_HEIGHT = 2;
	private static final float RADIUS = 6f;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFB4B8C0;
	private static final int TRACK = 0x40FFFFFF;
	private static final String ELLIPSIS = "...";

	public NowPlayingModule(int x, int y) {
		super("nowplaying", "Now playing", "Song, artist and progress", Category.MUSIC, false, x, y);
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
		var track = MusicManager.track();
		if (track == null && !editing(client))
			return;
		HudStyle.panel(graphics, left, top, WIDTH, HEIGHT);
		var font = client.font;
		int available = WIDTH - PADDING * 2;
		if (track == null) {
			Type.REGULAR.draw(graphics, font, "Nothing is playing", left + PADDING, top + 16, MUTED);
			return;
		}
		Type.SEMIBOLD.draw(graphics, font, shorten(font, Type.SEMIBOLD, track.title(), available), left + PADDING, top + 7, TEXT);
		Type.REGULAR.draw(graphics, font, shorten(font, Type.REGULAR, track.artist(), available), left + PADDING, top + 18, MUTED);
		int barTop = top + HEIGHT - PADDING - BAR_HEIGHT + 1;
		Draw.roundRect(graphics, left + PADDING, barTop, available, BAR_HEIGHT, 1f, TRACK);
		if (track.durationMs() > 0) {
			float progress = Math.clamp((float) MusicManager.positionMs() / track.durationMs(), 0f, 1f);
			Draw.roundRect(graphics, left + PADDING, barTop, Math.max(BAR_HEIGHT, available * progress), BAR_HEIGHT, 1f, Fx.alpha(ZyroxTheme.hudAccent(left, top), track.playing() ? 1f : 0.5f));
		}
	}

	public static String shorten(Font font, Type type, String text, int available) {
		if (type.width(font, text) <= available)
			return text;
		int length = text.length();
		while (length > 0 && type.width(font, text.substring(0, length) + ELLIPSIS) > available)
			length--;
		return text.substring(0, length).stripTrailing() + ELLIPSIS;
	}
}
