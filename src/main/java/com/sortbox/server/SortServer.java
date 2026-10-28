package com.sortbox.server;

import com.sortbox.SortBox;
import com.sortbox.networking.SortActionPayload;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-side sorting logic. */
public final class SortServer {
	private SortServer() {}

	/** Category buckets for grouping: tools/weapons, blocks, food, misc. */
	private static final int CAT_TOOL = 0;
	private static final int CAT_BLOCK = 1;
	private static final int CAT_FOOD = 2;
	private static final int CAT_MISC = 3;

	public static void handle(ServerPlayer player, SortActionPayload payload) {
		switch (payload.op()) {
			case SortActionPayload.OP_SORT_INV -> sortPlayerInventory(player);
			case SortActionPayload.OP_SORT_CONTAINER -> sortOpenContainer(player);
			case SortActionPayload.OP_STORE_ALL -> storeAll(player);
			case SortActionPayload.OP_TAKE_ALL -> takeAll(player);
			default -> {
			}
		}
	}

	// ---------- player inventory ----------

	private static void sortPlayerInventory(ServerPlayer player) {
		Inventory inv = player.getInventory();
		List<Integer> indices = new ArrayList<>();
		int size = inv.getNonEquipmentItems().size();
		for (int i = 9; i < size; i++) {
			indices.add(i); // keep hotbar 0-8 untouched
		}
		List<ItemStack> stacks = new ArrayList<>();
		for (int i : indices) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty()) {
				stacks.add(st);
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		mergeAndSort(stacks);
		distribute(stacks, (i, stack) -> inv.setItem(indices.get(i), stack), indices.size());
		inv.setChanged();
		player.containerMenu.sendAllDataToRemote();
	}

	// ---------- open container ----------

