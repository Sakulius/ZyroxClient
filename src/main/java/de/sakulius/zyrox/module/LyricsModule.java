package de.sakulius.zyrox.module;

import de.sakulius.zyrox.HudStyle;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.music.Lyrics;
import de.sakulius.zyrox.music.MusicManager;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Fx;
import de.sakulius.zyrox.screen.Type;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class LyricsModule extends HudModule {
	private static final int WIDTH = 280;
	private static final int HEIGHT = 58;
	private static final int LINE_HEIGHT = 19;
	private static final int SIDE_PADDING = 8;
	private static final float RADIUS = 8f;
	private static final float SCROLL_SPEED = 10f;
	private static final float PAUSED_ALPHA = 0.5f;
	private static final float MESSAGE_SECONDS = 4f;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFB4B8C0;
	private static final long MIN_LINE_MS = 900;
	private static final long MS_PER_CHARACTER = 95;
	private static final int DOTS = 3;

	private float scroll = -1f;
	private long lastFrame = System.nanoTime();
	private List<Lyrics.Line> scrollLines = List.of();

	public LyricsModule(int x, int y) {
		super("lyrics", "Synced lyrics", "Sing along while you play", Category.MUSIC, true, x, y);
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
	protected void render(GuiGraphics graphics, Minecraft client, int left, int top) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;

		if (MusicManager.skyLyrics) {
			if (editing(client)) {
				HudStyle.panel(graphics, left, top + HEIGHT / 2f - 10, WIDTH, 20);
				Type.REGULAR.centered(graphics, client.font, "Lyrics are floating in the sky", left + WIDTH / 2, top + HEIGHT / 2 - 4, MUTED);
			}
			return;
		}
		var track = MusicManager.track();
		var state = MusicManager.state();
		String message = null;
		if (track == null || state == MusicManager.LyricsState.NONE) {
			if (editing(client))
				message = "Lyrics show up here";
		} else if (state == MusicManager.LyricsState.LOADING) {
			message = "Finding lyrics";
		} else if (state == MusicManager.LyricsState.NOT_FOUND && (editing(client) || MusicManager.secondsSinceTrackChange() < MESSAGE_SECONDS)) {
			message = "No synced lyrics for this song";
		}
		if (message != null) {
			HudStyle.panel(graphics, left, top + HEIGHT / 2f - 10, WIDTH, 20);
			Type.REGULAR.centered(graphics, client.font, message, left + WIDTH / 2, top + HEIGHT / 2 - 4, MUTED);
			return;
		}
		var lines = MusicManager.lines();
		if (track == null || lines.isEmpty())
			return;

		long position = MusicManager.positionMs() + MusicManager.offsetMs;
		int index = Lyrics.indexAt(lines, position);
		if (lines != scrollLines) {
			scrollLines = lines;
			scroll = index;
		}
		scroll = Fx.approach(scroll, index, dt, SCROLL_SPEED);
		float alpha = track.playing() ? 1f : PAUSED_ALPHA;

		HudStyle.panel(graphics, left, top, WIDTH, HEIGHT, alpha);
		graphics.enableScissor(left, top + 1, left + WIDTH, top + HEIGHT - 1);
		int first = (int) Math.floor(scroll) - 1;
		for (int i = first; i <= first + 3; i++) {
			if (i < -1 || i >= lines.size())
				continue;
			float distance = i - scroll;
			float near = Math.max(0f, 1f - Math.abs(distance));
			float y = top + HEIGHT / 2f + distance * LINE_HEIGHT;
			float lineAlpha = alpha * Math.clamp(1f - Math.abs(distance) * 0.45f, 0f, 1f);
			if (i < 0 || lines.get(i).text().isBlank()) {
				dots(graphics, left + WIDTH / 2f, y, lineAlpha * Math.max(0.35f, near));
				continue;
			}
			float progress = i == index && MusicManager.karaoke ? lineProgress(lines, i, position) : -1f;
			line(graphics, client, lines.get(i).text(), left, (int) y, near, lineAlpha, progress);
		}
		graphics.disableScissor();
	}

	private static float lineProgress(List<Lyrics.Line> lines, int index, long position) {
		var line = lines.get(index);
		long estimated = MIN_LINE_MS + line.text().length() * MS_PER_CHARACTER;
		long length = index + 1 < lines.size() ? Math.min(estimated, lines.get(index + 1).timeMs() - line.timeMs()) : estimated;
		return Math.clamp((float) (position - line.timeMs()) / Math.max(1L, length), 0f, 1f);
	}

	private static void line(GuiGraphics graphics, Minecraft client, String text, int left, int centerY, float near, float alpha, float progress) {
		var font = client.font;
		if (near > 0.02f)
			fitted(graphics, font, largest(font, text), text, left, centerY, Fx.alpha(TEXT, alpha * near), progress);
		if (near < 0.98f)
			fitted(graphics, font, Type.REGULAR, text, left, centerY, Fx.alpha(MUTED, alpha * (1f - near)), -1f);
	}

	private static Type largest(Font font, String text) {
		int available = WIDTH - SIDE_PADDING * 2;
		if (Type.LARGE.width(font, text) <= available)
			return Type.LARGE;
		return Type.SEMIBOLD;
	}

	private static void fitted(GuiGraphics graphics, Font font, Type type, String text, int left, int centerY, int color, float progress) {
		int available = WIDTH - SIDE_PADDING * 2;
		int width = type.width(font, text);
		int textHeight = type == Type.LARGE ? 13 : 8;
		float scale = width > available ? (float) available / width : 1f;
		int centerX = left + WIDTH / 2;
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.scale(scale, scale);
		type.centered(graphics, font, text, 0, -textHeight / 2, progress >= 0f ? Fx.alpha(color, 0.45f) : color);
		pose.popMatrix();
		if (progress <= 0f)
			return;

		int textLeft = centerX - (int) (width * scale / 2);
		graphics.enableScissor(textLeft - 1, centerY - 12, textLeft + (int) (width * scale * progress), centerY + 12);
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.scale(scale, scale);
		type.centered(graphics, font, text, 0, -textHeight / 2, Fx.mix(color, Fx.alpha(ZyroxTheme.hudAccent(left, centerY), (color >>> 24) / 255f), 0.35f));
		pose.popMatrix();
		graphics.disableScissor();
	}

	private static void dots(GuiGraphics graphics, float centerX, float centerY, float alpha) {
		float time = ZyroxTheme.time();
		for (int i = 0; i < DOTS; i++) {
			float pulse = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(time * 4f - i * 0.8f));
			float x = centerX + (i - 1) * 9f;
			Draw.roundRect(graphics, x - 2f, centerY - 2f, 4f, 4f, 2f, Fx.alpha(TEXT, alpha * pulse));
		}
	}
}
