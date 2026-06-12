package dev.aurelia.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class UiTheme {
	public static final int BACKDROP = 0xD5090B11;
	public static final int WINDOW = 0xF20A0D14;
	public static final int GLASS = 0xD9121721;
	public static final int GLASS_STRONG = 0xF0161C28;
	public static final int SURFACE = 0xC9141A25;
	public static final int SURFACE_HOVER = 0xEE1B2432;
	public static final int BORDER = 0x99506A85;
	public static final int BORDER_SOFT = 0x55465C76;
	public static final int TEXT = 0xFFF2F7FF;
	public static final int MUTED = 0xFF8290A3;
	public static final int DIM = 0xFF536172;
	public static final int ACCENT = 0xFF66E3FF;
	public static final int ACCENT_2 = 0xFFA78BFA;
	public static final int ENABLED = 0xFF6FFFC1;
	public static final int WARNING = 0xFFFFC86B;
	public static final int DANGER = 0xFFFF5577;
	public static final ResourceLocation UI_FONT = ResourceLocation.fromNamespaceAndPath("aurelia", "ui");
	public static final float FONT_SCALE = 0.70f;

	private UiTheme() {}

	public static Component text(String value) {
		return Component.literal(value).withStyle(style -> style.withFont(UI_FONT));
	}

	public static int width(Font font, String value) {
		return Math.round(font.width(text(value)) * FONT_SCALE);
	}

	public static void draw(GuiGraphics graphics, Font font, String value, int x, int y, int color) {
		drawScaled(graphics, font, value, x, y, 1, color);
	}

	public static void drawScaled(GuiGraphics graphics, Font font, String value, int x, int y, float scale, int color) {
		float renderScale = FONT_SCALE * scale;
		graphics.pose().pushPose();
		graphics.pose().scale(renderScale, renderScale, 1);
		graphics.drawString(font, text(value), Math.round(x / renderScale), Math.round(y / renderScale), color, false);
		graphics.pose().popPose();
	}

	public static void panel(GuiGraphics graphics, int x1, int y1, int x2, int y2, int radius, int fill) {
		roundedRect(graphics, x1 - 4, y1 - 4, x2 + 4, y2 + 4, radius + 4, 0x16000000);
		roundedRect(graphics, x1 - 2, y1 - 2, x2 + 2, y2 + 2, radius + 2, 0x28000000);
		roundedRect(graphics, x1, y1, x2, y2, radius, BORDER_SOFT);
		roundedRect(graphics, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, radius - 1), fill);
	}

	public static void pill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
		roundedRect(graphics, x1, y1, x2, y2, Math.max(1, (y2 - y1) / 2), color);
	}

	public static void horizontalGradient(GuiGraphics graphics, int x1, int y1, int x2, int y2, int start, int end) {
		int width = Math.max(1, x2 - x1);
		for (int x = 0; x < width; x++) {
			float progress = width == 1 ? 0 : x / (float) (width - 1);
			graphics.fill(x1 + x, y1, x1 + x + 1, y2, mixColor(start, end, progress));
		}
	}

	public static int withAlpha(int color, int alpha) {
		return color & 0x00FFFFFF | Mth.clamp(alpha, 0, 255) << 24;
	}

	public static int mixColor(int start, int end, float progress) {
		progress = Mth.clamp(progress, 0, 1);
		int a = Mth.lerpInt(progress, start >>> 24, end >>> 24);
		int r = Mth.lerpInt(progress, start >> 16 & 255, end >> 16 & 255);
		int g = Mth.lerpInt(progress, start >> 8 & 255, end >> 8 & 255);
		int b = Mth.lerpInt(progress, start & 255, end & 255);
		return a << 24 | r << 16 | g << 8 | b;
	}

	public static void roundedRect(GuiGraphics graphics, float x1, float y1, float x2, float y2, float radius, int color) {
		if (x2 <= x1 || y2 <= y1) return;
		SdfRoundedRectRenderer.draw(graphics, x1, y1, x2, y2, radius, color);
	}
}
