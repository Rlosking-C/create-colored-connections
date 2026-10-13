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

public final class ConnectionHoverTracker {

	public static final float HOVER_LIFT = 4.5f;

	public static float stashedLift;

	private static final long SAMPLE_INTERVAL_MS = 100;
	private static final long RISE_MS = 150, FALL_MS = 200;

	public record Key(FactoryPanelPosition from, FactoryPanelPosition to) {}

	private static final Map<Key, Float> progress = new HashMap<>();
	private static Key hovered;
	private static Level lastLevel;
	private static long lastSample, lastAdvance;

	private ConnectionHoverTracker() {}

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

	private static void resample(Minecraft mc, Level level) {
		HitResult pick = mc.hitResult;
		if (!(pick instanceof BlockHitResult blockHit)) {
			hovered = null;
			return;
		}
		ConnectionHitTester.Hit hit = ConnectionHitTester.find(level,
			blockHit.getLocation(), blockHit.getDirection(),
			hovered == null ? null : hovered.from(),
			hovered == null ? null : hovered.to());
		hovered = hit == null ? null : new Key(hit.from(), hit.to());
	}

	public static Key hoveredConnection() {
		return hovered;
	}

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
		if (hovered != null)
			progress.putIfAbsent(hovered, 0.0001f);
	}

	private static float smoothstep(float t) {
		return t * t * (3 - 2 * t);
	}
}
