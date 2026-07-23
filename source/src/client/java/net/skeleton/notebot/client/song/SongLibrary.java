package net.skeleton.notebot.client.song;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.skeleton.notebot.client.NotebotText;
import net.skeleton.notebot.client.NotebotClient;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SongLibrary {
	private static final URI LEGACY_LIBRARY = URI.create("https://bleachhack.org/resources/notebot/songs.zip");
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("notebot").resolve("songs");

	public Path directory() {
		return directory;
	}

	public void ensureDirectory() throws IOException {
		Files.createDirectories(directory);
	}

	public List<Path> listSongs() {
		try {
			ensureDirectory();
			try (Stream<Path> files = Files.walk(directory)) {
				return files.filter(Files::isRegularFile)
						.filter(this::isSupported)
						.sorted(Comparator.comparing(path -> directory.relativize(path).toString(), String.CASE_INSENSITIVE_ORDER))
						.toList();
			}
		} catch (IOException exception) {
			NotebotClient.LOGGER.error("Unable to list Notebot songs", exception);
			return List.of();
		}
	}

	public void openDirectory() {
		try {
			ensureDirectory();
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(directory.toFile());
			}
		} catch (Exception exception) {
			NotebotClient.LOGGER.warn("Unable to open song directory", exception);
		}
	}

	public CompletableFuture<Void> downloadLegacySongs(Consumer<Component> status) {
		status.accept(NotebotText.tr("status.downloading"));
		HttpRequest request = HttpRequest.newBuilder(LEGACY_LIBRARY)
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", "Notebot-Modern/2.0")
				.GET()
				.build();

		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenApply(response -> {
					if (response.statusCode() < 200 || response.statusCode() >= 300) {
						throw new IllegalStateException("HTTP " + response.statusCode());
					}
					return response.body();
				})
				.thenAccept(this::extractZipSafely)
				.whenComplete((ignored, throwable) -> Minecraft.getInstance().execute(() -> {
					if (throwable == null) {
						status.accept(NotebotText.tr("status.download_complete"));
					} else {
						status.accept(NotebotText.tr("status.download_failed", rootMessage(throwable)));
						NotebotClient.LOGGER.warn("Unable to download legacy Notebot songs", throwable);
					}
				}));
	}

	private void extractZipSafely(byte[] zipBytes) {
		try {
			ensureDirectory();
			Path root = directory.toAbsolutePath().normalize();
			try (InputStream bytes = new ByteArrayInputStream(zipBytes); ZipInputStream zip = new ZipInputStream(bytes)) {
				ZipEntry entry;
				while ((entry = zip.getNextEntry()) != null) {
					if (entry.isDirectory()) continue;
					Path output = root.resolve(entry.getName()).normalize();
					if (!output.startsWith(root)) {
						throw new IOException("Unsafe ZIP entry: " + entry.getName());
					}
					Files.createDirectories(output.getParent());
					Path partial = output.resolveSibling(output.getFileName() + ".part");
					Files.copy(zip, partial, StandardCopyOption.REPLACE_EXISTING);
					Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private boolean isSupported(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".txt") || name.endsWith(".notelist") || name.endsWith(".nbs")
				|| name.endsWith(".mid") || name.endsWith(".midi");
	}

	private static String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) current = current.getCause();
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}
}
