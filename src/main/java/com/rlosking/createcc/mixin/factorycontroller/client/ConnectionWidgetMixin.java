package com.rlosking.createcc.mixin.factorycontroller.client;

import com.rlosking.createcc.compat.DyeUnderlay;
import com.rlosking.createcc.compat.VirtualConnectionDye;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.render.VirtualConnectionRenderer;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ConnectionWidget;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Visual half of GUI wire dyeing, mirroring the world renderer's two looks.
 *
 * <p><b>Idle (the sink gauge's gray — not active, missing address, or
 * redstone-paused — with no success/failure flash in progress)</b>: the gray
 * line carries no information, so the vanilla render is cancelled and the
 * whole line becomes the dye color, exactly like a dyed idle link on a
 * factory wall.</p>
 *
 * <p><b>Active (any status color, including a flash in progress)</b>: a dye
 * underlay is submitted first — FC's own connection texture tinted with the
 * dye color, covering the path 2px wider per side (50% of the 4px core, the
 * same border-to-core proportion as a dyed wall link) — then the vanilla
 * render proceeds and its textured status core stacks on top: a
 * <em>textured</em> dye border per side (not a flat color), matching
 * the washboard-textured dye edge of world links, with the status color and
 * its flash animation fully preserved.</p>
 *
 * <p><b>Hover</b>: the dye look stays — the idle dye line / the active dye
 * border answer the hover like any other frame. FC's white highlight bar
 * keeps its place beside the core: for the idle line the bar the screen
 * painted beneath simply peeks through the texture's transparent edge
 * pixels (the same mechanism as the vanilla hover look), while for the
 * active wire the bar is re-drawn on top of the underlay, because the
 * underlay (2px wider per side) would otherwise cover it.</p>
 */
@Mixin(ConnectionWidget.class)
public abstract class ConnectionWidgetMixin {

	/** Copied from ConnectionWidget's private constant (flash glow window in ticks). */
	@Unique
	private static final float createcc$FLASH_DECAY = 8f;

	/** The widget whose renderHighlight ran last — see {@link #createcc$markHovered}. */
	@Unique
	private static ConnectionWidget createcc$hovered;

	@Shadow
	@Final
	public Connection connection;

	@Shadow
	@Final
	List<Vector2i> path;

	@Shadow
	public abstract void renderHighlight(GuiGraphics gfx);

	/**
	 * Remembers which widget is being highlighted. FC's renderBoard always
	 * calls {@code renderHighlight} immediately before the hovered widget's
	 * own {@code render}, so the flag reliably links the two without touching
	 * the screen class.
	 */
	@Inject(method = "renderHighlight", at = @At("HEAD"))
	private void createcc$markHovered(GuiGraphics gfx, CallbackInfo ci) {
		createcc$hovered = (ConnectionWidget) (Object) this;
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void createcc$dyedWire(GuiGraphics gfx, ComponentHolder holder, CallbackInfo ci) {
		if (!(connection instanceof VirtualConnectionDye dyeable) || dyeable.createcc$getDye() == 0)
			return;
		boolean hovered = createcc$hovered == (Object) this;
		if (hovered)
			createcc$hovered = null;
		int dye = dyeable.createcc$getDye();
		int dyeColor = 0xFF000000 | DyeColor.byId(dye - 1).getTextureDiffuseColor();

		// Flash state, computed exactly like the vanilla render does: the wire
		// animates (and briefly mixes green/red) after each request. Since FC
		// 1.2.1 the "now" timestamp is passed in by the caller (null level
		// meaning "no flash"), mirroring ConnectionWidget.render's own call.
		ClientLevel level = Minecraft.getInstance().level;
		long animationTick = connection.getFlashTick(holder, level != null ? level.getGameTime() : -1L);
		boolean flashing = false;
		if (animationTick >= 0) {
			float age = animationTick + AnimationTickHolder.getPartialTicks();
			float glow = Mth.clamp(1f - age / createcc$FLASH_DECAY, 0f, 1f);
			flashing = glow > 0f;
		}

		// Idle gray: the same condition under which getConnectionColor answers
		// gray — the dye color becomes the whole line (animated texture when
		// the vanilla line would animate it)
		if (holder.componentAt(connection.to) instanceof VirtualGaugeBehaviour behaviour
				&& (!behaviour.isActive() || behaviour.isMissingAddress() || behaviour.isRedstonePaused())
				&& !flashing) {
			VirtualConnectionRenderer.create(path, dyeColor, animationTick >= 0)
				.drawPath(gfx.bufferSource(), gfx.pose());
			ci.cancel();
			return;
		}

		// Active: textured dye underlay peeking 2px past the status-colored
		// core on each side, same batch and pose so the core stacks on top
		DyeUnderlay.draw(gfx, path, dyeColor, animationTick >= 0);

		// Hovered: re-draw the white highlight bar over the underlay — the bar
		// the screen painted before this render is 4px wide, the underlay 8px,
		// so without this the underlay would hide it entirely. RenderType.gui()
		// and the tiled-sprite type share one buffer, so every type switch
		// flushes in submission order: white bar -> underlay -> white bar ->
		// status core; the dye border keeps peeking 2px outside the bar
		if (hovered) {
			renderHighlight(gfx);
			// markHovered fired again inside the call; clear the flag so a
			// non-hovered frame doesn't mistake this widget for the hovered one
			createcc$hovered = null;
		}
	}

	/**
	 * Adds a dye color line to FC's own connection tooltip (the one that
	 * already appears after FC's hover delay). It is inserted directly under
	 * the title line — the wire's visible color is what the player is asking
	 * about when hovering — and styled GRAY like FC's other info lines, with
	 * the dye name itself rendered in the dye's own color
	 * ({@code DyeColor.getTextColor()}, vanilla's bright-on-dark palette from
	 * sign text), so "染料：青色染料" reads in cyan.
	 *
	 * <p>Since FC 1.2.1 the tooltip is a list of already-formatted
	 * {@link FormattedCharSequence} lines (the overlapping-count rework), so
	 * the dye line is flattened the same way — {@code getVisualOrderText()}
	 * keeps the per-dye color of the styled name.</p>
	 *
	 * <p>Black needs no special case: GUI black dyeing clears the color
	 * (dye field 0, no line at all), and world-side black is never stored,
	 * so the unreachable black entry would only matter for readability.
	 * Non-logistics wires (number/redstone) never implement
	 * {@link VirtualConnectionDye}, so their tooltips stay untouched,
	 * mirroring the dyeing rule itself.</p>
	 */
	@Inject(method = "getTooltip", at = @At("RETURN"))
	private void createcc$dyeTooltipLine(ComponentHolder holder, int count, int index, boolean locked,
			CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
		if (!(connection instanceof VirtualConnectionDye dyeable) || dyeable.createcc$getDye() == 0)
			return;
		List<FormattedCharSequence> lines = cir.getReturnValue();
		if (lines.isEmpty())
			return;
		DyeColor dye = DyeColor.byId(dyeable.createcc$getDye() - 1);
		Component dyeName = Component.translatable("item.minecraft." + dye.getName() + "_dye")
				.withStyle(Style.EMPTY.withColor(dye.getTextColor()));
		lines.add(1, Component.translatable("gui.create_colored_connections.wire_dye", dyeName)
				.withStyle(ChatFormatting.GRAY).getVisualOrderText());
	}
}
