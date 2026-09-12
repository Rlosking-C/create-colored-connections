package com.rlosking.createcc.compat;

/**
 * Duck interface injected into Factory Controller's
 * {@code LogisticsConnection} by our mixin, giving server and client code a
 * type-safe handle on the dye tag without referencing the mod's classes.
 *
 * <p>The stored value is {@code DyeColor.ordinal() + 1}, with {@code 0}
 * meaning "not dyed". The +1 offset exists so the Java zero-initialization
 * of the injected field is already the correct default — white (ordinal 0)
 * would otherwise be indistinguishable from "unset".</p>
 */
public interface VirtualConnectionDye {

	/** @return the dye ordinal + 1, or 0 when the connection is not dyed */
	int createcc$getDye();

	/**
	 * Sets the dye tag.
	 *
	 * @param dye the dye ordinal + 1, or 0 to clear the color
	 */
	void createcc$setDye(int dye);
}
