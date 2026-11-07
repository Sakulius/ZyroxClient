package de.sakulius.zyrox.mixin;

import de.sakulius.zyrox.module.MenuSplash;
import de.sakulius.zyrox.screen.NewsCard;
import de.sakulius.zyrox.screen.PlayerPreviewWidget;
import de.sakulius.zyrox.screen.ZyroxMenuButton;
import de.sakulius.zyrox.screen.ZyroxScreen;
import de.sakulius.zyrox.screen.ZyroxTitle;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	@Unique
	private static final int ZYROX_WIDE_BUTTON = 90;

	@Shadow
	private SplashRenderer splash;

	@Shadow
	private boolean fading;

	@Shadow
	private long fadeInStart;

	@Unique
	private SplashRenderer zyrox$gameSplash;

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void zyrox$init(CallbackInfo ci) {
		if (zyrox$gameSplash == null)
			zyrox$gameSplash = splash;
		var wide = new ArrayList<Button>();
		var small = new ArrayList<AbstractWidget>();
		for (var child : children()) {
			if (child instanceof PlainTextButton)
				continue;
			if (child instanceof Button button && button.getWidth() >= ZYROX_WIDE_BUTTON)
				wide.add(button);
			else if (child instanceof AbstractWidget widget && widget.getWidth() < ZYROX_WIDE_BUTTON)
				small.add(widget);
		}
		Comparator<AbstractWidget> reading = Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX);
		wide.sort(reading);
		small.sort(reading);

		int top = ZyroxTitle.columnTop(height, wide.size());
		ZyroxTitle.layout(top);
		int y = top + ZyroxTitle.HEADER_HEIGHT;
		for (var button : wide) {
			removeWidget(button);
			addRenderableWidget(new ZyroxMenuButton(button, ZyroxTitle.COLUMN_X, y, ZyroxTitle.COLUMN_WIDTH, ZyroxTitle.BUTTON_HEIGHT));
			y += ZyroxTitle.BUTTON_STEP;
		}
		int x = ZyroxTitle.COLUMN_X;
		for (var widget : small) {
			widget.setX(x);
			widget.setY(y);
			x += ZyroxTitle.ICON_STEP;
		}
		zyrox$rightSide();
	}

	@Unique
	private void zyrox$rightSide() {
		int cardWidth = Math.clamp(Math.round(width * ZyroxTitle.NEWS_SHARE), ZyroxTitle.NEWS_MIN_WIDTH, ZyroxTitle.NEWS_MAX_WIDTH);
		int cardHeight = cardWidth * 9 / 16;
		int left = width - ZyroxTitle.SIDE_MARGIN - cardWidth;
		int top = ZyroxTitle.SIDE_MARGIN;
		addRenderableWidget(new NewsCard(this, left, top, cardWidth, cardHeight));
		int buttonsY = top + cardHeight + ZyroxTitle.SIDE_GAP;
		int half = (cardWidth - ZyroxTitle.SIDE_GAP) / 2;
		addRenderableWidget(new ZyroxMenuButton(Component.literal("Cosmetics"), left, buttonsY, cardWidth, ZyroxTitle.BUTTON_HEIGHT, input -> minecraft.setScreen(ZyroxScreen.cosmeticsFrom(this)), true));
		int modelTop = buttonsY + ZyroxTitle.BUTTON_HEIGHT + ZyroxTitle.SIDE_GAP;
		int modelHeight = Math.min(height - ZyroxTitle.MODEL_BOTTOM - modelTop, Math.round(height * ZyroxTitle.MODEL_SHARE));
		if (modelHeight >= ZyroxTitle.MODEL_MIN_HEIGHT) {
			int modelWidth = modelHeight * 3 / 4;
			addRenderableWidget(new PlayerPreviewWidget(left + (cardWidth - modelWidth) / 2, height - ZyroxTitle.MODEL_BOTTOM - modelHeight, modelWidth, modelHeight));
		}
	}

	@Inject(method = "realmsNotificationsEnabled", at = @At("HEAD"), cancellable = true)
	private void zyrox$noRealmsIcons(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void zyrox$useText(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		var custom = MenuSplash.custom();
		splash = custom != null ? custom : zyrox$gameSplash;
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void zyrox$background(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ZyroxTitle.background(graphics, width, height, 1f);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
	private void zyrox$logo(LogoRenderer logo, GuiGraphics graphics, int width, float alpha) {
		ZyroxTitle.logo(graphics, alpha);
		ZyroxTitle.version(graphics, font, height, alpha);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;F)V"))
	private void zyrox$splash(SplashRenderer renderer, GuiGraphics graphics, int width, Font font, float alpha) {
		ZyroxTitle.splash(graphics, font, ((SplashRendererAccessor) renderer).zyrox$text(), alpha);
	}
}
