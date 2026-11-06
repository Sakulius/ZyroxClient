package de.sakulius.zyrox.module;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public class WearableLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public WearableLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
		var client = Minecraft.getInstance();
		var entity = client.level == null ? null : client.level.getEntity(state.id);
		if (state.isInvisible || entity == null)
			return;
		boolean self = entity == client.player;
		if (state.headEquipment.isEmpty())
			wear(pose, collector, light, Cosmetics.hat(entity.getUUID(), self));
	}

	private void wear(PoseStack pose, SubmitNodeCollector collector, int light, Cosmetics.Wearable wearable) {
		if (wearable == null)
			return;
		var model = WearableModel.of(wearable.id());
		if (model.texture() == null)
			return;
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().head.translateAndRotate(pose);
		CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
		model.place(pose);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(model.texture()), (last, consumer) -> model.draw(last, consumer, light));
		pose.popPose();
	}
}
