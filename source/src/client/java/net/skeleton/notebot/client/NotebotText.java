package net.skeleton.notebot.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves NoteBot text normally, with a bundled Traditional Chinese fallback
 * for clients that do not merge a Fabric mod's language resources correctly.
 */
public final class NotebotText {
	private static final Map<String, String> ZH_TW = loadTraditionalChinese();

	private NotebotText() {
	}

	public static Component tr(String key, Object... arguments) {
		String fullKey = key.startsWith("notebot.") || key.startsWith("key.")
				? key
				: "notebot." + key;
		if (usesTraditionalChinese()) {
			String template = ZH_TW.get(fullKey);
			if (template != null) {
				return Component.literal(format(template, arguments));
			}
		}
		return Component.translatable(fullKey, arguments);
	}

	private static boolean usesTraditionalChinese() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.options == null || client.options.languageCode == null) {
			return false;
		}
		String language = client.options.languageCode.toLowerCase(Locale.ROOT);
		return language.equals("zh_tw") || language.equals("zh_hk");
	}

	private static String format(String template, Object[] arguments) {
		Object[] plainArguments = new Object[arguments.length];
		for (int index = 0; index < arguments.length; index++) {
			Object argument = arguments[index];
			plainArguments[index] = argument instanceof Component component
					? component.getString()
					: argument;
		}
		try {
			return String.format(Locale.ROOT, template, plainArguments);
		} catch (RuntimeException exception) {
			NotebotClient.LOGGER.warn("Unable to format bundled translation: {}", template, exception);
			return template;
		}
	}

	private static Map<String, String> loadTraditionalChinese() {
		Map<String, String> translations = new LinkedHashMap<>();
		String resource = "assets/notebot/lang/zh_tw.json";
		try (InputStream input = NotebotText.class.getClassLoader().getResourceAsStream(resource)) {
			if (input == null) {
				NotebotClient.LOGGER.warn("Bundled translation resource is missing: {}", resource);
				return Map.of();
			}
			try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
				for (Map.Entry<String, JsonElement> entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
					translations.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		} catch (IOException | RuntimeException exception) {
			NotebotClient.LOGGER.error("Unable to load bundled Traditional Chinese translations", exception);
			return Map.of();
		}
		return Map.copyOf(translations);
	}
}
