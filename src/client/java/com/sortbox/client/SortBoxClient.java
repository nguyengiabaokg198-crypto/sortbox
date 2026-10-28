package com.sortbox.client;

import com.sortbox.networking.SortActionPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds three big touch-friendly buttons under every container GUI:
 * [Sap Xep] [Cat nhanh] [Lay nhanh]. On the plain inventory screen only
 * [Sap Xep] is shown (there is no container to store into).
 */
@Environment(EnvType.CLIENT)
public class SortBoxClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) {
				return;
			}
			if (!ClientPlayNetworking.canSend(SortActionPayload.TYPE)) {
				return; // server has no SortBox: show nothing
			}
			var widgets = Screens.getWidgets(screen);
			int y = screen.height - 24;

			if (screen instanceof InventoryScreen) {
				// plain inventory: only sort the bag (hotbar stays untouched)
				widgets.add(Button.builder(Component.literal("SẮP XẾP"), b ->
					send(SortActionPayload.OP_SORT_INV))
					.bounds(screen.width / 2 - 60, y, 120, 18).build());
				return;
			}

			// any chest / hopper / other container: full set
			int bw = 76;
			int gap = 6;
			int x0 = screen.width / 2 - (bw * 3 + gap * 2) / 2;
			widgets.add(Button.builder(Component.literal("SẮP XẾP"), b ->
				send(SortActionPayload.OP_SORT_CONTAINER))
				.bounds(x0, y, bw, 18).build());
			widgets.add(Button.builder(Component.literal("CẤT NHANH"), b ->
				send(SortActionPayload.OP_STORE_ALL))
				.bounds(x0 + bw + gap, y, bw, 18).build());
			widgets.add(Button.builder(Component.literal("LẤY NHANH"), b ->
				send(SortActionPayload.OP_TAKE_ALL))
				.bounds(x0 + (bw + gap) * 2, y, bw, 18).build());
		});
	}

	private static void send(byte op) {
		ClientPlayNetworking.send(new SortActionPayload(op, (byte) 0));
	}
}
