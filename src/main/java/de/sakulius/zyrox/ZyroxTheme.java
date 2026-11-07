package de.sakulius.zyrox;

import de.sakulius.zyrox.screen.Fx;
import java.util.List;

public final class ZyroxTheme {
	public record Choice(String name, int color) {
	}

	public static final List<Choice> CHOICES = List.of(
		new Choice("Green", 0xFF57B847),
		new Choice("Blue", 0xFF4FA3F7),
		new Choice("Purple", 0xFFA98BFA),
		new Choice("Orange", 0xFFF5A342),
		new Choice("Red", 0xFFF2716B),
		new Choice("Pink", 0xFFF27FC0),
		new Choice("Teal", 0xFF3FCBD6),
		new Choice("Chroma", 0));

	private static final int CHROMA_INDEX = CHOICES.size() - 1;
	private static final float CHROMA_SPEED = 0.12f;

	public static int index;
	public static boolean chromaHud;
	public static boolean cinematic = true;

	private ZyroxTheme() {
	}

	public static float time() {
		return (System.currentTimeMillis() % 3_600_000L) / 1000f;
	}

	public static boolean isChroma() {
		return index == CHROMA_INDEX;
	}

	public static int chroma(float offset) {
		return Fx.hsv(time() * CHROMA_SPEED + offset, 0.75f, 1f);
	}

	public static int accent(float offset) {
		return isChroma() ? chroma(offset) : Fx.shiftHue(CHOICES.get(index).color(), (float) Math.sin(offset * Math.PI * 2) * 0.05f);
	}

	public static int accent() {
		return accent(0);
	}

	public static int hudAccent(int x, int y) {
		return chromaHud ? chroma((x + y) * 0.003f) : accent();
	}

	public static int hudText(int x, int y) {
		return chromaHud ? chroma((x + y) * 0.003f) : HudStyle.value();
	}
}
