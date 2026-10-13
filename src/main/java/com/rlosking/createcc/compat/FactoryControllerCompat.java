package com.rlosking.createcc.compat;

import com.rlosking.createcc.CreateCCConfig;
import com.rlosking.createcc.network.ColorConnectionPacket;
import com.rlosking.createcc.network.FCColorConnectionPacket;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class FactoryControllerCompat {

	private static final org.apache.logging.log4j.Logger LOGGER =
			org.apache.logging.log4j.LogManager.getLogger("createcc-fc");

	private static final String FC_MIN_VERSION = "1.2.1";

	private FactoryControllerCompat() {}

	public static boolean isLoaded() {
		for (IModInfo mod : ModList.get().getMods())
			if ("createfactorycontroller".equals(mod.getModId()))
				return versionAtLeast(mod.getVersion().toString(), FC_MIN_VERSION);
		return false;
	}

	private static boolean versionAtLeast(String version, String minimum) {
		int[] v = numericParts(version);
		int[] m = numericParts(minimum);
		for (int i = 0; i < Math.max(v.length, m.length); i++) {
			int a = i < v.length ? v[i] : 0;
			int b = i < m.length ? m[i] : 0;
			if (a != b)
				return a > b;
		}
		return true;
	}

	private static int[] numericParts(String version) {
		String numeric = version.split("[^0-9.]", 2)[0];
		if (numeric.isEmpty())
			return new int[0];
		String[] parts = numeric.split("\\.");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
			out[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
		return out;
	}

	public static void dyeConnection(FCColorConnectionPacket payload, IPayloadContext context) {
		if (!CreateCCConfig.FACTORY_CONTROLLER_DYEING.get())
			return;
		if (!(context.player() instanceof ServerPlayer player)) {
			LOGGER.info("[createcc-fc] server: not a server player");
			return;
		}
		if (!(player.containerMenu instanceof FactoryControllerMenu menu)) {
			LOGGER.info("[createcc-fc] server: open menu is {}",
				player.containerMenu.getClass().getName());
			return;
		}
		BlockPos controllerPos = menu.controllerPos;
		if (!controllerPos.equals(payload.pos())) {
			LOGGER.info("[createcc-fc] server: pos mismatch {} vs {}", controllerPos, payload.pos());
			return;
		}
		DyeColor expected = payload.dyeOrdinal() < 0 ? DyeColor.BLACK : DyeColor.byId(payload.dyeOrdinal());
		ItemStack cursorDye = menu.getCarried();
		ItemStack dyeSource = null;
		if (cursorDye.getItem() instanceof DyeItem carriedDye
				&& (player.isCreative() || carriedDye.getDyeColor() == expected)) {
			dyeSource = cursorDye;
		} else {
			InteractionHand hand = ColorConnectionPacket.heldDyeHand(player, payload.dyeOrdinal());
			if (hand != null)
				dyeSource = player.getItemInHand(hand);
		}
		if (dyeSource == null) {
			LOGGER.info("[createcc-fc] server: no matching dye on cursor or in hands (ordinal={})",
				payload.dyeOrdinal());
			return;
		}
		if (!(player.level().getBlockEntity(controllerPos) instanceof FactoryControllerBlockEntity be)) {
			LOGGER.info("[createcc-fc] server: no controller BE at {}", controllerPos);
			return;
		}
		if (Connection.Type.get(payload.typeName()) != LogisticsConnection.TYPE) {
			LOGGER.info("[createcc-fc] server: type {} not dyeable", payload.typeName());
			return;
		}
		Connection conn = be.connectionGraph().get(
			new VirtualComponentPosition(payload.fromX(), payload.fromY()),
			new VirtualComponentPosition(payload.toX(), payload.toY()),
			LogisticsConnection.TYPE);
		if (!(conn instanceof LogisticsConnection wire)) {
			LOGGER.info("[createcc-fc] server: wire not found ({},{}) -> ({},{})",
				payload.fromX(), payload.fromY(), payload.toX(), payload.toY());
			return;
		}
		int dye = payload.dyeOrdinal() < 0 ? 0 : payload.dyeOrdinal() + 1;
		if (((VirtualConnectionDye) wire).createcc$getDye() == dye) {
			LOGGER.info("[createcc-fc] server: wire already has dye {}", dye);
			return;
		}
		((VirtualConnectionDye) wire).createcc$setDye(dye);
		if (CreateCCConfig.DYE_CONSUMPTION.get() && !player.isCreative())
			dyeSource.shrink(1);
		be.setChanged();
		be.syncConnection(ConnectionKey.of(wire));
		LOGGER.info("[createcc-fc] server: dye {} applied and synced", dye);
		if (CreateCCConfig.DYE_EFFECTS.get())
			player.level().playSound(null, controllerPos,
				SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
	}
}
