package de.sakulius.zyrox.module;

import de.sakulius.zyrox.HudStyle;
import de.sakulius.zyrox.ZyroxTheme;
import de.sakulius.zyrox.screen.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemCounterModule extends HudModule {
	private record Group(ItemStack icon, Predicate<ItemStack> counts) {
	}

	private record Line(ItemStack icon, String count) {
	}

	private static final int HEIGHT = 18;
	private static final int ICON = 16;
	private static final int GAP = 6;
	private static final int PADDING = 3;
	private static final float RADIUS = 4f;

	private List<Group> groups;

	public ItemCounterModule(int x, int y) {
		super("item_counter", "Items", "Potions, pearls and more", Category.PVP, false, x, y);
	}

	private List<Group> groups() {
		if (groups == null) {
			groups = List.of(
				new Group(new ItemStack(Items.SPLASH_POTION), stack -> stack.is(Items.SPLASH_POTION)),
				new Group(new ItemStack(Items.GOLDEN_APPLE), stack -> stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)),
				new Group(new ItemStack(Items.ENDER_PEARL), stack -> stack.is(Items.ENDER_PEARL)),
				new Group(new ItemStack(Items.TOTEM_OF_UNDYING), stack -> stack.is(Items.TOTEM_OF_UNDYING)),
				new Group(new ItemStack(Items.ARROW), stack -> stack.is(ItemTags.ARROWS)));
		}
		return groups;
	}

	private List<Line> lines(Minecraft client) {
		var lines = new ArrayList<Line>();
		boolean showAll = editing(client);
		var player = client.player;
		for (var group : groups()) {
			int count = 0;
			if (player != null) {
				for (var stack : player.getInventory().getNonEquipmentItems())
					if (group.counts().test(stack))
						count += stack.getCount();
				if (group.counts().test(player.getOffhandItem()))
					count += player.getOffhandItem().getCount();
			}
			if (count > 0 || showAll)
				lines.add(new Line(group.icon(), Integer.toString(count)));
		}
		return lines;
	}

	@Override
	public int contentWidth(Minecraft client) {
		int width = PADDING * 2;
		for (var line : lines(client))
			width += ICON + 1 + Type.REGULAR.width(client.font, line.count()) + GAP;
		return Math.max(width - GAP, ICON + PADDING * 2);
	}

	@Override
	public int contentHeight(Minecraft client) {
		return HEIGHT;
	}

	@Override
	protected void render(GuiGraphics graphics, Minecraft client, int left, int top) {
		var lines = lines(client);
		if (lines.isEmpty())
			return;
		HudStyle.panel(graphics, left, top, contentWidth(client), HEIGHT);
		int x = left + PADDING;
		for (var line : lines) {
			graphics.renderFakeItem(line.icon(), x, top + 1);
			x += ICON + 1;
			Type.REGULAR.draw(graphics, client.font, line.count(), x, top + 5, ZyroxTheme.hudText(left, top));
			x += Type.REGULAR.width(client.font, line.count()) + GAP;
		}
	}
}
