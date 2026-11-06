package de.sakulius.zyrox.screen;

import de.sakulius.zyrox.ZyroxClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public enum Type {
	REGULAR("regular"),
	SEMIBOLD("semibold"),
	LARGE("large");

	private static final int MAX_SCALE = 4;

	private final Style[] styles = new Style[MAX_SCALE];

	Type(String name) {
		for (int i = 0; i < MAX_SCALE; i++) {
			var id = Identifier.fromNamespaceAndPath(ZyroxClient.MOD_ID, name + "_" + (i + 1));
			styles[i] = Style.EMPTY.withFont(new FontDescription.Resource(id));
		}
	}

	public Component of(String text) {
		int scale = Math.clamp(Draw.scale(), 1, MAX_SCALE);
		return Component.literal(text).withStyle(styles[scale - 1]);
	}

	public int width(Font font, String text) {
		return font.width(of(text));
	}

	public void draw(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, of(text), x, y, color, false);
	}

	public void centered(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		draw(graphics, font, text, x - width(font, text) / 2, y, color);
	}
}
