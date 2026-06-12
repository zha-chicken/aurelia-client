package dev.aurelia.client.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Modules {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("aurelia.json");
	private static final List<Module> ALL = new ArrayList<>();

	public static final Module WATERMARK = add("watermark", "Watermark", "Aurelia identity card", Category.VISUAL, true, "");
	public static final Module STATUS = add("status", "Status", "FPS and coordinates", Category.VISUAL, true, "");
	public static final Module ACTIVE_MODULES = add("active-modules", "Active Modules", "Enabled-module HUD list", Category.VISUAL, true, "Alignment", "Right", "Left");
	public static final Module THREAT_INTEL = add("threat-intel", "Threat Intel", "Read-only nearby player analysis", Category.INTEL, true, "Range", "12m", "24m", "48m");
	public static final Module CRYSTAL_RISK = add("crystal-risk", "Crystal Risk", "Elegant crystal highlighting and damage warning", Category.INTEL, true, "Range", "8m", "12m", "16m");
	public static final Module REDUCED_CRYSTAL_FX = add("reduced-crystal-fx", "Reduced Crystal FX", "Reduce screen-blocking crystal explosion particles", Category.VISUAL, true, "");
	public static final Module COMBAT_ADVISOR = add("combat-advisor", "Combat Advisor", "Hold Z for manual combat suggestions", Category.INTEL, true, "Crystal Scan", "3m", "5m", "7m");
	public static final Module TEST_DUMMY = add("test-dummy", "Test Dummy", "Single-player target for combat analysis testing", Category.INTEL, false, "Armor", "None", "Diamond", "Netherite");
	public static final Module SCAFFOLD_GUIDE = add("scaffold-guide", "Scaffold Guide", "Preview bridge placement readiness", Category.MOVEMENT, false, "Probe", "Feet", "Forward");

	public static final Module VELOCITY = add("velocity", "Velocity", "Cancel all knockback", Category.MOVEMENT, false, "");

	public static final Module KILLAURA;

	static {
		KILLAURA = new Module("killaura", "Killaura", "Auto-attack with true silent rotations, randomization & bypass tuning", Category.COMBAT, false);
		ALL.add(KILLAURA);
		KILLAURA.addSetting("Range", "3.0", "3.5", "4.0", "4.5", "5.0", "6.0");
		KILLAURA.addSetting("Turn Speed", "Slow", "Normal", "Fast", "Blatant");
		KILLAURA.addSetting("Silent", "Off", "On");
		KILLAURA.addSetting("Attack Rand", "Low", "Med", "High");
	}

	public static final Module CRYSTAL_AURA;

	static {
		CRYSTAL_AURA = new Module("crystal-aura", "Crystal Aura", "Places and breaks end crystals using damage calculations", Category.COMBAT, false);
		ALL.add(CRYSTAL_AURA);
		CRYSTAL_AURA.addSetting("Place Range", "3.0", "3.5", "4.0", "4.5", "5.0", "5.5");
		CRYSTAL_AURA.addSetting("Break Range", "3.0", "4.0", "5.0", "6.0");
		CRYSTAL_AURA.addSetting("Max Self Dmg", "No Limit", "2", "4", "6", "8", "12");
		CRYSTAL_AURA.addSetting("Min Target Dmg", "No Limit", "4", "6", "8", "10", "12");
		CRYSTAL_AURA.addSetting("Silent", "Off", "On");
	}

	public static final Module AUTO_DIG;

	static {
		AUTO_DIG = new Module("auto-dig", "Auto Dig", "Send digging packets to mine obsidian around targets' feet without interrupting your actions (placing, moving, etc.)", Category.COMBAT, false);
		ALL.add(AUTO_DIG);
		AUTO_DIG.addSetting("Range", "3.5", "4.5", "5.5", "6.5");
	}

	private Modules() {}

	private static Module add(String id, String name, String description, Category category, boolean enabled, String settingName, String... values) {
		Module module = new Module(id, name, description, category, enabled, settingName, values);
		ALL.add(module);
		return module;
	}

	public static List<Module> all() {
		return List.copyOf(ALL);
	}

	public static List<Module> enabled() {
		return ALL.stream()
			.filter(Module::enabled)
			.sorted(Comparator.comparingInt((Module module) -> module.name().length()).reversed())
			.toList();
	}

	public static List<Module> in(Category category) {
		return ALL.stream().filter(module -> module.category() == category).toList();
	}

	public static void load() {
		if (!Files.exists(CONFIG)) return;
		try {
			ConfigData values = GSON.fromJson(Files.readString(CONFIG), ConfigData.class);
			if (values != null) ALL.forEach(module -> {
				if (values.enabled != null && values.enabled.containsKey(module.id())) module.setEnabled(values.enabled.get(module.id()));
				if (values.settings != null && values.settings.containsKey(module.id())) {
					List<Integer> idxs = values.settings.get(module.id());
					for (int i = 0; i < idxs.size() && i < module.getSettingCount(); i++) {
						module.setSettingIndex(i, idxs.get(i));
					}
				}
			});
		} catch (Exception ignored) {
		}
	}

	public static void save() {
		ConfigData values = new ConfigData();
		ALL.forEach(module -> {
			values.enabled.put(module.id(), module.enabled());
			List<Integer> idxs = new ArrayList<>();
			for (int i = 0; i < module.getSettingCount(); i++) {
				idxs.add(module.getSettingIndex(i));
			}
			values.settings.put(module.id(), idxs);
		});
		try {
			Files.createDirectories(CONFIG.getParent());
			Files.writeString(CONFIG, GSON.toJson(values));
		} catch (IOException ignored) {
		}
	}

	private static final class ConfigData {
		Map<String, Boolean> enabled = new LinkedHashMap<>();
		Map<String, List<Integer>> settings = new LinkedHashMap<>();
	}
}
