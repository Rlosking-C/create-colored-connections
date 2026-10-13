package com.rlosking.createcc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ConnectionKey(FactoryPanelPosition from, FactoryPanelPosition to) {

	public static final Codec<ConnectionKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		FactoryPanelPosition.CODEC.fieldOf("from").forGetter(ConnectionKey::from),
		FactoryPanelPosition.CODEC.fieldOf("to").forGetter(ConnectionKey::to)
	).apply(instance, ConnectionKey::new));

	public static final StreamCodec<ByteBuf, ConnectionKey> STREAM_CODEC = StreamCodec.composite(
		FactoryPanelPosition.STREAM_CODEC, ConnectionKey::from,
		FactoryPanelPosition.STREAM_CODEC, ConnectionKey::to,
		ConnectionKey::new
	);

	public ConnectionKey(FactoryPanelPosition from, FactoryPanelPosition to) {
		this.from = from;
		this.to = to;
	}
}
