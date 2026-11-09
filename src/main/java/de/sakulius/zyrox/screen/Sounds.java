package de.sakulius.zyrox.screen;

import de.sakulius.zyrox.ZyroxClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum Sounds {
	HOVER("hover", 0.5f),
	CLICK("click", 0.8f),
	TOGGLE_ON("toggle_on", 0.8f),
	TOGGLE_OFF("toggle_off", 0.8f),
	OPEN("open", 0.9f),
	CLOSE("close", 0.8f),
	SLIDE("slide", 0.7f),
	TAB("tab", 0.7f),
	LOCKED("locked", 0.8f),
	TYPE("type", 0.5f),
	EQUIP("equip", 0.8f);

	public static final List<String> VOLUMES = List.of("Off", "Quiet", "Normal", "Loud");
	private static final float[] LEVELS = { 0f, 0.5f, 1f, 1.5f };
	private static final long HOVER_GAP_MS = 35;

	public static int volume = 2;
	public static boolean classic;
	private static long lastHover;

	private final SoundEvent event;
	private final float loudness;

	Sounds(String name, float loudness) {
		this.event = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, "ui." + name));
		this.loudness = loudness;
	}

	public void play() {
		play(1f);
	}

	public void play(float pitch) {
		float level = LEVELS[Math.clamp(volume, 0, LEVELS.length - 1)];
		if (level <= 0f)
			return;
		if (this == HOVER) {
			long now = System.currentTimeMillis();
			if (now - lastHover < HOVER_GAP_MS)
				return;
			lastHover = now;
		}
		var sound = classic ? SoundEvents.UI_BUTTON_CLICK.value() : event;
		float classicPitch = switch (this) {
			case TOGGLE_ON, OPEN, EQUIP -> 1.5f;
			case TOGGLE_OFF, CLOSE, LOCKED -> 0.8f;
			case HOVER, TYPE, SLIDE -> 1.8f;
			default -> 1.2f;
		};
		if (classic && (this == HOVER || this == TYPE))
			return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, classic ? classicPitch : pitch, loudness * level * (classic ? 0.5f : 1f)));
	}
}
