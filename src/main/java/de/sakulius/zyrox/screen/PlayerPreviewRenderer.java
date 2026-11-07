package de.sakulius.zyrox.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.sakulius.zyrox.module.WearableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;

public class PlayerPreviewRenderer extends PictureInPictureRenderer<PlayerPreviewState> {
	private static final int FULL_LIGHT = 15728880;
	private static final float MODEL_OFFSET = -1.601f;
	private static final float PIVOT = 1.0625f;

	public PlayerPreviewRenderer(MultiBufferSource.BufferSource buffers) {
		super(buffers);
	}

	@Override
	public Class<PlayerPreviewState> getRenderStateClass() {
		return PlayerPreviewState.class;
	}

	@Override
	protected void renderToTexture(PlayerPreviewState state, PoseStack pose) {
		var client = Minecraft.getInstance();
		client.gameRenderer.getLighting().setupFor(Lighting.Entry.PLAYER_SKIN);
		float size = state.scale() * client.getWindow().getGuiScale();
		var view = RenderSystem.getModelViewStack();
		view.pushMatrix();
		view.rotateAround(Axis.XP.rotationDegrees(state.rotationX()), 0f, size * PIVOT, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(-state.rotationY()));
		pose.translate(0f, MODEL_OFFSET, 0f);
		var player = state.playerModel();
		player.renderToBuffer(pose, bufferSource.getBuffer(player.renderType(state.skin())), FULL_LIGHT, OverlayTexture.NO_OVERLAY);
		if (state.cape() != null && state.capeModel() != null) {
			var cape = state.capeModel();
			cape.setupAnim(Unit.INSTANCE);
			cape.renderToBuffer(pose, bufferSource.getBuffer(cape.renderType(state.cape())), FULL_LIGHT, OverlayTexture.NO_OVERLAY);
		}
		if (state.hat() != null) {
			var hat = WearableModel.of(state.hat());
			if (hat.texture() != null) {
				pose.pushPose();
				player.root().translateAndRotate(pose);
				player.root().getChild("head").translateAndRotate(pose);
				CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
				hat.place(pose);
				hat.draw(pose.last(), bufferSource.getBuffer(RenderTypes.entityCutout(hat.texture())), FULL_LIGHT);
				pose.popPose();
			}
		}
		bufferSource.endBatch();
		view.popMatrix();
	}

	@Override
	protected String getTextureLabel() {
		return "zyrox player preview";
	}
}
