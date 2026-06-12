package dev.aurelia.client.mixin;

import dev.aurelia.client.module.Modules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	private static Vec3 aurelia$recentCrystal;
	private static long aurelia$recentCrystalAt;

	@Inject(method = "removeEntity", at = @At("HEAD"))
	private void aurelia$rememberRemovedCrystal(int id, Entity.RemovalReason reason, CallbackInfo info) {
		Entity entity = ((ClientLevel) (Object) this).getEntity(id);
		if (entity instanceof EndCrystal) {
			aurelia$recentCrystal = entity.position();
			aurelia$recentCrystalAt = System.currentTimeMillis();
		}
	}

	@Inject(
		method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void aurelia$reduceCrystalExplosion(
		ParticleOptions particle,
		double x, double y, double z,
		double velocityX, double velocityY, double velocityZ,
		CallbackInfo info
	) {
		if (!Modules.REDUCED_CRYSTAL_FX.enabled() || particle.getType() != ParticleTypes.EXPLOSION_EMITTER) return;
		if (aurelia$recentCrystal != null
			&& System.currentTimeMillis() - aurelia$recentCrystalAt < 750
			&& aurelia$recentCrystal.distanceToSqr(x, y, z) < 9) {
			info.cancel();
		}
	}
}
