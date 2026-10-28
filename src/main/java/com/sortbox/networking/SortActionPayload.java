package com.sortbox.networking;

import com.sortbox.SortBox;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server request: sort the player inventory or the open container. */
public record SortActionPayload(byte op, byte arg) implements CustomPacketPayload {
	public static final byte OP_SORT_INV = 0;
	public static final byte OP_SORT_CONTAINER = 1;
	public static final byte OP_STORE_ALL = 2;
	public static final byte OP_TAKE_ALL = 3;

	public static final CustomPacketPayload.Type<SortActionPayload> TYPE =
		new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(SortBox.MOD_ID, "action"));

	public static final StreamCodec<ByteBuf, SortActionPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeByte(p.op());
			buf.writeByte(p.arg());
		},
		buf -> new SortActionPayload(buf.readByte(), buf.readByte()));

	@Override
	public CustomPacketPayload.Type<SortActionPayload> type() {
		return TYPE;
	}
}
