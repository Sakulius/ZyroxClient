package de.sakulius.zyrox.music;

import com.mojang.blaze3d.vertex.PoseStack;
import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.module.Modules;
import de.sakulius.zyrox.screen.CameraRig;
import de.sakulius.zyrox.screen.Fx;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class SkyLyrics {
	public static final List<String> SIZES = List.of("Small", "Medium", "Large");
	private static final float[] SIZE_SCALES = { 0.75f, 1f, 1.4f };

	private static final Style FONT = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "sky")));
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final double DISTANCE = 16.0;
	private static final double HEIGHT = 6.0;
	private static final double GLIDE_DISTANCE = 26.0;
	private static final float VIEW_ZONE = 14f;
	private static final float WORLD_SCALE = 0.06f;
	private static final float MAX_WIDTH_BLOCKS = 24f;
	private static final float LINE_SPACING = 32f;
	private static final float SIDE_LINE_SCALE = 0.58f;
	private static final float SIDE_LINE_ALPHA = 0.55f;
	private static final float BOB_BLOCKS = 0.18f;
	private static final float BOB_SPEED = 1.3f;
	private static final float FOLLOW_SPEED = 5f;
	private static final float POSITION_SPEED = 6f;
	private static final float SCROLL_SPEED = 9f;
	private static final float FADE_SPEED = 7f;
	private static final float PAUSED_ALPHA = 0.5f;
	private static final float MESSAGE_SECONDS = 4f;
	private static final float MIN_VISIBLE = 0.02f;
	private static final long MIN_LINE_MS = 900;
	private static final long MS_PER_CHARACTER = 95;
	private static final int TEXT = 0xFFFFFF;
	private static final int UNSUNG = 0xB9BDC6;
	private static final int OPAQUE = 0xFF000000;
	private static final int OUTLINE = 0xFF10121A;
	private static final String[] DOTS = { "•", "• •", "• • •" };

	private static float yaw;
	private static Vec3 anchor;
	private static float scroll;
	private static List<Lyrics.Line> scrollLines = List.of();
	private static float visible;
	private static long lastFrame = System.nanoTime();

	private SkyLyrics() {
	}

	private static boolean wanted(Minecraft client) {
		var track = MusicManager.track();
		return MusicManager.skyLyrics && Modules.LYRICS.enabled && track != null && client.player != null && !CameraRig.hidesHud()
			&& (MusicManager.state() == MusicManager.LyricsState.READY || message() != null);
	}

	private static String message() {
		if (MusicManager.state() == MusicManager.LyricsState.NOT_FOUND && MusicManager.secondsSinceTrackChange() < MESSAGE_SECONDS)
			return "No synced lyrics for this song";
		return null;
	}

	public static void render(LevelRenderContext context) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;
		var client = Minecraft.getInstance();
		boolean wanted = wanted(client);
		visible = Fx.approach(visible, wanted ? 1f : 0f, dt, FADE_SPEED);
		if (visible < MIN_VISIBLE || client.player == null) {
			anchor = null;
			return;
		}
		var track = MusicManager.track();
		if (track == null)
			return;
		updateAnchor(client, dt);

		var camera = client.gameRenderer.mainCamera();
		var offset = anchor.subtract(camera.position());
		float bob = (float) Math.sin(ZyroxTheme.time() * BOB_SPEED) * BOB_BLOCKS;
		float alpha = visible * (track.playing() ? 1f : PAUSED_ALPHA);
		float size = WORLD_SCALE * SIZE_SCALES[Math.clamp(MusicManager.skySize, 0, SIZE_SCALES.length - 1)];

		var pose = context.poseStack();
		pose.pushPose();
		pose.translate(offset.x, offset.y + bob, offset.z);
		pose.rotate(camera.rotation());

		var message = message();
		var lines = MusicManager.lines();
		if (lines.isEmpty()) {
			if (message != null)
				line(context, client.font, pose, styled(message, UNSUNG), 0f, size * SIDE_LINE_SCALE, alpha);
			pose.popPose();
			return;
		}

		long position = MusicManager.positionMs() + MusicManager.offsetMs;
		int index = Lyrics.indexAt(lines, position);
		if (lines != scrollLines) {
			scrollLines = lines;
			scroll = index;
		}
		scroll = Fx.approach(scroll, index, dt, SCROLL_SPEED);

		int first = (int) Math.floor(scroll) - 1;
		for (int i = first; i <= first + 3; i++) {
			if (i < -1 || i >= lines.size())
				continue;
			float distance = i - scroll;
			float near = Math.max(0f, 1f - Math.abs(distance));
			float scale = size * Mth.lerp(near, SIDE_LINE_SCALE, 1f);
			float lineAlpha = alpha * Mth.lerp(near, SIDE_LINE_ALPHA, 1f) * Math.clamp(2f - Math.abs(distance) * 1.2f, 0f, 1f);
			float y = -distance * LINE_SPACING * size;
			Component text;
			if (i < 0 || lines.get(i).text().isBlank())
				text = styled(DOTS[(int) (ZyroxTheme.time() * 2f) % DOTS.length], TEXT);
			else
				text = i == index && MusicManager.karaoke ? sung(lines, i, position) : styled(lines.get(i).text(), i == index ? TEXT : UNSUNG);
			line(context, client.font, pose, text, y, scale, lineAlpha);
		}
		pose.popPose();
	}

	private static void updateAnchor(Minecraft client, float dt) {
		var player = client.player;
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		var eye = player.getEyePosition(partial);
		float view = player.getViewYRot(partial);
		boolean first = anchor == null;
		if (first)
			yaw = view;

		Vec3 target;
		if (player.isFallFlying()) {
			target = eye.add(player.getLookAngle().scale(GLIDE_DISTANCE));
			yaw = view;
		} else {
			float away = Mth.wrapDegrees(view - yaw);
			if (Math.abs(away) > VIEW_ZONE)
				yaw += (away - Math.signum(away) * VIEW_ZONE) * (1f - (float) Math.exp(-FOLLOW_SPEED * dt));
			double angle = Math.toRadians(yaw);
			target = eye.add(-Math.sin(angle) * DISTANCE, HEIGHT, Math.cos(angle) * DISTANCE);
		}
		anchor = first ? target : anchor.lerp(target, 1f - (float) Math.exp(-POSITION_SPEED * dt));
	}

	private static Component sung(List<Lyrics.Line> lines, int index, long position) {
		var line = lines.get(index);
		String text = line.text();
		long estimated = MIN_LINE_MS + text.length() * MS_PER_CHARACTER;
		long length = index + 1 < lines.size() ? Math.min(estimated, lines.get(index + 1).timeMs() - line.timeMs()) : estimated;
		float progress = Math.clamp((float) (position - line.timeMs()) / Math.max(1L, length), 0f, 1f);
		int split = Math.round(text.length() * progress);
		int done = Fx.mix(0xFF000000 | TEXT, 0xFF000000 | ZyroxTheme.accent(), 0.45f) & 0xFFFFFF;
		MutableComponent result = styled(text.substring(0, split), done);
		return result.append(styled(text.substring(split), UNSUNG));
	}

	private static MutableComponent styled(String text, int color) {
		return Component.literal(text).withStyle(FONT.withColor(color));
	}

	private static void line(LevelRenderContext context, Font font, PoseStack pose, Component text, float y, float scale, float alpha) {
		if (alpha < MIN_VISIBLE)
			return;
		int width = font.width(text);
		float fitted = Math.min(scale, MAX_WIDTH_BLOCKS / Math.max(1, width));
		pose.pushPose();
		pose.translate(0f, y, 0f);
		pose.scale(fitted, -fitted, fitted);
		int color = Fx.alpha(OPAQUE | TEXT, alpha);
		context.submitNodeCollector().submitText(pose, -width / 2f, 0f, text.getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH, FULL_BRIGHT, color, 0, Fx.alpha(OUTLINE, alpha));
		pose.popPose();
	}
}
