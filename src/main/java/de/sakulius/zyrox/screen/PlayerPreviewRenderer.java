package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.sakulius.zyrox.module.WearableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;

public class PlayerPreviewRenderer extends PictureInPictureRenderer<PlayerPreviewState> {
	private static final int FULL_LIGHT = 15728880;
	private static final float MODEL_OFFSET = -1.601f;
	private static final float PIVOT = 1.0625f;

	@Override
	public Class<PlayerPreviewState> getRenderStateClass() {
		return PlayerPreviewState.class;
	}

	@Override
	protected void renderToTexture(PlayerPreviewState state, PoseStack pose, SubmitNodeCollector collector) {
		var client = Minecraft.getInstance();
		client.gameRenderer.lighting().setupFor(Lighting.Entry.PLAYER_SKIN);
		float size = state.scale() * client.gameRenderer.gameRenderState().windowRenderState.guiScale;
		RenderSystem.getModelViewStack().rotateAround(Axis.XP.rotationDegrees(state.rotationX()), 0f, size * PIVOT, 0f);
		pose.rotateDegrees(Axis.YP, -state.rotationY());
		pose.translate(0f, MODEL_OFFSET, 0f);
		collector.submitModel(state.playerModel(), Unit.INSTANCE, pose, state.skin(), FULL_LIGHT, OverlayTexture.NO_OVERLAY, 0);
		if (state.cape() != null && state.capeModel() != null) {
			state.capeModel().setupAnim(Unit.INSTANCE);
			collector.submitModel(state.capeModel(), Unit.INSTANCE, pose, state.cape(), FULL_LIGHT, OverlayTexture.NO_OVERLAY, 0);
		}
		if (state.hat() == null)
			return;
		var hat = WearableModel.of(state.hat());
		if (hat.texture() == null)
			return;
		pose.pushPose();
		state.playerModel().root().translateAndRotate(pose);
		state.playerModel().root().getChild("head").translateAndRotate(pose);
		CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
		hat.place(pose);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(hat.texture()), (last, consumer) -> hat.draw(last, consumer, FULL_LIGHT));
		pose.popPose();
	}

	@Override
	protected String getTextureLabel() {
		return "zyrox player preview";
	}
}
