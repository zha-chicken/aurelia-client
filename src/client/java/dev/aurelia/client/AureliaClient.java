package dev.aurelia.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurelia.client.combat.AutoDig;
import dev.aurelia.client.combat.CrystalAura;
import dev.aurelia.client.combat.Killaura;
import dev.aurelia.client.combat.Velocity;
import dev.aurelia.client.module.Modules;
import dev.aurelia.client.testing.TestDummyManager;
import dev.aurelia.client.ui.AureliaScreen;
import dev.aurelia.client.ui.HudRenderer;
import dev.aurelia.client.ui.SdfRoundedRectRenderer;
import dev.aurelia.client.ui.WorldOverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class AureliaClient implements ClientModInitializer {
	public static final String NAME = "Aurelia";
	public static boolean suggestionsHeld;

	@Override
	public void onInitializeClient() {
		SdfRoundedRectRenderer.initialize();
		Modules.load();
		KeyMapping menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.aurelia.menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			"category.aurelia"
		));
		KeyMapping suggestionsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.aurelia.suggestions",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_Z,
			"category.aurelia"
		));
		KeyMapping dummyKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.aurelia.test_dummy",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_H,
			"category.aurelia"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			suggestionsHeld = suggestionsKey.isDown();
			while (menuKey.consumeClick()) client.setScreen(new AureliaScreen());
			while (dummyKey.consumeClick()) TestDummyManager.toggle(client);
			TestDummyManager.tick(client);
			Killaura.tick(client);
			CrystalAura.tick(client);
			Velocity.tick(client);
			AutoDig.tick(client);
		});
		HudRenderCallback.EVENT.register(HudRenderer::render);
		WorldRenderEvents.AFTER_ENTITIES.register(WorldOverlayRenderer::render);
	}
}
