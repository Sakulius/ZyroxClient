package de.sakulius.zyrox.screen;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.Model;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

public record PlayerPreviewState(Model.Simple playerModel, Model<Unit> capeModel, Identifier skin, Identifier cape, String hat, float rotationX, float rotationY, int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
	public PlayerPreviewState(Model.Simple playerModel, Model<Unit> capeModel, Identifier skin, Identifier cape, String hat, float rotationX, float rotationY, int x0, int y0, int x1, int y1, float scale, ScreenRectangle scissorArea) {
		this(playerModel, capeModel, skin, cape, hat, rotationX, rotationY, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}
}
