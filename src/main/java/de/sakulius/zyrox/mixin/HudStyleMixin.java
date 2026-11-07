package de.sakulius.zyrox.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.sakulius.zyrox.HudStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public class HudStyleMixin {
	@Unique
	private static final String FILL = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V";
	@Unique
	private static final String TEXT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V";
	@Unique
	private static final String SPRITE = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";
	@Unique
	private static final int SCOREBOARD_PADDING = 2;
	@Unique
	private static final int SLOT_INSET = 2;

	@Unique
	private int zyrox$left;
	@Unique
	private int zyrox$top;
	@Unique
	private int zyrox$right;
	@Unique
	private int zyrox$bottom;

	@Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = FILL, ordinal = 0))
	private void zyrox$firstBackground(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		if (!HudStyle.scoreboard) {
			graphics.fill(x0, y0, x1, y1, color);
			return;
		}
		zyrox$left = x0;
		zyrox$top = y0;
		zyrox$right = x1;
		zyrox$bottom = y1;
	}

	@Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = FILL, ordinal = 1))
	private void zyrox$secondBackground(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		if (!HudStyle.scoreboard) {
			graphics.fill(x0, y0, x1, y1, color);
			return;
		}
		int left = Math.min(zyrox$left, x0) - SCOREBOARD_PADDING;
		int top = Math.min(zyrox$top, y0) - SCOREBOARD_PADDING;
		int right = Math.max(zyrox$right, x1) + SCOREBOARD_PADDING;
		int bottom = Math.max(zyrox$bottom, y1) + SCOREBOARD_PADDING;
		HudStyle.panel(graphics, left, top, right - left, bottom - top);
	}

	@Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = TEXT, ordinal = 2))
	private void zyrox$scoreNumber(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, boolean shadow) {
		if (!HudStyle.hideScores)
			graphics.text(font, text, x, y, color, shadow);
	}

	@Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", target = SPRITE, ordinal = 0))
	private void zyrox$hotbar(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		if (HudStyle.hotbar)
			HudStyle.panel(graphics, x, y, w, h);
		else
			graphics.blitSprite(pipeline, sprite, x, y, w, h);
	}

	@Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", target = SPRITE, ordinal = 1))
	private void zyrox$selectedSlot(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		if (HudStyle.hotbar)
			HudStyle.slotHighlight(graphics, x + SLOT_INSET, y + SLOT_INSET, w - SLOT_INSET * 2, h - SLOT_INSET * 2);
		else
			graphics.blitSprite(pipeline, sprite, x, y, w, h);
	}

	@Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", target = SPRITE, ordinal = 2))
	private void zyrox$offhandLeft(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		if (HudStyle.hotbar)
			HudStyle.panel(graphics, x, y + 1, w - 7, h - 2);
		else
			graphics.blitSprite(pipeline, sprite, x, y, w, h);
	}

	@Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", target = SPRITE, ordinal = 3))
	private void zyrox$offhandRight(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		if (HudStyle.hotbar)
			HudStyle.panel(graphics, x + 7, y + 1, w - 7, h - 2);
		else
			graphics.blitSprite(pipeline, sprite, x, y, w, h);
	}
}
