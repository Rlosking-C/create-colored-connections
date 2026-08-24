package com.rlosking.createcc.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.rlosking.createcc.ConnectionHitTester;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Hover lift: when the crosshair points at a connection, the whole line
 * (dye border + vanilla core) smoothly floats above all other lines.
 *
 * <p>Purpose: tell overlapping links apart without thickening or glowing —
 * the lift is measured in vanilla layer units and totals a few microns
 * (each layer = 1/512 blocks), so there is zero visible displacement; only
 * the occlusion order at crossings changes, keeping the restrained Create
 * look. Uncolored lines lift too (tracing overlaps matters for any line).</p>
 *
 * <p>How it works:
 * <ol>
 * <li>Every {@link #SAMPLE_INTERVAL_MS} the crosshair hit refreshes the
 *     "hovered connection" — reusing the exact same picking chain
 *     {@link ConnectionHitTester#find} as the dye right-click interaction,
 *     so pointing precision and tolerance match: wherever you can dye, you
 *     can lift</li>
 * <li>Each connection keeps a 0→1 lift progress: rising to 1 within
 *     {@link #RISE_MS} while hovered, falling back to 0 within
 *     {@link #FALL_MS} when not — no abrupt jumps</li>
 * <li>The renderer mixin queries {@link #liftFor} every frame and maps the
 *     linear progress through smoothstep onto the layer delta with easing
 *     at both ends</li>
 * </ol></p>
 *
 * <p>Client-only: state lives in a static map, wholesale invalidated on
 * dimension change; nothing is persisted or synced over the network.</p>
 */
public final class ConnectionHoverTracker {

	/**
	 * Total lift in layer units. Must exceed the highest layer vanilla can
	 * produce: flashing active = 1 + 2 + 2 = 5, plus the 0.125 axis offset
	 * gives 5.125. With 4.5, even a hovered line on the lowest idle layer (1)
	 * reaches 5.5 — always above every other line (including their dye
	 * borders) — while staying clear of real block geometry
	 * (5.5/512 ≈ 0.011 blocks, far below the panel thickness).
	 */
	public static final float HOVER_LIFT = 4.5f;

	/**
	 * Lift of the connection being rendered by the current renderPath call.
	 *
	 * <p>The mixin's {@code @ModifyArg} injection point (vanilla core Y
	 * argument) cannot carry host-method parameters (it can't know which
	 * connection is rendering), so the renderer mixin computes and writes it
	 * on the first line of its HEAD injection, and the ModifyArg handler
	 * reads it back. renderPath calls are serialized per connection on the
	 * render thread — no concurrency, the stash is safe.</p>
	 */
	public static float stashedLift;

	/** Crosshair sampling interval: picking scans nearby block entities; 10Hz feels responsive at negligible cost */
	private static final long SAMPLE_INTERVAL_MS = 100;
	/** Rise slightly faster (snappy), fall slightly slower (not jarring) */
	private static final long RISE_MS = 150, FALL_MS = 200;

	/** Map key: source panel → target panel, same shape as the ConnectionKey color key (fields, order) */
	public record Key(FactoryPanelPosition from, FactoryPanelPosition to) {}

	/** Lift progress per connection (0~1); entries reaching zero while unhovered are removed, keeping the map tiny */
	private static final Map<Key, Float> progress = new HashMap<>();
	/** The connection currently under the crosshair; null when the crosshair is off every line */
	private static Key hovered;
	/** World of the last sample: a dimension change / world exit invalidates all animation state */
	private static Level lastLevel;
	private static long lastSample, lastAdvance;

	private ConnectionHoverTracker() {}

	/**
	 * Renderer query: advances the global animation clock, then returns the
	 * connection's current lift in layer units (always ≥ 0).
	 *
	 * <p>Called multiple times per frame by the renderer mixin (once per dye
	 * border segment, once per vanilla core segment); timestamp guards ensure
	 * "sampling" and "progress advancing" each happen at most once per frame,
	 * every other call is a plain map lookup.</p>
	 */
	public static float liftFor(FactoryPanelPosition from, FactoryPanelPosition to) {
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level != lastLevel) {
			lastLevel = level;
			progress.clear();
			hovered = null;
		}
		if (level != null && now - lastSample >= SAMPLE_INTERVAL_MS) {
			lastSample = now;
			resample(mc, level);
		}
		if (now != lastAdvance) {
			long dt = Math.min(now - lastAdvance, 100);
			lastAdvance = now;
			advance(dt);
		}
		Float p = progress.get(new Key(from, to));
		return p == null ? 0 : HOVER_LIFT * smoothstep(p);
	}

	/** Refreshes the hovered target from the crosshair hit (same picking chain as the dye interaction). */
	private static void resample(Minecraft mc, Level level) {
		HitResult pick = mc.hitResult;
		if (!(pick instanceof BlockHitResult blockHit)) {
			hovered = null;
			return;
		}
		// Pass the current target into the picker as the "sticky" connection:
		// hysteresis keeps it picked while the crosshair stays on it, so close
		// or crossing lines no longer flip the hover on every tiny mouse move
		ConnectionHitTester.Hit hit = ConnectionHitTester.find(level,
			blockHit.getLocation(), blockHit.getDirection(),
			hovered == null ? null : hovered.from(),
			hovered == null ? null : hovered.to());
		hovered = hit == null ? null : new Key(hit.from(), hit.to());
	}

	/**
	 * The connection currently under the crosshair (null when off every line).
	 *
	 * <p>Consumed by the dye click handler so the line that is visibly lifted
	 * is exactly the line that gets dyed — clicking through a bundle of
	 * overlapping lines must match what the player sees raised, not whichever
	 * line is a hair closer to the raw hit point.</p>
	 */
	public static Key hoveredConnection() {
		return hovered;
	}

	/**
	 * Advances every tracked connection's progress: the hovered one rises
	 * toward 1, the rest fall toward 0. Iterates the whole map (not just the
	 * currently rendered lines) so off-screen connections also fall back on
	 * schedule.
	 */
	private static void advance(long dtMs) {
		Iterator<Map.Entry<Key, Float>> it = progress.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Key, Float> e = it.next();
			float p = e.getValue();
			boolean rising = e.getKey().equals(hovered);
			float step = dtMs / (float) (rising ? RISE_MS : FALL_MS);
			p = rising ? Math.min(1, p + step) : Math.max(0, p - step);
			if (p <= 0)
				it.remove();
			else
				e.setValue(p);
		}
		// A newly hovered connection enters the map at 0 and starts rising next frame
		if (hovered != null)
			progress.putIfAbsent(hovered, 0.0001f);
	}

	/** smoothstep: linear progress → ease-in/ease-out, no velocity jumps, close to vanilla easing */
	private static float smoothstep(float t) {
		return t * t * (3 - 2 * t);
	}
}
