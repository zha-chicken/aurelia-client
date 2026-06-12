package dev.aurelia.client.testing;

import com.mojang.authlib.GameProfile;
import dev.aurelia.client.module.Modules;
import dev.aurelia.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class TestDummyManager {
	private static final UUID DUMMY_UUID = UUID.nameUUIDFromBytes("aurelia:test-dummy".getBytes(StandardCharsets.UTF_8));
	private static RemotePlayer dummy;
	private static ClientLevel dummyLevel;

	private TestDummyManager() {}

	public static void tick(Minecraft client) {
		if (!Modules.TEST_DUMMY.enabled() || !client.hasSingleplayerServer() || client.player == null || client.level == null) {
			remove();
			return;
		}

		if (dummy == null || dummyLevel != client.level || dummy.isRemoved()) spawn(client);
	}

	public static void toggle(Minecraft client) {
		Modules.TEST_DUMMY.toggle();
		Modules.save();
		if (!Modules.TEST_DUMMY.enabled()) remove();
		else tick(client);
	}

	public static void refreshArmor() {
		if (dummy != null && !dummy.isRemoved()) equip(dummy);
	}

	private static void spawn(Minecraft client) {
		remove();
		GameProfile profile = new GameProfile(DUMMY_UUID, "Aurelia Dummy");
		dummy = new RemotePlayer(client.level, profile);
		dummyLevel = client.level;

		Vec3 look = client.player.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0, look.z);
		if (horizontal.lengthSqr() < 0.001) horizontal = new Vec3(0, 0, 1);
		horizontal = horizontal.normalize().scale(4);
		Vec3 position = client.player.position().add(horizontal);

		dummy.setPos(position.x, position.y, position.z);
		dummy.setYRot(client.player.getYRot() + 180);
		dummy.setXRot(0);
		dummy.setHealth(20);
		dummy.setNoGravity(true);
		dummy.setInvulnerable(true);
		dummy.setSilent(true);
		dummy.setCustomName(UiTheme.text("Aurelia Dummy"));
		dummy.setCustomNameVisible(true);
		equip(dummy);

		// --- Reliable CA test setup (updated for "real" obsidian that CA can actually use) ---
		// Place dummy on a single stand column (obsidian + 2 air clearance).
		// Then create a dedicated FULL 3x3 obsidian platform 3 blocks EAST of the stand.
		// - Every one of the 9 bases has its full 1x2 crystal AABB (y+1 to y+3) 100% clear of the dummy's hitbox
		//   (platform left edge at stand.x+2, dummy box only reaches ~stand.x +/- 0.3).
		// - Platform close enough to dummy (~3 blocks) for very high target damage from any of the 9 spots.
		// - When user stands near dummy (as instructed for testing), self dmg will be comparable; CA's
		//   dummy-lenient scoring + "No Limit" / high Max Self Dmg settings will allow placement.
		// - All blocks set on BOTH client.level and serverLevel (for SP) so they are "real" not fake.
		// - This replaces previous cross/north(2) setups that could still clip on closest surround bases.
		ServerLevel serverLevel = null;
		if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
			serverLevel = client.getSingleplayerServer().getLevel(client.level.dimension());
		}

		BlockPos standBase = new BlockPos((int) position.x, (int) position.y - 1, (int) position.z);

		// Dummy stand column (obsidian floor under its feet)
		client.level.setBlock(standBase, Blocks.OBSIDIAN.defaultBlockState(), 3);
		if (serverLevel != null) serverLevel.setBlock(standBase, Blocks.OBSIDIAN.defaultBlockState(), 3);
		client.level.setBlock(standBase.above(1), Blocks.AIR.defaultBlockState(), 3);
		if (serverLevel != null) serverLevel.setBlock(standBase.above(1), Blocks.AIR.defaultBlockState(), 3);
		client.level.setBlock(standBase.above(2), Blocks.AIR.defaultBlockState(), 3);
		if (serverLevel != null) serverLevel.setBlock(standBase.above(2), Blocks.AIR.defaultBlockState(), 3);

		// 3x3 crystal placement platform, safely offset EAST (perpendicular) so zero AABB intersection ever.
		BlockPos platformCenter = standBase.east(3);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos b = platformCenter.offset(dx, 0, dz);
				client.level.setBlock(b, Blocks.OBSIDIAN.defaultBlockState(), 3);
				if (serverLevel != null) serverLevel.setBlock(b, Blocks.OBSIDIAN.defaultBlockState(), 3);
				client.level.setBlock(b.above(1), Blocks.AIR.defaultBlockState(), 3);
				if (serverLevel != null) serverLevel.setBlock(b.above(1), Blocks.AIR.defaultBlockState(), 3);
				client.level.setBlock(b.above(2), Blocks.AIR.defaultBlockState(), 3);
				if (serverLevel != null) serverLevel.setBlock(b.above(2), Blocks.AIR.defaultBlockState(), 3);
			}
		}

		// Final precise stand position for dummy (center of its obsidian)
		Vec3 standPos = new Vec3(standBase.getX() + 0.5, standBase.getY() + 1.0, standBase.getZ() + 0.5);
		dummy.setPos(standPos.x, standPos.y, standPos.z);
		// Face roughly toward the platform (east) for visual clarity when testing
		dummy.setYRot(90.0f);
		dummy.setXRot(0.0f);

		client.level.addEntity(dummy);
	}

	private static void equip(RemotePlayer player) {
		clearArmor(player);
		switch (Modules.TEST_DUMMY.settingValue()) {
			case "Diamond" -> {
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
				player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
				player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
			}
			case "Netherite" -> {
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
				player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
				player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
			}
		}
	}

	private static void clearArmor(RemotePlayer player) {
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
	}

	private static void remove() {
		if (dummy != null && dummyLevel != null && !dummy.isRemoved()) {
			dummyLevel.removeEntity(dummy.getId(), Entity.RemovalReason.DISCARDED);
		}
		dummy = null;
		dummyLevel = null;
	}
}
