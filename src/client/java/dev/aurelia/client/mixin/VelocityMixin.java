package dev.aurelia.client.mixin;

import dev.aurelia.client.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Velocity mixin - cancels incoming motion packets for the local player.
 * This provides clean, full knockback cancel without needing to care about anticheat
 * (as requested: blatant cancel-all).
 */
@Mixin(ClientPacketListener.class)
public abstract class VelocityMixin {

	@Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
	private void aurelia$cancelVelocity(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (Modules.VELOCITY.enabled()
				&& mc.player != null
				&& packet.getId() == mc.player.getId()) {
			ci.cancel();
		}
	}
}
