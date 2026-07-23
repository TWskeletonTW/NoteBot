package net.skeleton.notebot.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.skeleton.notebot.client.gui.NotebotScreen;
import net.skeleton.notebot.client.song.SongLibrary;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotebotClient implements ClientModInitializer {
	public static final String MOD_ID = "notebot";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static NotebotController controller;

	@Override
	public void onInitializeClient() {
		NotebotConfig config = NotebotConfig.load();
		SongLibrary library = new SongLibrary();
		controller = new NotebotController(config, library);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
		KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.notebot.open", GLFW.GLFW_KEY_N, category));
		KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.notebot.toggle", GLFW.GLFW_KEY_P, category));
		KeyMapping record = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.notebot.record", GLFW.GLFW_KEY_O, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) client.gui.setScreen(new NotebotScreen(controller));
			while (toggle.consumeClick()) controller.toggle();
			while (record.consumeClick()) controller.toggleRecording();
			controller.tick();
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
				ClientCommands.literal("notebot")
						.executes(command -> {
							command.getSource().getClient().execute(() ->
									command.getSource().getClient().gui.setScreen(new NotebotScreen(controller)));
							return 1;
						})
						.then(ClientCommands.literal("toggle").executes(command -> {
							controller.toggle();
							command.getSource().sendFeedback(controller.status());
							return 1;
						}))
						.then(ClientCommands.literal("record").executes(command -> {
							controller.toggleRecording();
							command.getSource().sendFeedback(controller.status());
							return 1;
						}))
						.then(ClientCommands.literal("folder").executes(command -> {
							controller.library().openDirectory();
							return 1;
						}))
		));

		LOGGER.info("NoteBot v1.0 initialized for Minecraft 26.2");
	}

	public static NotebotController controller() {
		return controller;
	}
}
