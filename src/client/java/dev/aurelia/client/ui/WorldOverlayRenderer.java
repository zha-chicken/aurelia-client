package dev.aurelia.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aurelia.client.AureliaClient;
import dev.aurelia.client.analysis.SituationAnalysis;
import dev.aurelia.client.combat.Killaura;
import dev.aurelia.client.module.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class WorldOverlayRenderer {
	private WorldOverlayRenderer() {}

	public static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;

		PoseStack matrices = context.matrixStack();
		if (matrices == null || context.consumers() == null) return;

		SituationAnalysis.Snapshot snapshot = SituationAnalysis.capture(client);
		Vec3 camera = context.camera().getPosition();

		if (Modules.CRYSTAL_RISK.enabled()) drawCrystalFills(context, matrices, snapshot, camera);
		if (Modules.COMBAT_ADVISOR.enabled() && AureliaClient.suggestionsHeld) drawHeatmapFills(context, matrices, snapshot, camera);

		VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());
		if (Modules.CRYSTAL_RISK.enabled()) drawCrystalOutlines(matrices, lines, snapshot, camera);
		if (Modules.COMBAT_ADVISOR.enabled() && AureliaClient.suggestionsHeld) {
			drawHeatmapOutlines(matrices, lines, snapshot, camera);
			drawAdvisor(matrices, lines, snapshot, camera);
		}
		if (Modules.CRYSTAL_RISK.enabled()) drawCrystalDamageLabels(context, matrices, snapshot, camera);

		// Killaura target box: get a fresh lines consumer to avoid "Not building!" state issues
		// with previously-used RenderType.lines() buffers in the same AFTER_ENTITIES pass.
		if (Modules.KILLAURA.enabled()) {
			LivingEntity killauraTarget = Killaura.getCurrentTarget();
			if (killauraTarget != null) {
				VertexConsumer killauraLines = context.consumers().getBuffer(RenderType.lines());
				drawKillauraTarget(matrices, killauraLines, killauraTarget, camera);
			}
		}
	}

	private static void drawCrystalFills(WorldRenderContext context, PoseStack matrices, SituationAnalysis.Snapshot snapshot, Vec3 camera) {
		VertexConsumer fill = context.consumers().getBuffer(RenderType.debugFilledBox());
		for (SituationAnalysis.CrystalRisk risk : snapshot.crystalRisks()) {
			AABB crystalBox = risk.crystal().getBoundingBox().inflate(0.16).move(-camera.x, -camera.y, -camera.z);

			LevelRenderer.addChainedFilledBoxVertices(matrices, fill,
				crystalBox.minX, crystalBox.minY, crystalBox.minZ, crystalBox.maxX, crystalBox.maxY, crystalBox.maxZ,
				0.95f, 0.12f, 0.20f, 0.20f);
		}
	}

	private static void drawHeatmapFills(WorldRenderContext context, PoseStack matrices, SituationAnalysis.Snapshot snapshot, Vec3 camera) {
		VertexConsumer fill = context.consumers().getBuffer(RenderType.debugFilledBox());
		for (SituationAnalysis.CrystalCandidate candidate : snapshot.crystalCandidates()) {
			boolean best = candidate.pos().equals(snapshot.bestCrystalPos());
			float[] color = best ? new float[] {0.30f, 1.0f, 0.64f} : heatColor(candidate.heat());
			AABB box = new AABB(candidate.pos().above()).inflate(0.015).move(-camera.x, -camera.y, -camera.z);
			LevelRenderer.addChainedFilledBoxVertices(matrices, fill,
				box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
				color[0], color[1], color[2], best ? 0.24f : 0.10f);
		}
	}

	private static void drawCrystalOutlines(PoseStack matrices, VertexConsumer lines, SituationAnalysis.Snapshot snapshot, Vec3 camera) {
		for (SituationAnalysis.CrystalRisk risk : snapshot.crystalRisks()) {
			AABB crystalBox = risk.crystal().getBoundingBox().inflate(0.16).move(-camera.x, -camera.y, -camera.z);
			LevelRenderer.renderLineBox(matrices, lines, crystalBox, 1.0f, 0.28f, 0.36f, 0.98f);
		}
	}

	private static void drawCrystalDamageLabels(WorldRenderContext context, PoseStack matrices, SituationAnalysis.Snapshot snapshot, Vec3 camera) {
		Minecraft client = Minecraft.getInstance();
		MultiBufferSource buffers = context.consumers();
		double effectiveHealth = client.player.getHealth() + client.player.getAbsorptionAmount();

		for (SituationAnalysis.CrystalRisk risk : snapshot.crystalRisks()) {
			Vec3 labelPos = risk.crystal().position().add(0, -0.45, 0).subtract(camera);
			Component damageLabel = UiTheme.text(String.format("%.1f DMG", risk.predictedDamage()));
			Component remainingLabel = UiTheme.text(String.format("LEFT %.1f", Math.max(0, effectiveHealth - risk.predictedDamage())));
			Component lethalMarker = UiTheme.text("!");
			float damageWidth = client.font.width(damageLabel);
			float remainingWidth = client.font.width(remainingLabel);
			boolean lethal = risk.predictedDamage() >= effectiveHealth;
			double distance = client.player.distanceTo(risk.crystal());
			float scale = 0.025f * UiTheme.FONT_SCALE * Mth.clamp(1.18f - (float) distance / 24f, 0.58f, 1.0f);
			int alpha = risk.exposure() <= 0.02 && risk.predictedDamage() < 2
				? 72
				: risk.exposure() < 0.18 && risk.predictedDamage() < 4 ? 145 : 255;
			int primaryColor = withAlpha(lethal ? 0xFFFF3658 : 0xFFF7F0FA, alpha);
			int secondaryColor = withAlpha(lethal ? 0xFFFF7A90 : 0xFFAAA5BC, alpha);
			int background = withAlpha(lethal ? 0xFF500716 : 0xFF18141F, Math.min(alpha, lethal ? 190 : 160));

			matrices.pushPose();
			matrices.translate(labelPos.x, labelPos.y, labelPos.z);
			matrices.mulPose(context.camera().rotation());
			matrices.scale(scale, -scale, scale);
			Matrix4f matrix = matrices.last().pose();
			client.font.drawInBatch(
				damageLabel,
				-damageWidth / 2,
				-5,
				primaryColor,
				false,
				matrix,
				buffers,
				Font.DisplayMode.SEE_THROUGH,
				background,
				0xF000F0
			);
			client.font.drawInBatch(
				remainingLabel,
				-remainingWidth / 2,
				6,
				secondaryColor,
				false,
				matrix,
				buffers,
				Font.DisplayMode.SEE_THROUGH,
				background,
				0xF000F0
			);
			if (lethal) {
				client.font.drawInBatch(
					lethalMarker,
					-Math.max(damageWidth, remainingWidth) / 2 - 10,
					0,
					0xFFFF2448,
					false,
					matrix,
					buffers,
					Font.DisplayMode.SEE_THROUGH,
					0xB0500716,
					0xF000F0
				);
			}
			matrices.popPose();
		}
	}

	private static int withAlpha(int color, int alpha) {
		return color & 0x00FFFFFF | Mth.clamp(alpha, 0, 255) << 24;
	}

	private static void drawHeatmapOutlines(PoseStack matrices, VertexConsumer lines, SituationAnalysis.Snapshot snapshot, Vec3 camera) {
		for (SituationAnalysis.CrystalCandidate candidate : snapshot.crystalCandidates()) {
			boolean best = candidate.pos().equals(snapshot.bestCrystalPos());
			float[] color = best ? new float[] {0.30f, 1.0f, 0.64f} : heatColor(candidate.heat());
			AABB box = new AABB(candidate.pos().above()).inflate(best ? 0.055 : 0.02).move(-camera.x, -camera.y, -camera.z);
			LevelRenderer.renderLineBox(matrices, lines, box, color[0], color[1], color[2], best ? 1.0f : 0.78f);
		}
	}

	private static void drawAdvisor(PoseStack matrices, VertexConsumer lines, SituationAnalysis.Snapshot snapshot, Vec3 camera) {
		if (snapshot.target() != null) {
			AABB targetBox = snapshot.target().getBoundingBox().inflate(0.08).move(-camera.x, -camera.y, -camera.z);
			LevelRenderer.renderLineBox(matrices, lines, targetBox, 1.0f, 0.42f, 0.58f, 0.95f);
		}

		if (snapshot.bestCrystalPos() != null) {
			AABB crystalBox = new AABB(snapshot.bestCrystalPos().above()).inflate(0.03).move(-camera.x, -camera.y, -camera.z);
			LevelRenderer.renderLineBox(matrices, lines, crystalBox, 0.48f, 0.90f, 0.76f, 0.95f);
		}
	}

	private static void drawKillauraTarget(PoseStack matrices, VertexConsumer lines, LivingEntity target, Vec3 camera) {
		AABB box = target.getBoundingBox().inflate(0.12).move(-camera.x, -camera.y, -camera.z);
		// Aggressive red-pink highlight for active killaura target
		LevelRenderer.renderLineBox(matrices, lines, box, 1.0f, 0.25f, 0.30f, 0.95f);
	}

	private static float[] heatColor(SituationAnalysis.Heat heat) {
		return switch (heat) {
			case STRONG -> new float[] {0.38f, 0.94f, 0.68f};
			case NEUTRAL -> new float[] {1.0f, 0.76f, 0.32f};
			case DANGER -> new float[] {1.0f, 0.24f, 0.34f};
		};
	}
}
