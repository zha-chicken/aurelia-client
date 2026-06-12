package dev.aurelia.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class SdfRoundedRectRenderer {
	private static final int FIXED_POINT_SCALE = 32;
	private static final VertexFormat FORMAT = VertexFormat.builder()
		.add("Position", VertexFormatElement.POSITION)
		.add("Color", VertexFormatElement.COLOR)
		.add("UV0", VertexFormatElement.UV0)
		.add("UV1", VertexFormatElement.UV1)
		.add("UV2", VertexFormatElement.UV2)
		.build();
	private static ShaderInstance shader;
	private static final RenderType RENDER_TYPE = new RenderType(
		"aurelia_sdf_rounded_rect",
		FORMAT,
		VertexFormat.Mode.QUADS,
		DefaultVertexFormat.BLOCK.getVertexSize() * 256,
		false,
		true,
		() -> {
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableDepthTest();
			RenderSystem.setShader(() -> shader);
		},
		() -> {
			RenderSystem.enableDepthTest();
			RenderSystem.disableBlend();
		}
	) {};

	private SdfRoundedRectRenderer() {}

	public static void initialize() {
		CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
			ResourceLocation.fromNamespaceAndPath("aurelia", "sdf_rounded_rect"),
			FORMAT,
			loaded -> shader = loaded
		));
	}

	public static void draw(GuiGraphics graphics, float x1, float y1, float x2, float y2, float radius, int color) {
		if (x2 <= x1 || y2 <= y1) return;
		if (shader == null) {
			graphics.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), color);
			return;
		}

		float halfWidth = (x2 - x1) * 0.5f;
		float halfHeight = (y2 - y1) * 0.5f;
		float clampedRadius = Math.max(0, Math.min(radius, Math.min(halfWidth, halfHeight)));
		int encodedRadius = Math.round(clampedRadius * FIXED_POINT_SCALE);
		int encodedHalfWidth = Math.round(halfWidth * FIXED_POINT_SCALE);
		int encodedHalfHeight = Math.round(halfHeight * FIXED_POINT_SCALE);
		Matrix4f pose = graphics.pose().last().pose();
		VertexConsumer vertices = graphics.bufferSource().getBuffer(RENDER_TYPE);

		vertex(vertices, pose, x1, y2, -halfWidth, halfHeight, encodedRadius, encodedHalfWidth, encodedHalfHeight, color);
		vertex(vertices, pose, x2, y2, halfWidth, halfHeight, encodedRadius, encodedHalfWidth, encodedHalfHeight, color);
		vertex(vertices, pose, x2, y1, halfWidth, -halfHeight, encodedRadius, encodedHalfWidth, encodedHalfHeight, color);
		vertex(vertices, pose, x1, y1, -halfWidth, -halfHeight, encodedRadius, encodedHalfWidth, encodedHalfHeight, color);
	}

	private static void vertex(VertexConsumer vertices, Matrix4f pose, float x, float y, float localX, float localY,
							   int radius, int halfWidth, int halfHeight, int color) {
		vertices.addVertex(pose, x, y, 0)
			.setColor(color)
			.setUv(localX, localY)
			.setUv1(radius, 0)
			.setUv2(halfWidth, halfHeight);
	}
}
