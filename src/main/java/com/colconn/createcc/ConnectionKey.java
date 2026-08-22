package com.colconn.createcc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Unique identity key of a factory gauge connection.
 *
 * <p>In Create, a connection is uniquely determined by its "source panel
 * position from" pointing at "target panel position to" (mirroring the
 * key/value structure of FactoryPanelBehaviour.targetedBy). Color data is
 * persisted under this key, so colors survive even when connection objects
 * are rebuilt.</p>
 *
 * <p>Note: when a panel is relocated its position changes; the behaviour
 * Mixin is responsible for remapping or cleaning up color keys at that time.</p>
 */
public record ConnectionKey(FactoryPanelPosition from, FactoryPanelPosition to) {

	/** Persistence codec; reuses Create's FactoryPanelPosition.CODEC directly */
	public static final Codec<ConnectionKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		FactoryPanelPosition.CODEC.fieldOf("from").forGetter(ConnectionKey::from),
		FactoryPanelPosition.CODEC.fieldOf("to").forGetter(ConnectionKey::to)
	).apply(instance, ConnectionKey::new));

	/** Network stream codec; reuses Create's FactoryPanelPosition.STREAM_CODEC directly */
	public static final StreamCodec<ByteBuf, ConnectionKey> STREAM_CODEC = StreamCodec.composite(
		FactoryPanelPosition.STREAM_CODEC, ConnectionKey::from,
		FactoryPanelPosition.STREAM_CODEC, ConnectionKey::to,
		ConnectionKey::new
	);

	/**
	 * Constructs a connection key.
	 *
	 * @param from source panel (the end the link is drawn from)
	 * @param to   target panel (the end the link is drawn on; rendering happens on this panel)
	 */
	public ConnectionKey(FactoryPanelPosition from, FactoryPanelPosition to) {
		this.from = from;
		this.to = to;
	}
}
