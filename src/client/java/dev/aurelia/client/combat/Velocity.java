package dev.aurelia.client.combat;

import dev.aurelia.client.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Velocity - full knockback cancel.
 * Cancels incoming motion packets for the local player (mixin) and clears any residual velocity.
 * No bypass tuning needed as per request (blatant full cancel).
 */
public final class Velocity {
	private Velocity() {}

	public static void tick(Minecraft client) {
		if (!Modules.VELOCITY.enabled() || client.player == null) return;

		// The real work is done in VelocityMixin: we cancel ClientboundSetEntityMotionPacket
		// for the local player. This prevents the server from forcing knockback velocity on us.
		//
		// IMPORTANT: We deliberately do NOT zero the player's delta movement here every tick.
		// Doing so fights the normal client movement code (walking, sprinting, jumping input)
		// and makes the player feel slow or stuck, which is what was happening before.
		//
		// Only external knockback (from hits, explosions, etc.) is cancelled via the packet.
		// Normal player-controlled movement is left untouched. This matches the behavior
		// in LiquidBounce, Meteor, TrollHack, etc. for a "full cancel" velocity module.
	}
}
