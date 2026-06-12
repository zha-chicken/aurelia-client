package dev.aurelia.client.combat;

import dev.aurelia.client.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Auto Dig (inspired by TrollHack PacketDig / Packet Mine style).
 *
 * Sends raw digging packets (START/STOP_DESTROY_BLOCK) for obsidian blocks
 * surrounding a target's feet. This mines without setting the client's
 * main breaking state, so your current actions (placing blocks, moving, etc.)
 * are not interrupted.
 */
public final class AutoDig {
	private static final BlockPos[] HORIZONTAL_OFFSETS = {
		new BlockPos( 1, 0,  0), new BlockPos(-1, 0,  0),
		new BlockPos( 0, 0,  1), new BlockPos( 0, 0, -1),
		new BlockPos( 1, 0,  1), new BlockPos(-1, 0,  1),
		new BlockPos( 1, 0, -1), new BlockPos(-1, 0, -1)
	};

	// Track active packet mines: pos -> start time (ms)
	private static final Map<BlockPos, Long> activeMines = new HashMap<>();

	private AutoDig() {}

	public static void tick(Minecraft client) {
		if (!Modules.AUTO_DIG.enabled() || client.player == null || client.level == null || client.gameMode == null) {
			activeMines.clear();
			return;
		}

		LocalPlayer self = client.player;
		ClientLevel level = client.level;

		float range = getFloat("Range", 5.0f);
		Player target = findBestTarget(self, range + 2);
		if (target == null) {
			// Clean up old mines if no target
			cleanupMines(self, level);
			return;
		}

		BlockPos center = target.blockPosition(); // block containing the player's feet

		// Find obsidian "surrounding the feet": check the 8 horizontal directions
		// at the feet level (y) and one block below (y-1). This covers typical
		// obsidian "city" / surround placements around an opponent's feet.
		List<BlockPos> candidates = new ArrayList<>();
		for (int dy = -1; dy <= 0; dy++) {
			for (BlockPos off : HORIZONTAL_OFFSETS) {
				BlockPos p = center.offset(off.getX(), dy, off.getZ());
				BlockState state = level.getBlockState(p);
				if (state.getBlock() == Blocks.OBSIDIAN) {
					double distSqr = self.distanceToSqr(Vec3.atCenterOf(p));
					if (distSqr <= range * range) {
						candidates.add(p);
					}
				}
			}
		}

		var conn = client.getConnection();
		if (conn == null) return;

		long now = System.currentTimeMillis();

		// Start new mines for candidates not already active
		for (BlockPos pos : candidates) {
			if (!activeMines.containsKey(pos)) {
				Direction face = getMiningFace(pos, self.getEyePosition(1.0f));
				conn.send(new ServerboundPlayerActionPacket(
					ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
					pos,
					face
				));
				activeMines.put(pos, now);
			}
		}

		// Check active mines: use real per-tick destroy progress (based on currently held tool)
		// + elapsed time since we started the packet mine on that block.
		// When accumulated damage >= 1.0 we send STOP_DESTROY_BLOCK.
		// This is much more accurate than a fixed timeout and should make the surround
		// actually break when you are holding a proper pickaxe.
		Iterator<Map.Entry<BlockPos, Long>> it = activeMines.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Long> entry = it.next();
			BlockPos pos = entry.getKey();
			long start = entry.getValue();

			BlockState state = level.getBlockState(pos);
			boolean gone = state.isAir() || state.getBlock() != Blocks.OBSIDIAN;

			float perTick = state.getDestroyProgress(self, level, pos);
			float elapsedTicks = (now - start) / 50.0f;   // ~20 ticks per second
			float damage = perTick * elapsedTicks;

			if (gone || damage >= 1.0f) {
				Direction face = getMiningFace(pos, self.getEyePosition(1.0f));
				conn.send(new ServerboundPlayerActionPacket(
					ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
					pos,
					face
				));
				it.remove();
			} else {
				// Keep the server "interested" in us mining this block by re-sending START
				// every so often. This is common in packet-mine implementations.
				if ((now - start) % 350 < 60) {
					Direction face = getMiningFace(pos, self.getEyePosition(1.0f));
					conn.send(new ServerboundPlayerActionPacket(
						ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
						pos,
						face
					));
				}
			}
		}
	}

	private static float getFloat(String name, float def) {
		try {
			return Float.parseFloat(Modules.AUTO_DIG.getSettingValueByName(name));
		} catch (Exception e) {
			return def;
		}
	}

	private static Player findBestTarget(LocalPlayer self, double maxDist) {
		return self.level().getEntitiesOfClass(
			Player.class,
			self.getBoundingBox().inflate(maxDist),
			e -> e != self && e.isAlive() && !e.isSpectator() && (!e.isCreative() || e.getName().getString().contains("Dummy"))
		).stream()
			.min(Comparator.comparingDouble(self::distanceToSqr))
			.orElse(null);
	}

	private static Direction getMiningFace(BlockPos pos, Vec3 eye) {
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 diff = eye.subtract(center);
		double ax = Math.abs(diff.x);
		double ay = Math.abs(diff.y);
		double az = Math.abs(diff.z);

		if (ax > ay && ax > az) {
			return diff.x > 0 ? Direction.WEST : Direction.EAST;
		} else if (az > ay) {
			return diff.z > 0 ? Direction.NORTH : Direction.SOUTH;
		} else {
			return diff.y > 0 ? Direction.DOWN : Direction.UP;
		}
	}

	private static void cleanupMines(LocalPlayer self, ClientLevel level) {
		if (activeMines.isEmpty()) return;

		var conn = self.connection;
		if (conn == null) {
			activeMines.clear();
			return;
		}

		Iterator<Map.Entry<BlockPos, Long>> it = activeMines.entrySet().iterator();
		while (it.hasNext()) {
			BlockPos pos = it.next().getKey();
			if (level.getBlockState(pos).isAir()) {
				// already gone, send stop to be safe
				Direction face = getMiningFace(pos, self.getEyePosition(1.0f));
				conn.send(new ServerboundPlayerActionPacket(
					ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
					pos,
					face
				));
				it.remove();
			}
		}
	}
}
