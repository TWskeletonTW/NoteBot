package net.skeleton.notebot.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class NotebotConfig {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("notebot.properties");

	private boolean tune = true;
	private TuneMode tuneMode = TuneMode.NORMAL;
	private boolean loop;
	private boolean ignoreInstruments;
	private boolean autoPlay;

	public static NotebotConfig load() {
		NotebotConfig config = new NotebotConfig();
		if (!Files.isRegularFile(FILE)) {
			return config;
		}

		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(FILE)) {
			values.load(input);
			config.tune = Boolean.parseBoolean(values.getProperty("tune", "true"));
			config.tuneMode = TuneMode.parse(values.getProperty("tuneMode", "NORMAL"));
			config.loop = Boolean.parseBoolean(values.getProperty("loop", "false"));
			config.ignoreInstruments = Boolean.parseBoolean(values.getProperty("ignoreInstruments", "false"));
			config.autoPlay = Boolean.parseBoolean(values.getProperty("autoPlay", "false"));
		} catch (IOException ignored) {
		}
		return config;
	}

	public synchronized void save() {
		Properties values = new Properties();
		values.setProperty("tune", Boolean.toString(tune));
		values.setProperty("tuneMode", tuneMode.name());
		values.setProperty("loop", Boolean.toString(loop));
		values.setProperty("ignoreInstruments", Boolean.toString(ignoreInstruments));
		values.setProperty("autoPlay", Boolean.toString(autoPlay));
		try {
			Files.createDirectories(FILE.getParent());
			try (OutputStream output = Files.newOutputStream(FILE)) {
				values.store(output, "Notebot Modern settings");
			}
		} catch (IOException ignored) {
		}
	}

	public boolean tune() { return tune; }
	public TuneMode tuneMode() { return tuneMode; }
	public boolean loop() { return loop; }
	public boolean ignoreInstruments() { return ignoreInstruments; }
	public boolean autoPlay() { return autoPlay; }

	public void toggleTune() { tune = !tune; save(); }
	public void cycleTuneMode() { tuneMode = tuneMode.next(); save(); }
	public void toggleLoop() { loop = !loop; save(); }
	public void toggleIgnoreInstruments() { ignoreInstruments = !ignoreInstruments; save(); }
	public void toggleAutoPlay() { autoPlay = !autoPlay; save(); }

	public enum TuneMode {
		NORMAL("normal"),
		WAIT_1("wait_1"),
		WAIT_2("wait_2"),
		BATCH_5("batch_5"),
		ALL("all");

		private final String translationSuffix;

		TuneMode(String translationSuffix) {
			this.translationSuffix = translationSuffix;
		}

		public String translationKey() {
			return "notebot.tune_mode." + translationSuffix;
		}

		public TuneMode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		private static TuneMode parse(String value) {
			try {
				return valueOf(value.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				return NORMAL;
			}
		}
	}
}
