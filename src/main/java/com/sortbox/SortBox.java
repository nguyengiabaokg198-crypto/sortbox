package com.sortbox;

import com.mojang.logging.LogUtils;
import com.sortbox.networking.SortActionPayload;
import com.sortbox.server.SortServer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import org.slf4j.Logger;

/**
 * SortBox: one-tap sorting for the player inventory and open containers,
 * plus quick store-all / take-all between them. GUI buttons only - no commands.
 */
public class SortBox implements ModInitializer {
	public static final String MOD_ID = "sortbox";
	public static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(SortActionPayload.TYPE, SortActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(
			SortActionPayload.TYPE, (payload, ctx) ->
				ctx.server().executeIfPossible(() -> SortServer.handle(ctx.player(), payload)));
		LOGGER.info("[SortBox] initialized: sort / store-all / take-all buttons ready");
	}
}
