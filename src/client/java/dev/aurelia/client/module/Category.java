package dev.aurelia.client.module;

public enum Category {
	VISUAL("Visual"),
	INTEL("Intel"),
	COMBAT("Combat"),
	MOVEMENT("Movement");

	private final String title;

	Category(String title) {
		this.title = title;
	}

	public String title() {
		return title;
	}
}
