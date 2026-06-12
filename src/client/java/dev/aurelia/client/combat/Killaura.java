package dev.aurelia.client.combat;

import dev.aurelia.client.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Killaura focused on anticheat bypass.
 *
 * Features:
 * - Smooth limited + per-tick randomized turn speed (inspired by LiquidBounce patterns)
 * - Randomized aim point on hitbox
 * - Natural target prioritization (angle to current view)
 * - True silent rotations: when enabled, sends ServerboundMovePlayerPacket.Rot but does NOT change your client view.
 *   Server sees the correct rotation for the attack, your camera stays free.
 * - Attack timing randomization (variable extra delay + cooldown variance based on "Attack Rand" setting)
 * - Multiple independent settings (Range, Turn Speed profile, Silent On/Off, Attack Rand level)
 *
 * Use "Silent: On" + lower "Turn Speed" + "Attack Rand: Med/High" for better bypass on modern ACs.
 */
public final class Killaura {
	private static final Random RANDOM = new Random();

	private static LivingEntity currentTarget;
	private static float auraYaw;
	private static float auraPitch;
	private static boolean rotationInitialized;
	private static int attackDelay = 0;

	// Stability for aim point: prevents super-fast 1-frame body-part switching
	// which is very detectable. We stick to one randomized point on the model
	// for several ticks before picking a new "part" to attack.
	private static Vec3 stableAimPoint;
	private static LivingEntity lastAimedTarget;
	private static int aimStabilityTicks = 0;

	private Killaura() {}

	public static void tick(Minecraft client) {
		if (!Modules.KILLAURA.enabled() || client.player == null || client.level == null || client.gameMode == null) {
			currentTarget = null;
			rotationInitialized = false;
			attackDelay = 0;
			stableAimPoint = null;
			lastAimedTarget = null;
			aimStabilityTicks = 0;
			return;
		}

		if (attackDelay > 0) {
			attackDelay--;
			return;
		}

		Player player = client.player;

		float range = getFloatSetting("Range", 4.0f);
		String turnProfile = Modules.KILLAURA.getSettingValueByName("Turn Speed");
		boolean silent = "On".equals(Modules.KILLAURA.getSettingValueByName("Silent"));
		String randLevel = Modules.KILLAURA.getSettingValueByName("Attack Rand");

		TurnSpeeds turn = getTurnSpeeds(turnProfile);
		int extraDelay = getExtraAttackDelay(randLevel);

		// Find candidates
		AABB searchBox = player.getBoundingBox().inflate(range + 2);
		List<LivingEntity> candidates = client.level.getEntitiesOfClass(
			LivingEntity.class,
			searchBox,
			e -> isValidTarget(player, e, range)
		);

		currentTarget = candidates.stream()
			.min(Comparator.comparingDouble(e -> scoreTarget(player, e)))
			.orElse(null);

		if (currentTarget == null) {
			// Clear stability when no target
			stableAimPoint = null;
			lastAimedTarget = null;
			aimStabilityTicks = 0;
			return;
		}

		// --- Stable aim point (key anti-detect fix) ---
		// Only pick a new random point on the model every N ticks.
		// This stops the killaura from "kept changing parts" super fast (1-frame jitter on different body parts).
		if (currentTarget != lastAimedTarget || aimStabilityTicks <= 0 || stableAimPoint == null) {
			stableAimPoint = getRandomizedAimPoint(currentTarget);
			// Hold the same body part for a while (longer = less detectable twitching)
			aimStabilityTicks = 8 + RANDOM.nextInt(10); // ~0.4 - 0.9 seconds of stability
			lastAimedTarget = currentTarget;
		}
		aimStabilityTicks--;

		// Cooldown check + variance from rand setting
		float cooldown = player.getAttackStrengthScale(0.0f);
		float required = getRequiredCooldown(randLevel);
		if (cooldown < required) return;

		// Use the *stable* aim point (doesn't twitch between body parts rapidly).
		Vec3 aimPoint = (stableAimPoint != null) ? stableAimPoint : getRandomizedAimPoint(currentTarget);
		float[] desired = computeYawPitchToPoint(player, aimPoint);

		if (silent) {
			// TRUE SILENT: Do not touch client POV at all.
			// Compute exact needed rotation for this attack and send it in one packet.
			// No limited "turning" animation in packets or on screen.
			// This is the standard way for good bypass while moving/walking.
			auraYaw = desired[0];
			auraPitch = desired[1];

			var conn = client.getConnection();
			if (conn != null) {
				conn.send(new ServerboundMovePlayerPacket.Rot(auraYaw, auraPitch, player.onGround()));
			}
		} else {
			// Visible mode: use the (now much slower) limited turn speed so the
			// camera physically turns gradually toward the target over many frames.
			// This prevents the "POV snaps in ~10 frames" while walking.
			updateLimitedRotation(desired[0], desired[1], turn.min, turn.max);
			applyPlayerRotation(player, auraYaw, auraPitch);
		}

		// Attack + schedule randomization delay
		client.gameMode.attack(player, currentTarget);
		player.swing(InteractionHand.MAIN_HAND);

		attackDelay = extraDelay;
	}

