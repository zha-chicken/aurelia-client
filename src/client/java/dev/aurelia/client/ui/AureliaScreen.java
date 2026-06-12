package dev.aurelia.client.ui;

import dev.aurelia.client.module.Category;
import dev.aurelia.client.module.Module;
import dev.aurelia.client.module.Modules;
import dev.aurelia.client.testing.TestDummyManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AureliaScreen extends Screen {
	private static final int HEADER_HEIGHT = 52;
	private static final int NAV_WIDTH = 112;
	private static final int ROW_HEIGHT = 46;
	private final List<Hitbox> hitboxes = new ArrayList<>();
	private final Map<Category, Float> categoryAnimations = new HashMap<>();
	private final Map<Module, Float> hoverAnimations = new HashMap<>();
	private final Map<Module, Float> toggleAnimations = new HashMap<>();
	private Category category = Category.COMBAT;
	private Module selected = Modules.CRYSTAL_AURA;
	private long previousFrame = System.nanoTime();
	private float frameDelta;

	public AureliaScreen() {
		super(UiTheme.text("Aurelia"));
		for (Category item : Category.values()) categoryAnimations.put(item, item == category ? 1f : 0f);
		for (Module module : Modules.all()) {
			hoverAnimations.put(module, 0f);
			toggleAnimations.put(module, module.enabled() ? 1f : 0f);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		frameDelta = Mth.clamp((now - previousFrame) / 1_000_000_000f, 0, 0.05f);
		previousFrame = now;
		drawBackdrop(g);
		hitboxes.clear();

		int windowWidth = Math.min(Mth.clamp(width - 52, 360, 680), width - 14);
		int windowHeight = Math.min(Mth.clamp(height - 50, 250, 420), height - 14); // a bit taller max to help fit settings on small screens
		int left = width / 2 - windowWidth / 2;
		int top = height / 2 - windowHeight / 2;
		int right = left + windowWidth;
		int bottom = top + windowHeight;

		boolean hasSettings = selected != null && selected.hasSetting();
		boolean showDetails = hasSettings || (windowWidth >= 380);
		int detailsWidth = 0;
		if (showDetails && hasSettings) {
			// Always try to give the settings panel some room when the module has settings
			detailsWidth = Math.min(150, Math.max(95, windowWidth / 3));
		} else if (showDetails) {
			detailsWidth = Math.min(145, Math.max(100, windowWidth / 3));
		}
		int listLeft = left + NAV_WIDTH;
		int listRight = right - detailsWidth;

		UiTheme.roundedRect(g, left - 9, top - 9, right + 9, bottom + 9, 21, 0x1266E3FF);
		UiTheme.roundedRect(g, left - 5, top - 5, right + 5, bottom + 5, 17, 0x1AA78BFA);
		UiTheme.roundedRect(g, left, top, right, bottom, 13, UiTheme.BORDER_SOFT);
		UiTheme.roundedRect(g, left + 1, top + 1, right - 1, bottom - 1, 12, UiTheme.WINDOW);
		UiTheme.horizontalGradient(g, left + 22, top, right - 22, top + 2, UiTheme.ACCENT, UiTheme.ACCENT_2);
		g.fill(left + 14, top + HEADER_HEIGHT, right - 14, top + HEADER_HEIGHT + 1, 0x334A637E);
		g.fill(listLeft, top + HEADER_HEIGHT + 12, listLeft + 1, bottom - 12, 0x334A637E);
		if (showDetails) g.fill(listRight, top + HEADER_HEIGHT + 12, listRight + 1, bottom - 12, 0x334A637E);

		drawHeader(g, left, top, right);
		drawNavigation(g, left, top + HEADER_HEIGHT, bottom, mouseX, mouseY);
		drawModules(g, listLeft, top + HEADER_HEIGHT, listRight, bottom, mouseX, mouseY);
		if (showDetails) drawDetails(g, listRight, top + HEADER_HEIGHT, right, bottom, mouseX, mouseY);
	}

	private void drawBackdrop(GuiGraphics g) {
		g.fillGradient(0, 0, width, height, 0xE0080A10, UiTheme.BACKDROP);
		UiTheme.roundedRect(g, width / 2 - 190, height / 2 - 130, width / 2 + 70, height / 2 + 130, 90, 0x0E66E3FF);
		UiTheme.roundedRect(g, width / 2 - 30, height / 2 - 160, width / 2 + 230, height / 2 + 100, 100, 0x0EA78BFA);
	}

	private void drawHeader(GuiGraphics g, int left, int top, int right) {
		UiTheme.roundedRect(g, left + 15, top + 13, left + 42, top + 40, 9, 0x2266E3FF);
		UiTheme.roundedRect(g, left + 19, top + 17, left + 38, top + 36, 7, UiTheme.GLASS_STRONG);
		UiTheme.horizontalGradient(g, left + 24, top + 22, left + 33, top + 31, UiTheme.ACCENT, UiTheme.ACCENT_2);
		UiTheme.draw(g, font, "AURELIA", left + 51, top + 15, UiTheme.TEXT);
		UiTheme.drawScaled(g, font, "TACTICAL VISUAL INTERFACE", left + 51, top + 29, 0.72f, UiTheme.MUTED);

		String status = "ONLINE";
		int statusWidth = UiTheme.width(font, status);
		int statusX = right - statusWidth - 91;
		UiTheme.pill(g, statusX - 15, top + 17, statusX + statusWidth + 9, top + 35, 0x1F6FFFC1);
		UiTheme.roundedRect(g, statusX - 8, top + 24, statusX - 4, top + 28, 2, UiTheme.ENABLED);
		UiTheme.drawScaled(g, font, status, statusX, top + 22, 0.72f, UiTheme.ENABLED);

		String hint = "RSHIFT";
		UiTheme.pill(g, right - 72, top + 16, right - 15, top + 36, UiTheme.SURFACE);
		UiTheme.drawScaled(g, font, hint, right - 59, top + 22, 0.72f, UiTheme.MUTED);
	}

	private void drawNavigation(GuiGraphics g, int left, int top, int bottom, int mouseX, int mouseY) {
		UiTheme.drawScaled(g, font, "WORKSPACES", left + 16, top + 15, 0.68f, UiTheme.DIM);
		int y = top + 31;
		for (Category item : Category.values()) {
			boolean active = item == category;
			boolean hovered = inside(mouseX, mouseY, left + 9, y, left + NAV_WIDTH - 9, y + 32);
			float categoryProgress = animate(categoryAnimations, item, active || hovered ? 1 : 0, 10);
			if (categoryProgress > 0.01f) {
				UiTheme.roundedRect(g, left + 9, y, left + NAV_WIDTH - 9, y + 32, 8,
					UiTheme.mixColor(0x00141925, active ? 0xD6247082 : UiTheme.SURFACE, categoryProgress));
			}
			if (active) {
				UiTheme.roundedRect(g, left + 9, y + 8, left + 12, y + 24, 2, UiTheme.ACCENT);
			}
			String catLabel = switch (item) {
				case COMBAT -> "⚔ " + item.title();
				case VISUAL -> "◌ " + item.title();
				case INTEL -> "◈ " + item.title();
				default -> "▶ " + item.title();
			};
			UiTheme.draw(g, font, catLabel, left + 19, y + 8, active ? UiTheme.TEXT : UiTheme.MUTED);
			String count = Integer.toString(Modules.in(item).size());
			UiTheme.pill(g, left + NAV_WIDTH - 30, y + 9, left + NAV_WIDTH - 16, y + 23, active ? 0x3366E3FF : 0x22141920);
			UiTheme.drawScaled(g, font, count, left + NAV_WIDTH - 26, y + 13, 0.68f, active ? UiTheme.ACCENT : UiTheme.DIM);
			hitboxes.add(new Hitbox(Type.CATEGORY, null, item, left + 9, y, left + NAV_WIDTH - 9, y + 32));
			y += 39;
		}
		UiTheme.drawScaled(g, font, "AURELIA / 0.1", left + 16, bottom - 20, 0.68f, UiTheme.DIM);
	}

	private void drawModules(GuiGraphics g, int left, int top, int right, int bottom, int mouseX, int mouseY) {
		int enabled = (int) Modules.in(category).stream().filter(Module::enabled).count();
		UiTheme.draw(g, font, category.title(), left + 16, top + 14, UiTheme.TEXT);
		UiTheme.drawScaled(g, font, enabled + " ACTIVE / " + Modules.in(category).size() + " SYSTEMS", left + 16, top + 29, 0.68f, UiTheme.MUTED);
		int y = top + 49;
		for (Module module : Modules.in(category)) {
			if (y + ROW_HEIGHT > bottom - 10) break;
			boolean hovered = inside(mouseX, mouseY, left + 11, y, right - 11, y + ROW_HEIGHT - 6);
			boolean focused = module == selected;
			float hover = animate(hoverAnimations, module, hovered ? 1 : 0, 12);
			int baseCard = focused ? 0xE217202D : UiTheme.SURFACE;
			int card = UiTheme.mixColor(baseCard, UiTheme.SURFACE_HOVER, hover);
			UiTheme.roundedRect(g, left + 11, y, right - 11, y + ROW_HEIGHT - 6, 9, focused ? 0x555F7D9C : UiTheme.BORDER_SOFT);
			UiTheme.roundedRect(g, left + 12, y + 1, right - 12, y + ROW_HEIGHT - 7, 8, card);
			if (module.enabled()) UiTheme.roundedRect(g, left + 12, y + 10, left + 15, y + ROW_HEIGHT - 16, 2, UiTheme.ACCENT);
			UiTheme.draw(g, font, module.name(), left + 21, y + 8, module.enabled() ? UiTheme.TEXT : 0xFFD2DAE5);
			if (right - left > 188) UiTheme.drawScaled(g, font, module.description(), left + 21, y + 23, 0.68f, UiTheme.MUTED);
			drawToggle(g, right - 45, y + 12, module);
			hitboxes.add(new Hitbox(Type.MODULE, module, null, left + 11, y, right - 11, y + ROW_HEIGHT - 6));
			y += ROW_HEIGHT;
		}
	}

	private void drawDetails(GuiGraphics g, int left, int top, int right, int bottom, int mouseX, int mouseY) {
		int dWidth = right - left;
		boolean narrow = dWidth < 130;
		boolean veryNarrow = dWidth < 110;

		// Extremely compressed for 5 settings on tiny panels
		UiTheme.drawScaled(g, font, "PROFILE", left + 6, top + 4, veryNarrow ? 0.42f : (narrow ? 0.44f : 0.50f), UiTheme.DIM);
		if (selected == null) return;

		UiTheme.draw(g, font, selected.name(), left + 6, top + 12, UiTheme.TEXT);
		String status = selected.enabled() ? "ON" : "OFF";
		UiTheme.drawScaled(g, font, status, left + 6, top + 20, 0.44f, selected.enabled() ? UiTheme.ENABLED : UiTheme.MUTED);
		UiTheme.drawScaled(g, font, selected.description(), left + 6, top + 28, narrow ? 0.40f : 0.46f, UiTheme.MUTED);

		// Settings - minimal cards to fit all 5
		int settingCount = selected.getSettingCount();
		if (settingCount > 0) {
			int y = top + (veryNarrow ? 38 : (narrow ? 40 : 46));
			int cardH = veryNarrow ? 12 : (narrow ? 13 : (settingCount >= 5 ? 14 : 16));

			for (int i = 0; i < settingCount; i++) {
				if (y + cardH + 0 > bottom - 6) break;

				String sname = selected.getSettingName(i).toUpperCase();
				String sval = selected.getSettingValue(i);

				UiTheme.roundedRect(g, left + 6, y - 1, right - 6, y + cardH + 1, 2, 0x223A4557);
				boolean hovered = inside(mouseX, mouseY, left + 7, y, right - 7, y + cardH);
				int bg = hovered ? UiTheme.mixColor(UiTheme.SURFACE, UiTheme.SURFACE_HOVER, 0.6f) : UiTheme.SURFACE;
				UiTheme.roundedRect(g, left + 7, y, right - 7, y + cardH, 2, bg);

				float labelScale = veryNarrow ? 0.36f : (narrow ? 0.38f : 0.40f);
				UiTheme.drawScaled(g, font, sname, left + 8, y + 0, labelScale, UiTheme.DIM);
				UiTheme.draw(g, font, sval, left + 8, y + 5, UiTheme.TEXT);

				hitboxes.add(new Hitbox(Type.SETTING, selected, null, left + 7, y, right - 7, y + cardH, i));
				y += cardH + 0;
			}
		}

		UiTheme.drawScaled(g, font, "L TOGGLE  R CYCLE", left + 6, bottom - 5, 0.36f, UiTheme.DIM);
	}

	private void drawToggle(GuiGraphics g, int x, int y, Module module) {
		float progress = animate(toggleAnimations, module, module.enabled() ? 1 : 0, 14);
		UiTheme.pill(g, x, y, x + 27, y + 15, UiTheme.mixColor(0xFF29313D, 0x4466E3FF, progress));
		UiTheme.pill(g, x + 1, y + 1, x + 26, y + 14, UiTheme.mixColor(0xFF1A2029, 0xFF245B70, progress));
		float knobX = x + 3 + progress * 12;
		UiTheme.roundedRect(g, knobX, y + 3, knobX + 9, y + 12, 4, UiTheme.mixColor(0xFF778493, UiTheme.ACCENT, progress));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Hitbox hitbox : hitboxes) {
			if (!hitbox.contains(mouseX, mouseY)) continue;
			if (hitbox.type == Type.CATEGORY) {
				category = hitbox.category;
				selected = Modules.in(category).stream().findFirst().orElse(null);
			} else if (hitbox.type == Type.MODULE) {
				selected = hitbox.module;
				if (button == 0) hitbox.module.toggle();
				else if (button == 1) {
					hitbox.module.cycleSetting(0); // cycle primary
					if (hitbox.module == Modules.TEST_DUMMY) TestDummyManager.refreshArmor();
				}
				Modules.save();
			} else if (hitbox.type == Type.SETTING) {
				int si = hitbox.settingIndex >= 0 ? hitbox.settingIndex : 0;
				hitbox.module.cycleSetting(si);
				if (hitbox.module == Modules.TEST_DUMMY && si == 0) TestDummyManager.refreshArmor();
				Modules.save();
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static boolean inside(double x, double y, int x1, int y1, int x2, int y2) {
		return x >= x1 && x < x2 && y >= y1 && y < y2;
	}

	private <T> float animate(Map<T, Float> values, T key, float target, float speed) {
		float value = values.getOrDefault(key, target);
		float next = target + (value - target) * (float) Math.exp(-speed * frameDelta);
		values.put(key, next);
		return next;
	}

	private enum Type { CATEGORY, MODULE, SETTING }

	private record Hitbox(Type type, Module module, Category category, int x1, int y1, int x2, int y2, int settingIndex) {
		Hitbox(Type type, Module module, Category category, int x1, int y1, int x2, int y2) {
			this(type, module, category, x1, y1, x2, y2, -1);
		}

		boolean contains(double x, double y) {
			return inside(x, y, x1, y1, x2, y2);
		}
	}
}
