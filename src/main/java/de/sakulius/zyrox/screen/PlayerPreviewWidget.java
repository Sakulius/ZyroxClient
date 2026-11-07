package de.sakulius.zyrox.screen;

import de.sakulius.zyrox.module.Cosmetics;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

public class PlayerPreviewWidget extends AbstractWidget {
	private static final float FIT = 0.9f;
	private static final float MODEL_HEIGHT = 2.125f;
	private static final float START_X = -5f;
	private static final float START_Y = 28f;
	private static final float DRAG_SPEED = 2.5f;
	private static final float TILT_LIMIT = 50f;
	private static final float CAPE_LEAN = (float) Math.toRadians(6);

	private static Model.Simple wide;
	private static Model.Simple slim;
	private static Model<Unit> cape;

	private final Supplier<PlayerSkin> skin;
	private float rotationX = START_X;
	private float rotationY = START_Y;

	public PlayerPreviewWidget(int x, int y, int width, int height) {
		super(x, y, width, height, CommonComponents.EMPTY);
		var client = Minecraft.getInstance();
		skin = client.getSkinManager().createLookup(client.getGameProfile(), false);
		if (wide == null) {
			var models = client.getEntityModels();
			wide = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
			slim = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
			cape = new Model<>(models.bakeLayer(ModelLayers.PLAYER_CAPE), RenderTypes::entitySolid) {
				@Override
				public void setupAnim(Unit state) {
					super.setupAnim(state);
					var part = root().getChild("body").getChild("cape");
					part.yRot = 0f;
					part.xRot = CAPE_LEAN;
				}
			};
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		var own = skin.get();
		var id = Minecraft.getInstance().getGameProfile().id();
		var dressed = Cosmetics.apply(id, true, own);
		var hat = Cosmetics.hat(id, true);
		float scale = FIT * getHeight() / MODEL_HEIGHT;
		graphics.guiRenderState.addPicturesInPictureState(new PlayerPreviewState(
			own.model() == PlayerModelType.SLIM ? slim : wide, cape, own.body().texturePath(),
			dressed.cape() == null ? null : dressed.cape().texturePath(), hat == null ? null : hat.id(),
			rotationX, rotationY, getX(), getY(), getRight(), getBottom(), scale, null));
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		rotationX = Mth.clamp(rotationX - (float) dragY * DRAG_SPEED, -TILT_LIMIT, TILT_LIMIT);
		rotationY += (float) dragX * DRAG_SPEED;
	}

	@Override
	public void playDownSound(net.minecraft.client.sounds.SoundManager sounds) {
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
