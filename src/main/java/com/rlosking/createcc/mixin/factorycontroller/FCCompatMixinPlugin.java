package com.rlosking.createcc.mixin.factorycontroller;

import java.util.List;
import java.util.Set;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gate for the Factory Controller mixin config: every mixin in it targets
 * FC classes and imports them, so the whole config is skipped when the mod
 * is not installed — or when it is older than the version this mod was
 * compiled against ({@link #FC_MIN_VERSION}). FC ships rapid beta updates
 * and has already renamed methods and moved classes between minor versions,
 * which would crash at runtime (NoSuchMethodError / NoClassDefFoundError)
 * if our mixins applied against them.
 *
 * <p>Boot-order subtlety: mixin configs declared in mods.toml are processed
 * right after mod discovery but well before the runtime
 * {@code net.minecraftforge.fml.ModList} exists — {@code ModList.get()} is
 * still null while this plugin is being asked, so the presence and version
 * check must go through {@link FMLLoader#getLoadingModList()}, which is
 * populated as soon as discovery finishes. If even that is unavailable
 * (config pulled in by other means, e.g. a manual {@code mixin.config}
 * property), the mixins are skipped rather than applied blindly: the
 * integration stays off instead of breaking the boot.</p>
 *
 * <p><b>1.20.1 port note:</b> the Forge FML API shape here is identical to the
 * NeoForge 1.21.1 original — {@code FMLLoader.getLoadingModList()} returns a
 * {@code LoadingModList} whose {@code getModFileById(String)} yields a
 * {@code ModFileInfo} whose {@code getMods()} yields {@code IModInfo}s. Only
 * the package names differ ({@code net.minecraftforge.*}), so the logic is
 * carried over unchanged. Verified against fmlloader-1.20.1-47.3.25 and
 * forgespi-7.0.1 in the Gradle cache.</p>
 */
public class FCCompatMixinPlugin implements IMixinConfigPlugin {

	private static final Logger LOGGER = LogManager.getLogger("createcc-fc");

	/** Minimum Factory Controller version the mixins compile against (see build.gradle). */
	private static final String FC_MIN_VERSION = "1.2.1";

	/** Cached answer for {@link #shouldApplyMixin} (asked once per mixin). */
	private Boolean createcc$fcSupported;

	@Override
	public void onLoad(String mixinPackage) {
		LOGGER.debug("[createcc-fc] plugin onLoad");
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (createcc$fcSupported == null) {
			LoadingModList mods = FMLLoader.getLoadingModList();
			createcc$fcSupported = mods != null && isFactoryControllerSupported(mods);
			// Debug, not info: this runs on every boot and is a diagnostic, not an
			// event a player or pack author needs in the log by default.
			LOGGER.debug("[createcc-fc] gate: Factory Controller {} (minimum {}) -> {} (loadingModList={})",
				installedVersion(mods), FC_MIN_VERSION,
				createcc$fcSupported ? "new enough, mixins apply" : "missing or too old, mixins skipped",
				mods != null);
		}
		return createcc$fcSupported;
	}

	/**
	 * Installed Factory Controller version, for the gate's log line only: "not
	 * installed" when the mod is absent, "unknown" when even the mod list is not
	 * available yet. Without this the line printed the minimum version in the
	 * position where a detected version would be expected, which read as though
	 * the mod were present when it was not.
	 */
	private static String installedVersion(LoadingModList mods) {
		if (mods == null)
			return "unknown";
		ModFileInfo file = mods.getModFileById("createfactorycontroller");
		if (file != null)
			for (IModInfo mod : file.getMods())
				if ("createfactorycontroller".equals(mod.getModId()))
					return mod.getVersion().toString();
		return "not installed";
	}

	/**
	 * Resolves the installed Factory Controller's declared version and checks
	 * it against {@link #FC_MIN_VERSION}. Absent mod or absent version both
	 * answer false — the integration then simply stays off.
	 */
	private static boolean isFactoryControllerSupported(LoadingModList mods) {
		ModFileInfo file = mods.getModFileById("createfactorycontroller");
		if (file == null)
			return false;
		for (IModInfo mod : file.getMods())
			if ("createfactorycontroller".equals(mod.getModId()))
				return versionAtLeast(mod.getVersion().toString(), FC_MIN_VERSION);
		return false;
	}

	/**
	 * Compares the leading numeric components only ("1.2.1-forge-1.20.1"
	 * is 1.2.1), so loader/minecraft suffixes never make a version look newer
	 * than it is.
	 */
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
		// Cut at the first character that is neither a digit nor a dot
		String numeric = version.split("[^0-9.]", 2)[0];
		if (numeric.isEmpty())
			return new int[0];
		String[] parts = numeric.split("\\.");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
			out[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
		return out;
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
