package com.rlosking.createcc.mixin.factorycontroller;

import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gate for the Factory Controller mixin config: every mixin in it targets
 * FC classes and imports them, so the whole config is skipped when the mod
 * is not installed.
 *
 * <p>Boot-order subtlety: mixin configs declared in neoforge.mods.toml are
 * processed right after mod discovery but well before the runtime
 * {@code net.neoforged.fml.ModList} exists — {@code ModList.get()} is still
 * null while this plugin is being asked, so the presence check must go
 * through {@link FMLLoader#getLoadingModList()}, which is populated as soon
 * as discovery finishes. If even that is unavailable (config pulled in by
 * other means, e.g. a manual {@code mixin.config} property), the mixins are
 * skipped rather than applied blindly: the integration stays off instead of
 * breaking the boot.</p>
 */
public class FCCompatMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LogManager.getLogger("createcc-fc");

	@Override
	public void onLoad(String mixinPackage) {
		LOGGER.info("[createcc-fc] plugin onLoad");
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		LoadingModList mods = FMLLoader.getLoadingModList();
		boolean present = mods != null && mods.getModFileById("createfactorycontroller") != null;
		// Boot-time diagnostic (one line per mixin, before any log spam starts)
		LOGGER.info("[createcc-fc] gate {} -> {} (loadingModList={})", mixinClassName, present, mods != null);
		return present;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass,
			String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass,
			String mixinClassName, IMixinInfo mixinInfo) {}
}
