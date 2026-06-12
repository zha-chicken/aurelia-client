package dev.aurelia.client.analysis;

import dev.aurelia.client.AureliaClient;
import dev.aurelia.client.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SituationAnalysis {
	private static long cachedGameTime = Long.MIN_VALUE;
	private static ClientLevel cachedLevel;
	private static Snapshot cachedSnapshot = Snapshot.EMPTY;
	private static long candidateGameTime = Long.MIN_VALUE;
	private static UUID candidateTarget;
	private static BlockPos candidateOrigin;
	private static List<CrystalCandidate> cachedCandidates = List.of();

	private SituationAnalysis() {}

	public static Snapshot capture(Minecraft client) {
		if (client.player == null || client.level == null) return Snapshot.EMPTY;
		if (client.level == cachedLevel && client.level.getGameTime() == cachedGameTime) return cachedSnapshot;

		Player target = client.level.players().stream()
			.filter(player -> player != client.player && !player.isSpectator() && player.isAlive())
			.filter(player -> client.player.distanceTo(player) <= threatRange())
			.max(Comparator.comparingDouble(player -> targetPriority(client.player, player)))
			.orElse(null);

		double targetDistance = target == null ? 0 : client.player.distanceTo(target);
		int targetScore = target == null ? 0 : threatScore(client.player, target, targetDistance);
		boolean lineOfSight = target != null && client.player.hasLineOfSight(target);
		float cooldown = client.player.getAttackStrengthScale(0);

		List<EndCrystal> crystals = client.level.getEntitiesOfClass(
				EndCrystal.class,
				client.player.getBoundingBox().inflate(crystalRange())
			);
		List<CrystalRisk> crystalRisks = crystals.stream()
			.map(crystal -> {
				CrystalPrediction prediction = crystalPrediction(client.player, crystal.position());
				return new CrystalRisk(crystal, prediction.damage, prediction.exposure);
			})
			.toList();
		double nearestCrystal = crystals.stream()
			.mapToDouble(client.player::distanceTo)
			.min()
			.orElse(Double.POSITIVE_INFINITY);

		List<CrystalCandidate> candidates = !AureliaClient.suggestionsHeld ? List.of() : cachedCrystalCandidates(client, target);
		CrystalCandidate candidate = candidates.stream().findFirst().orElse(CrystalCandidate.NONE);
		cachedGameTime = client.level.getGameTime();
		cachedLevel = client.level;
		cachedSnapshot = new Snapshot(target, targetDistance, targetScore, lineOfSight, cooldown, nearestCrystal,
			candidate.pos, candidate.targetDamage, candidate.selfDamage, crystalRisks, candidates);
		return cachedSnapshot;
	}

	private static List<CrystalCandidate> cachedCrystalCandidates(Minecraft client, Player target) {
		Player focus = target == null ? client.player : target;
		BlockPos origin = focus.blockPosition();
		boolean stale = client.level.getGameTime() - candidateGameTime >= 4
			|| !focus.getUUID().equals(candidateTarget)
			|| candidateOrigin == null
			|| candidateOrigin.distManhattan(origin) > 1;
		if (stale) {
			cachedCandidates = crystalCandidates(client, target);
			candidateGameTime = client.level.getGameTime();
			candidateTarget = focus.getUUID();
			candidateOrigin = origin.immutable();
		}
		return cachedCandidates;
	}

	private static List<CrystalCandidate> crystalCandidates(Minecraft client, Player target) {
		int radius = switch (Modules.COMBAT_ADVISOR.settingValue()) {
			case "3m" -> 3;
			case "7m" -> 7;
			default -> 5;
		};

		Player focus = target == null ? client.player : target;
		BlockPos origin = focus.blockPosition();
		List<CrystalCandidate> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -4, -radius), origin.offset(radius, 2, radius))) {
			if (!isCrystalBase(client, pos)) continue;
			Vec3 explosion = Vec3.atBottomCenterOf(pos).add(0, 1, 0);
			double selfDamage = predictedCrystalDamage(client.player, explosion);
			if (target == null) {
				double score = 24 - selfDamage;
				candidates.add(new CrystalCandidate(pos.immutable(), 0, selfDamage, score, true));
				continue;
			}

			double targetDamage = predictedCrystalDamage(target, explosion);
			double score = targetDamage - selfDamage * 0.72;
			if (targetDamage >= 2) candidates.add(new CrystalCandidate(pos.immutable(), targetDamage, selfDamage, score, false));
		}
		return candidates.stream()
			.sorted(Comparator.comparingDouble(CrystalCandidate::score).reversed())
			.limit(24)
			.toList();
	}

	public static boolean isCrystalBase(Minecraft client, BlockPos pos) {
		var block = client.level.getBlockState(pos).getBlock();
		return (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK)
			&& client.level.getBlockState(pos.above()).isAir()
			&& client.level.getBlockState(pos.above(2)).isAir()
			&& client.level.getEntities((Entity) null, new AABB(
				pos.getX(), pos.getY() + 1, pos.getZ(),
				pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1
			), entity -> entity.isAlive()).isEmpty();
	}

	public static double predictedCrystalDamage(Player player, Vec3 explosion) {
		return crystalPrediction(player, explosion).damage;
	}

	private static CrystalPrediction crystalPrediction(Player player, Vec3 explosion) {
		double distance = player.position().distanceTo(explosion) / 12.0;
		if (distance > 1) return new CrystalPrediction(0, 0);
		double exposure = Explosion.getSeenPercent(explosion, player);
		return new CrystalPrediction(reducedDamage(player, distance, exposure), exposure);
	}

	public static double reducedDamage(Player player, double distance, double exposure) {
		double impact = (1 - distance) * exposure;
		double raw = (impact * impact + impact) * 42 + 1;
		raw = applyDifficulty(player, raw);

		double armor = player.getArmorValue();
		double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		double effectiveArmor = Math.min(20, Math.max(armor / 5.0, armor - raw / (2 + toughness / 4.0)));
		double damage = raw * (1 - effectiveArmor / 25.0);

		int protection = protectionPoints(player);
		damage *= 1 - Math.min(20, protection) / 25.0;

		var resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		if (resistance != null) damage *= Math.max(0, 1 - 0.2 * (resistance.getAmplifier() + 1));
		return Math.max(0, damage);
	}

	private static double applyDifficulty(Player player, double damage) {
		Difficulty difficulty = player.level().getDifficulty();
		return switch (difficulty) {
			case PEACEFUL -> 0;
			case EASY -> Math.min(damage / 2 + 1, damage);
			case HARD -> damage * 1.5;
			default -> damage;
		};
	}

	private static int protectionPoints(Player player) {
		var registry = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		var protection = registry.getHolderOrThrow(Enchantments.PROTECTION);
		var blastProtection = registry.getHolderOrThrow(Enchantments.BLAST_PROTECTION);
		int points = 0;
		for (var stack : player.getArmorSlots()) {
			points += EnchantmentHelper.getItemEnchantmentLevel(protection, stack);
			points += EnchantmentHelper.getItemEnchantmentLevel(blastProtection, stack) * 2;
		}
		return Math.min(20, points);
	}

	private static double targetPriority(Player self, Player target) {
		double distance = self.distanceTo(target);
		double visible = self.hasLineOfSight(target) ? 18 : 0;
		double vulnerable = Math.max(0, 30 - target.getHealth() - target.getAbsorptionAmount());
		double proximity = Math.max(0, 36 - distance * 2);
		double armorPenalty = target.getArmorValue() * 0.5;
		return visible + vulnerable + proximity - armorPenalty;
	}

	private static double threatRange() {
		return switch (Modules.THREAT_INTEL.settingValue()) {
			case "12m" -> 12;
			case "48m" -> 48;
			default -> 24;
		};
	}

	private static double crystalRange() {
		return switch (Modules.CRYSTAL_RISK.settingValue()) {
			case "8m" -> 8;
			case "16m" -> 16;
			default -> 12;
		};
	}

	private static int threatScore(Player self, Player target, double distance) {
		double proximity = Math.max(0, 1 - distance / 16.0) * 48;
		double health = target.getHealth() / Math.max(1, target.getMaxHealth()) * 22;
		double armor = target.getArmorValue() / 20.0 * 20;
		double lineOfSight = self.hasLineOfSight(target) ? 10 : 0;
		return (int) Math.min(100, proximity + health + armor + lineOfSight);
	}

	public record Snapshot(
		Player target,
		double targetDistance,
		int threatScore,
		boolean lineOfSight,
		float attackCooldown,
		double nearestCrystal,
		BlockPos bestCrystalPos,
		double targetCrystalDamage,
		double selfCrystalDamage,
		List<CrystalRisk> crystalRisks,
		List<CrystalCandidate> crystalCandidates
	) {
		public static final Snapshot EMPTY = new Snapshot(null, 0, 0, false, 0, Double.POSITIVE_INFINITY, null, 0, 0, List.of(), List.of());

		public double highestCrystalDamage() {
			return crystalRisks.stream()
				.mapToDouble(CrystalRisk::predictedDamage)
				.max()
				.orElse(0);
		}

		public double healthAfterWorstCrystal(Player player) {
			return player.getHealth() + player.getAbsorptionAmount() - highestCrystalDamage();
		}

		public String crystalLabel(Player player) {
			if (crystalRisks.isEmpty()) return "CLEAR";
			double effectiveHealth = player.getHealth() + player.getAbsorptionAmount();
			double remaining = healthAfterWorstCrystal(player);
			if (remaining <= 0) return "LETHAL";
			if (remaining <= Math.max(4, effectiveHealth * 0.25)) return "CRITICAL";
			if (remaining <= effectiveHealth * 0.60) return "CAUTION";
			return "NEARBY";
		}

		public boolean hasLethalCrystal(Player player) {
			return healthAfterWorstCrystal(player) <= 0;
		}

		public String suggestion() {
			if (target == null) return "NO TARGET IN ANALYSIS RANGE";
			if (!lineOfSight) return "REPOSITION FOR LINE OF SIGHT";
			if (targetDistance > 3.1) return "CLOSE DISTANCE MANUALLY";
			if (attackCooldown < 0.9f) return "WAIT FOR ATTACK COOLDOWN";
			return "MANUAL ATTACK WINDOW READY";
		}

		public String combatDetail() {
			if (target == null) return "Hold Z near another player";
			return String.format("%s / %.1fm / LOS %s / cooldown %.0f%%",
				target.getName().getString(), targetDistance, lineOfSight ? "clear" : "blocked", attackCooldown * 100);
		}

		public String crystalDetail() {
			if (bestCrystalPos == null) return "No safe crystal candidate found";
			if (target == null) return String.format("Placement preview / self %.1f / safest legal base", selfCrystalDamage);
			double health = target.getHealth() + target.getAbsorptionAmount();
			return String.format("Crystal preview / target %.1f / self %.1f / kill %s",
				targetCrystalDamage, selfCrystalDamage, targetCrystalDamage >= health ? "likely" : "unlikely");
		}
	}

	private record CrystalPrediction(double damage, double exposure) {}

	public record CrystalRisk(EndCrystal crystal, double predictedDamage, double exposure) {}

	public record CrystalCandidate(BlockPos pos, double targetDamage, double selfDamage, double score, boolean preview) {
		private static final CrystalCandidate NONE = new CrystalCandidate(null, 0, 0, Double.NEGATIVE_INFINITY, false);

		public Heat heat() {
			if (preview) {
				if (selfDamage < 8) return Heat.STRONG;
				if (selfDamage < 14) return Heat.NEUTRAL;
				return Heat.DANGER;
			}
			if (selfDamage >= targetDamage || selfDamage >= 18) return Heat.DANGER;
			if (score >= 8) return Heat.STRONG;
			return Heat.NEUTRAL;
		}
	}

	public enum Heat { STRONG, NEUTRAL, DANGER }
}