	public static LivingEntity getCurrentTarget() {
		return currentTarget;
	}

	// --- Settings helpers (now using multiple settings) ---

	private static float getFloatSetting(String name, float def) {
		try {
			return Float.parseFloat(Modules.KILLAURA.getSettingValueByName(name));
		} catch (Exception e) {
			return def;
		}
	}

	private record TurnSpeeds(float min, float max) {}

	private static TurnSpeeds getTurnSpeeds(String profile) {
		// These are only used for visible (non-silent) mode.
		// Kept quite low so even visible turning looks more natural and less detectable
		// when walking around targets. 10-20 °/tick is ~0.5-1s for a 90-180° turn.
		return switch (profile) {
			case "Slow" -> new TurnSpeeds(8f, 18f);
			case "Normal" -> new TurnSpeeds(18f, 35f);
			case "Fast" -> new TurnSpeeds(35f, 60f);
			default -> new TurnSpeeds(80f, 140f); // Blatant - still limited but faster
		};
	}

	private static float getRequiredCooldown(String randLevel) {
		float base = 0.90f;
		return switch (randLevel) {
			case "Med" -> base + (RANDOM.nextFloat() < 0.3f ? 0.02f : 0f);
			case "High" -> base + 0.02f + RANDOM.nextFloat() * 0.05f;
			default -> base;
		};
	}

	private static int getExtraAttackDelay(String randLevel) {
		return switch (randLevel) {
			case "Med" -> RANDOM.nextInt(2);           // 0-1 extra ticks
			case "High" -> 1 + RANDOM.nextInt(3);     // 1-3 extra ticks
			default -> 0;
		};
	}

	// --- Targeting / Rotation helpers (unchanged core logic) ---

	private static boolean isValidTarget(Player self, LivingEntity entity, float range) {
		if (entity == self || !entity.isAlive() || entity.isSpectator()) return false;
		if (entity instanceof Player p && p.isCreative()) return false;
		return self.distanceToSqr(entity) <= (range * range);
	}

	private static double scoreTarget(Player self, LivingEntity target) {
		double dist = self.distanceTo(target);
		float[] toTarget = computeYawPitchToPoint(self, target.getEyePosition());
		float yawDiff = Math.abs(Mth.wrapDegrees(toTarget[0] - self.getYRot()));
		float pitchDiff = Math.abs(Mth.wrapDegrees(toTarget[1] - self.getXRot()));
		double angleCost = (yawDiff * 1.0 + pitchDiff * 0.6) * 0.012;
		return dist + angleCost;
	}

	private static Vec3 getRandomizedAimPoint(LivingEntity target) {
		AABB box = target.getBoundingBox();
		double w = box.getXsize();
		double h = box.getYsize();
		double d = box.getZsize();
		double rx = box.minX + RANDOM.nextDouble() * w;
		double ry = box.minY + RANDOM.nextDouble() * h * 0.75 + (h * 0.15);
		double rz = box.minZ + RANDOM.nextDouble() * d;
		return new Vec3(rx, ry, rz);
	}

	private static float[] computeYawPitchToPoint(Player self, Vec3 point) {
		Vec3 eye = self.getEyePosition(1.0f);
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		double distXZ = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0f;
		float pitch = (float) (-(Math.atan2(dy, distXZ) * 180.0D / Math.PI));
		return new float[]{yaw, Mth.clamp(pitch, -90f, 90f)};
	}

	private static void updateLimitedRotation(float desiredYaw, float desiredPitch, float minTurn, float maxTurn) {
		if (!rotationInitialized) {
			auraYaw = desiredYaw;
			auraPitch = desiredPitch;
			rotationInitialized = true;
			return;
		}
		float yawDelta = Mth.wrapDegrees(desiredYaw - auraYaw);
		float pitchDelta = Mth.wrapDegrees(desiredPitch - auraPitch);
		float thisTickTurn = minTurn + RANDOM.nextFloat() * (maxTurn - minTurn);
		float yawStep = Mth.clamp(yawDelta, -thisTickTurn, thisTickTurn);
		float pitchStep = Mth.clamp(pitchDelta, -thisTickTurn, thisTickTurn);
		auraYaw = Mth.wrapDegrees(auraYaw + yawStep);
		auraPitch = Mth.clamp(auraPitch + pitchStep, -90f, 90f);
	}

	private static void applyPlayerRotation(Player player, float yaw, float pitch) {
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yHeadRot = yaw;
		player.yBodyRot = yaw;
		player.yRotO = yaw;
		player.xRotO = pitch;
		player.yHeadRotO = yaw;
		player.yBodyRotO = yaw;
	}
}
