package de.sakulius.zyrox.module;

import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.screen.Draw;
import de.sakulius.zyrox.screen.Fx;
import de.sakulius.zyrox.screen.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;

public class ArmorModule extends HudModule {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final int ROW = 17;
	private static final int WIDTH = 48;
	private static final float RADIUS = 4f;
	private static final int FULL = 0xFF57E05B;
	private static final int BROKEN = 0xFFF2504B;
	private static final int UNBREAKABLE = 0xFFFFFFFF;

	public ArmorModule(int x, int y) {
		super("armor", "Armor", "Gear durability", false, x, y);
	}

	@Override
	public int contentWidth(Minecraft client) {
		return WIDTH;
	}

	@Override
	public int contentHeight(Minecraft client) {
		return ROW * SLOTS.length + 2;
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, Minecraft client, int left, int top) {
		var player = client.player;
		if (player == null)
			return;
		boolean any = false;
		for (var slot : SLOTS)
			any |= !player.getItemBySlot(slot).isEmpty();
		if (!any && !editing(client))
			return;

		Draw.roundRect(graphics, left, top, WIDTH, contentHeight(client), RADIUS, ZyroxTheme.HUD_BACKGROUND);
		for (int i = 0; i < SLOTS.length; i++) {
			var stack = player.getItemBySlot(SLOTS[i]);
			if (stack.isEmpty())
				continue;
			int rowTop = top + 1 + i * ROW;
			graphics.item(stack, left + 3, rowTop);
			if (stack.isDamageableItem()) {
				float remaining = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
				Type.REGULAR.draw(graphics, client.font, (int) (remaining * 100) + "%", left + 21, rowTop + 4, Fx.mix(BROKEN, FULL, remaining));
			} else {
				Type.REGULAR.draw(graphics, client.font, "-", left + 21, rowTop + 4, UNBREAKABLE);
			}
		}
	}
}
