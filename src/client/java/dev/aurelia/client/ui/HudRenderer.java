package dev.aurelia.client.ui;

import dev.aurelia.client.AureliaClient;
import dev.aurelia.client.analysis.SituationAnalysis;
import dev.aurelia.client.module.Module;
import dev.aurelia.client.module.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.BlockItem;
import net.minecraft.util.Mth;

public final class HudRenderer {
	private static float targetPresence;
	private static float crystalPresence;

	private HudRenderer() {}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui || client.player == null || client.level == null) return;

		SituationAnalysis.Snapshot snapshot = SituationAnalysis.capture(client);
		float delta = deltaTracker.getGameTimeDeltaPartialTick(false);
		targetPresence = approach(targetPresence, snapshot.target() == null ? 0 : 1, delta * 0.12f);
		crystalPresence = approach(crystalPresence, Double.isFinite(snapshot.nearestCrystal()) ? 1 : 0, delta * 0.12f);

		if (Modules.WATERMARK.enabled()) drawWatermark(graphics, client);
		if (Modules.STATUS.enabled()) drawStatus(graphics, client);
		if (Modules.ACTIVE_MODULES.enabled()) drawActiveModules(graphics, client);
		if (Modules.THREAT_INTEL.enabled() && targetPresence > 0.02f) drawThreatCard(graphics, client, snapshot);
		if (Modules.CRYSTAL_RISK.enabled() && crystalPresence > 0.02f) drawCrystalRisk(graphics, client, snapshot);
		if (Modules.CRYSTAL_RISK.enabled() && snapshot.hasLethalCrystal(client.player)) drawLethalBorder(graphics);
		if (Modules.SCAFFOLD_GUIDE.enabled()) drawScaffoldGuide(graphics, client);
		if (Modules.COMBAT_ADVISOR.enabled() && AureliaClient.suggestionsHeld) drawCombatAdvisor(graphics, client, snapshot);
	}

	private static void drawCombatAdvisor(GuiGraphics g, Minecraft client, SituationAnalysis.Snapshot snapshot) {
		int width = 272;
		int x = g.guiWidth() / 2 - width / 2;
		int y = g.guiHeight() - 118;
		panel(g, x, y, x + width, y + 62, 0.96f);
		UiTheme.horizontalGradient(g, x + 17, y, x + width - 17, y + 2, UiTheme.ACCENT, UiTheme.ACCENT_2);
		UiTheme.drawScaled(g, client.font, "COMBAT ADVISOR / LIVE ANALYSIS", x + 13, y + 9, 0.66f, UiTheme.ACCENT);
		UiTheme.draw(g, client.font, snapshot.suggestion(), x + 13, y + 21, UiTheme.TEXT);
		UiTheme.drawScaled(g, client.font, snapshot.combatDetail(), x + 13, y + 38, 0.72f, UiTheme.MUTED);
		UiTheme.drawScaled(g, client.font, snapshot.crystalDetail(), x + 13, y + 50, 0.68f,
			snapshot.bestCrystalPos() == null ? UiTheme.DIM : UiTheme.ENABLED);
	}

	private static void drawWatermark(GuiGraphics g, Minecraft client) {
		panel(g, 12, 12, 133, 42, 0.92f);
		UiTheme.roundedRect(g, 18, 18, 37, 36, 6, 0x2466E3FF);
		UiTheme.horizontalGradient(g, 23, 23, 32, 31, UiTheme.ACCENT, UiTheme.ACCENT_2);
		UiTheme.draw(g, client.font, "AURELIA", 44, 18, UiTheme.TEXT);
		UiTheme.drawScaled(g, client.font, "VISUAL INTERFACE", 44, 31, 0.64f, UiTheme.MUTED);
		UiTheme.pill(g, 105, 20, 126, 29, 0x226FFFC1);
		UiTheme.drawScaled(g, client.font, "LIVE", 109, 22, 0.55f, UiTheme.ENABLED);
	}

	private static void drawStatus(GuiGraphics g, Minecraft client) {
		int width = g.guiWidth();
		String fps = client.getFps() + " FPS";
		String coords = (int) client.player.getX() + " / " + (int) client.player.getY() + " / " + (int) client.player.getZ();
		int boxWidth = Math.max(UiTheme.width(client.font, fps), UiTheme.width(client.font, coords)) + 24;
		int x = width - boxWidth - 12;
		panel(g, x, 12, width - 12, 46, 0.88f);
		UiTheme.draw(g, client.font, fps, x + 12, 19, UiTheme.TEXT);
		UiTheme.drawScaled(g, client.font, coords, x + 12, 33, 0.68f, UiTheme.MUTED);
		UiTheme.roundedRect(g, width - 20, 20, width - 16, 24, 2, UiTheme.ENABLED);
	}

	private static void drawActiveModules(GuiGraphics g, Minecraft client) {
		int y = 57;
		boolean left = Modules.ACTIVE_MODULES.settingValue().equals("Left");
		for (Module module : Modules.enabled()) {
			if (module == Modules.ACTIVE_MODULES || module == Modules.WATERMARK || module == Modules.STATUS) continue;
			int textWidth = UiTheme.width(client.font, module.name());
			int x = left ? 20 : g.guiWidth() - textWidth - 20;
			int x1 = left ? 12 : x - 8;
			int x2 = left ? x + textWidth + 8 : g.guiWidth() - 12;
			UiTheme.pill(g, x1, y - 4, x2, y + 13, 0xA0121721);
			UiTheme.roundedRect(g, left ? x1 + 4 : x2 - 7, y + 2, left ? x1 + 7 : x2 - 4, y + 7, 2, UiTheme.ACCENT);
			UiTheme.draw(g, client.font, module.name(), x, y, UiTheme.TEXT);
			y += 19;
		}
	}

	private static void drawScaffoldGuide(GuiGraphics g, Minecraft client) {
		boolean blockHeld = client.player.getMainHandItem().getItem() instanceof BlockItem
			|| client.player.getOffhandItem().getItem() instanceof BlockItem;
		var probe = client.player.blockPosition().below();
		if (Modules.SCAFFOLD_GUIDE.settingValue().equals("Forward")) {
			probe = probe.relative(client.player.getDirection());
		}
		boolean airBelow = client.level.getBlockState(probe).isAir();
		String text = !blockHeld ? "SCAFFOLD / HOLD A BLOCK" : airBelow ? "SCAFFOLD / PLACE READY" : "SCAFFOLD / APPROACH EDGE";
		int color = !blockHeld ? UiTheme.WARNING : airBelow ? UiTheme.ENABLED : UiTheme.MUTED;
		int x = g.guiWidth() / 2 - UiTheme.width(client.font, text) / 2;
		int y = g.guiHeight() - 54;
		UiTheme.pill(g, x - 10, y - 6, x + UiTheme.width(client.font, text) + 10, y + 15, 0xB0121721);
		UiTheme.draw(g, client.font, text, x, y, color);
	}

	private static void drawThreatCard(GuiGraphics g, Minecraft client, SituationAnalysis.Snapshot snapshot) {
		int y = g.guiHeight() - 88 + (int) ((1 - ease(targetPresence)) * 24);
		int alpha = (int) (targetPresence * 255);
		panel(g, 12, y, 194, y + 66, targetPresence * 0.92f);
		if (snapshot.target() == null) return;

		int threatColor = snapshot.threatScore() > 70 ? UiTheme.DANGER : snapshot.threatScore() > 40 ? UiTheme.WARNING : UiTheme.ENABLED;
		UiTheme.drawScaled(g, client.font, "THREAT INTELLIGENCE", 23, y + 10, 0.64f, withAlpha(UiTheme.MUTED, alpha));
		UiTheme.draw(g, client.font, snapshot.target().getName().getString(), 23, y + 22, withAlpha(UiTheme.TEXT, alpha));
		UiTheme.drawScaled(g, client.font, String.format("%.1fm / %d ARMOR", snapshot.targetDistance(), snapshot.target().getArmorValue()), 23, y + 38, 0.68f, withAlpha(UiTheme.MUTED, alpha));
		UiTheme.pill(g, 23, y + 53, 181, y + 57, withAlpha(0xFF263342, alpha));
		UiTheme.pill(g, 23, y + 53, 23 + (int) (158 * snapshot.threatScore() / 100f), y + 57, withAlpha(threatColor, alpha));
		UiTheme.drawScaled(g, client.font, Integer.toString(snapshot.threatScore()), 166, y + 23, 0.68f, withAlpha(threatColor, alpha));
	}

	private static void drawCrystalRisk(GuiGraphics g, Minecraft client, SituationAnalysis.Snapshot snapshot) {
		String label = snapshot.crystalLabel(client.player);
		double damage = snapshot.highestCrystalDamage();
		double remaining = Math.max(0, snapshot.healthAfterWorstCrystal(client.player));
		int color = switch (label) {
			case "LETHAL" -> 0xFFFF3658;
			case "CRITICAL" -> 0xFFFF6B86;
			case "CAUTION" -> 0xFFFFCA72;
			default -> 0xFF79E6C2;
		};
		String text = String.format("CRYSTAL %s  DMG %.1f  LEFT %.1f", label, damage, remaining);
		float scale = 0.75f;
		int x = 18;
		int y = Modules.WATERMARK.enabled() ? 49 : 17;
		int alpha = (int) (crystalPresence * 255);

		UiTheme.drawScaled(g, client.font, text, x, y, scale, withAlpha(color, alpha));
	}

	private static void drawLethalBorder(GuiGraphics g) {
		int width = g.guiWidth();
		int height = g.guiHeight();
		int outer = 0xD8FF2448;
		int middle = 0x78FF2448;
		int inner = 0x28FF2448;

		border(g, 0, width, height, 2, outer);
		border(g, 2, width, height, 4, middle);
		border(g, 6, width, height, 7, inner);
	}

	private static void border(GuiGraphics g, int inset, int width, int height, int thickness, int color) {
		g.fill(inset, inset, width - inset, inset + thickness, color);
		g.fill(inset, height - inset - thickness, width - inset, height - inset, color);
		g.fill(inset, inset + thickness, inset + thickness, height - inset - thickness, color);
		g.fill(width - inset - thickness, inset + thickness, width - inset, height - inset - thickness, color);
	}

	private static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, float opacity) {
		int alpha = Mth.clamp((int) (opacity * 225), 0, 255);
		UiTheme.panel(g, x1, y1, x2, y2, 9, withAlpha(UiTheme.GLASS, alpha));
	}

	private static float approach(float value, float target, float speed) {
		return Mth.lerp(Mth.clamp(speed, 0, 1), value, target);
	}

	private static float ease(float value) {
		return 1 - (float) Math.pow(1 - value, 3);
	}

	private static int withAlpha(int color, int alpha) {
		return color & 0x00FFFFFF | Mth.clamp(alpha, 0, 255) << 24;
	}
}
