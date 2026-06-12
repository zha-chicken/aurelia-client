package dev.aurelia.client.module;

import java.util.ArrayList;
import java.util.List;

public final class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting> settings = new ArrayList<>();
	private boolean enabled;

	public Module(String id, String name, String description, Category category, boolean enabled, String settingName, String... settingValues) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = enabled;
		if (settingName != null && !settingName.isEmpty() && settingValues != null && settingValues.length > 0) {
			addSetting(settingName, settingValues);
		}
	}

	// Minimal constructor for modules that add settings after creation
	public Module(String id, String name, String description, Category category, boolean enabled) {
		this(id, name, description, category, enabled, null, (String[]) null);
	}

	public String id() { return id; }
	public String name() { return name; }
	public String description() { return description; }
	public Category category() { return category; }
	public boolean enabled() { return enabled; }

	public int getSettingCount() { return settings.size(); }
	public boolean hasSetting() { return !settings.isEmpty(); }

	// Legacy single-setting accessors (operate on first setting for compatibility)
	public String settingName() { return hasSetting() ? settings.get(0).name : ""; }
	public String settingValue() { return hasSetting() ? settings.get(0).value() : "None"; }
	public int settingIndex() { return hasSetting() ? settings.get(0).index : 0; }

	public String getSettingName(int index) {
		return (index >= 0 && index < settings.size()) ? settings.get(index).name : "";
	}
	public String getSettingValue(int index) {
		return (index >= 0 && index < settings.size()) ? settings.get(index).value() : "None";
	}
	public int getSettingIndex(int index) {
		return (index >= 0 && index < settings.size()) ? settings.get(index).index : 0;
	}

	public String getSettingValueByName(String name) {
		for (Setting s : settings) {
			if (s.name.equals(name)) return s.value();
		}
		return "";
	}

	public void addSetting(String name, String... values) {
		if (name != null && !name.isEmpty() && values != null && values.length > 0) {
			settings.add(new Setting(name, values));
		}
	}

	public void toggle() {
		enabled = !enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void cycleSetting() {
		if (hasSetting()) cycleSetting(0);
	}

	public void cycleSetting(int index) {
		if (index >= 0 && index < settings.size()) {
			Setting s = settings.get(index);
			s.index = (s.index + 1) % s.values.length;
		}
	}

	public void setSettingIndex(int settingIndex) {
		if (hasSetting()) setSettingIndex(0, settingIndex);
	}

	public void setSettingIndex(int index, int value) {
		if (index >= 0 && index < settings.size()) {
			Setting s = settings.get(index);
			s.index = Math.floorMod(value, s.values.length);
		}
	}

	private static class Setting {
		final String name;
		final String[] values;
		int index = 0;

		Setting(String name, String[] values) {
			this.name = name;
			this.values = values;
		}

		String value() { return values[index]; }
	}
}