	/** The container the player is looking at (the chest), not their own inventory. */
	private static Target findTarget(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu == null) {
			return null;
		}
		Map<Container, List<Slot>> groups = new LinkedHashMap<>();
		for (Slot slot : menu.slots) {
			if (slot.container == player.getInventory()) {
				continue;
			}
			groups.computeIfAbsent(slot.container, c -> new ArrayList<>()).add(slot);
		}
		Container best = null;
		List<Slot> bestSlots = null;
		for (var e : groups.entrySet()) {
			if (bestSlots == null || e.getValue().size() > bestSlots.size()) {
				best = e.getKey();
				bestSlots = e.getValue();
			}
		}
		if (best == null || bestSlots == null) {
			return null;
		}
		return new Target(best, bestSlots, menu);
	}

	private record Target(Container container, List<Slot> slots, AbstractContainerMenu menu) {
	}

	private static void sortOpenContainer(ServerPlayer player) {
		Target target = findTarget(player);
		if (target == null) {
			player.sendSystemMessage(Component.literal("[SortBox] Mở rương/túi trước rồi mới sắp xếp được"));
			return;
		}
		sortContainer(target);
		player.containerMenu.sendAllDataToRemote();
	}

	private static void sortContainer(Target target) {
		List<Slot> slots = target.slots();
		int n = slots.size();
		List<ItemStack> stacks = new ArrayList<>();
		for (Slot slot : slots) {
			ItemStack st = slot.getItem();
			if (!st.isEmpty()) {
				stacks.add(st);
				slot.set(ItemStack.EMPTY);
			}
		}
		mergeAndSort(stacks);
		for (int i = 0; i < n; i++) {
			Slot slot = slots.get(i);
			slot.set(i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
		}
		target.container().setChanged();
	}

	// ---------- store all / take all ----------

	private static void storeAll(ServerPlayer player) {
		Target target = findTarget(player);
		if (target == null) {
			player.sendSystemMessage(Component.literal("[SortBox] Mở rương/túi trước rồi mới cất được"));
			return;
		}
		Container chest = target.container();
		Inventory inv = player.getInventory();
		int kept = inv.getSelectedSlot(); // item in hand stays
		int size = inv.getNonEquipmentItems().size();
		int moved = 0;
		List<ItemStack> leftovers = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			if (i == kept) {
				continue;
			}
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) {
				continue;
			}
			inv.setItem(i, ItemStack.EMPTY);
			ItemStack rest = insertIntoContainer(chest, st);
			if (!rest.isEmpty()) {
				leftovers.add(rest);
			} else {
				moved++;
			}
		}
		// put back whatever did not fit (prefer original slots we emptied)
		int li = 0;
		for (int i = 0; i < size && li < leftovers.size(); i++) {
			if (i != kept && inv.getItem(i).isEmpty()) {
				inv.setItem(i, leftovers.get(li++));
			}
		}
		while (li < leftovers.size()) {
			ItemStack rest = leftovers.get(li++);
			if (!inv.add(rest)) {
				// truly no space: drop at feet, never lose items
				net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
					player.level(), player.getX(), player.getY(), player.getZ(), rest);
				player.level().addFreshEntity(drop);
			}
		}
		inv.setChanged();
		chest.setChanged();
		player.containerMenu.sendAllDataToRemote();
		player.sendSystemMessage(Component.literal(
			"[SortBox] Đã cất " + moved + " chồng vào rương" + (li < leftovers.size() ? " (túi đầy: giữ lại)" : "")));
	}

	private static void takeAll(ServerPlayer player) {
		Target target = findTarget(player);
		if (target == null) {
			player.sendSystemMessage(Component.literal("[SortBox] Mở rương/túi trước rồi mới lấy được"));
			return;
		}
		List<Slot> slots = target.slots();
		int moved = 0;
		for (Slot slot : slots) {
			ItemStack st = slot.getItem();
			if (st.isEmpty()) {
				continue;
			}
			slot.set(ItemStack.EMPTY);
			if (!player.getInventory().add(st)) {
				slot.set(st); // no room: put it back
			} else {
				moved++;
			}
		}
		target.container().setChanged();
		player.containerMenu.sendAllDataToRemote();
		player.sendSystemMessage(Component.literal("[SortBox] Đã lấy " + moved + " chồng từ rương"));
	}

	// ---------- helpers ----------

	@FunctionalInterface
	private interface SlotWriter {
		void write(int listIndex, ItemStack stack);
	}

	private static void distribute(List<ItemStack> stacks, SlotWriter writer, int capacity) {
		for (int i = 0; i < capacity; i++) {
			writer.write(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
		}
	}

	/** Merges equal stacks to max size, then sorts by category and item id. */
	private static void mergeAndSort(List<ItemStack> stacks) {
		// merge same items
		List<ItemStack> merged = new ArrayList<>();
		for (ItemStack st : stacks) {
			int remaining = st.getCount();
			int max = st.getMaxStackSize();
			if (max > 1) {
				for (ItemStack m : merged) {
					if (remaining == 0) {
						break;
					}
					if (!m.isEmpty() && ItemStack.isSameItemSameComponents(m, st)
							&& m.getCount() < m.getMaxStackSize()) {
						int room = m.getMaxStackSize() - m.getCount();
						int add = Math.min(room, remaining);
						m.grow(add);
						remaining -= add;
					}
				}
			}
			if (remaining > 0) {
				merged.add(st.copyWithCount(remaining));
			}
		}
		merged.sort(Comparator
			.comparingInt(SortServer::categoryOf)
			.thenComparing(st -> BuiltInRegistries.ITEM.getKey(st.getItem()).toString()));
		stacks.clear();
		stacks.addAll(merged);
	}

	/** First empty-or-compatible slot insertion, vanilla-style. Returns leftover. */
	private static ItemStack insertIntoContainer(Container container, ItemStack src) {
		int remaining = src.getCount();
		int max = src.getMaxStackSize();
		int size = container.getContainerSize();
		// pass 1: top up existing matching stacks
		for (int i = 0; i < size && remaining > 0; i++) {
			ItemStack slot = container.getItem(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, src) && slot.getCount() < max) {
				int add = Math.min(max - slot.getCount(), remaining);
				slot.grow(add);
				remaining -= add;
			}
		}
		// pass 2: fill empty slots
		for (int i = 0; i < size && remaining > 0; i++) {
			if (container.getItem(i).isEmpty()) {
				int add = Math.min(max, remaining);
				remaining -= add;
				container.setItem(i, src.copyWithCount(add));
			}
		}
		return remaining == src.getCount() ? src
			: (remaining > 0 ? src.copyWithCount(remaining) : ItemStack.EMPTY);
	}

	private static int categoryOf(ItemStack st) {
		if (isTagged(st, ItemTags.SWORDS) || isTagged(st, ItemTags.PICKAXES)
				|| isTagged(st, ItemTags.AXES) || isTagged(st, ItemTags.SHOVELS)
				|| isTagged(st, ItemTags.HOES)) {
			return CAT_TOOL;
		}
		if (st.getItem() instanceof BlockItem) {
			return CAT_BLOCK;
		}
		if (st.getComponents().has(DataComponents.FOOD)) {
			return CAT_FOOD;
		}
		return CAT_MISC;
	}

	private static boolean isTagged(ItemStack st, TagKey<Item> tag) {
		return st.is(holder -> holder.is(tag));
	}
}
