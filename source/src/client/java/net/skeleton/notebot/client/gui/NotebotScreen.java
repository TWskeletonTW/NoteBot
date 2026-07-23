/*
 * Reimplements BleachHack's Notebot song GUI for Minecraft 26.2.
 * Licensed under the GNU GPL v3 or later.
 */
package net.skeleton.notebot.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.skeleton.notebot.client.InstrumentSupport;
import net.skeleton.notebot.client.NotebotConfig;
import net.skeleton.notebot.client.NotebotController;
import net.skeleton.notebot.client.NotebotText;
import net.skeleton.notebot.client.song.Note;
import net.skeleton.notebot.client.song.Song;
import net.skeleton.notebot.client.song.SongParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class NotebotScreen extends Screen {
	private static Path rememberedSelectedPath;
	private static Song rememberedSelectedSong;
	private static int rememberedPage;
	private static double rememberedDetailScroll;

	private final NotebotController controller;
	private List<Path> files = List.of();
	private int page = rememberedPage;
	private int pageSize;
	private Path selectedPath = rememberedSelectedPath;
	private Song selectedSong = rememberedSelectedSong;
	private Path pendingPath;
	private boolean previewing;
	private int previewTick;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int detailX;
	private int detailWidth;
	private int detailTop;
	private int detailBottom;
	private int detailContentHeight;
	private double detailScroll = rememberedDetailScroll;

	public NotebotScreen(NotebotController controller) {
		super(NotebotText.tr("screen.title"));
		this.controller = controller;
	}

	@Override
	protected void init() {
		super.init();
		files = controller.library().listSongs();
		if (selectedPath != null && !files.contains(selectedPath)) {
			clearRememberedSelection();
		}
		panelWidth = Math.min(650, width - 24);
		panelHeight = Math.min(390, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		pageSize = Math.max(4, (panelHeight - 108) / 22);
		int maxPage = Math.max(0, (files.size() - 1) / pageSize);
		page = Math.clamp(page, 0, maxPage);

		int listX = panelX + 12;
		int listWidth = Math.min(210, panelWidth / 3);
		addRenderableWidget(Button.builder(Component.literal("<"), button -> {
			page = Math.max(0, page - 1);
			rememberedPage = page;
			rebuildWidgets();
		}).bounds(listX, panelY + 30, 28, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), button -> {
			page = Math.min(maxPage, page + 1);
			rememberedPage = page;
			rebuildWidgets();
		}).bounds(listX + listWidth - 28, panelY + 30, 28, 20).build());

		int first = page * pageSize;
		for (int row = 0; row < pageSize && first + row < files.size(); row++) {
			Path path = files.get(first + row);
			String filename = controller.library().directory().relativize(path).toString();
			addRenderableWidget(Button.builder(Component.literal(filename), button -> select(path))
					.bounds(listX, panelY + 56 + row * 22, listWidth, 20).build());
		}

		int bottom = panelY + panelHeight - 28;
		addRenderableWidget(Button.builder(tr("button.open_folder"), button -> controller.library().openDirectory())
				.bounds(listX, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(tr("button.download"), button -> {
			controller.library().downloadLegacySongs(controller::setStatus);
		}).bounds(listX + 104, bottom, listWidth - 104, 20).build());

		int rightX = listX + listWidth + 14;
		int rightWidth = panelX + panelWidth - 12 - rightX;
		int half = Math.max(70, (rightWidth - 8) / 2);
		int actionY = panelY + panelHeight - 52;
		detailX = rightX;
		detailWidth = rightWidth;
		detailTop = panelY + 122;
		detailBottom = Math.max(detailTop, actionY - 8);
		addRenderableWidget(Button.builder(tr(controller.active() ? "button.stop" : "button.play"), button -> {
			controller.toggle();
			rebuildWidgets();
		}).bounds(rightX, actionY, half, 20).build());
		addRenderableWidget(Button.builder(tr(controller.recording() ? "button.stop_recording" : "button.record"), button -> {
			controller.toggleRecording();
			rebuildWidgets();
		}).bounds(rightX + half + 8, actionY, rightWidth - half - 8, 20).build());

		addRenderableWidget(Button.builder(tr("button.select"), button -> {
			if (selectedSong != null) controller.selectSong(selectedSong);
		}).bounds(rightX, bottom, 70, 20).build());
		addRenderableWidget(Button.builder(tr(previewing ? "button.stop_preview" : "button.preview"), button -> togglePreview())
				.bounds(rightX + 74, bottom, 92, 20).build());
		addRenderableWidget(Button.builder(tr("button.delete"), button -> deleteSelected())
				.bounds(rightX + 170, bottom, 70, 20).build());
		addRenderableWidget(Button.builder(tr("button.refresh"), button -> returnToSongList())
				.bounds(rightX + 244, bottom, Math.max(60, rightWidth - 244), 20).build());

		NotebotConfig config = controller.config();
		int settingsY = panelY + 30;
		addRenderableWidget(Button.builder(tr("setting.tune", onOff(config.tune())), button -> {
			config.toggleTune(); rebuildWidgets();
		}).bounds(rightX, settingsY, 105, 20).build());
		addRenderableWidget(Button.builder(tr("setting.mode", NotebotText.tr(config.tuneMode().translationKey())), button -> {
			config.cycleTuneMode(); rebuildWidgets();
		}).bounds(rightX + 109, settingsY, 120, 20).build());
		addRenderableWidget(Button.builder(tr("setting.loop", onOff(config.loop())), button -> {
			config.toggleLoop(); rebuildWidgets();
		}).bounds(rightX + 233, settingsY, Math.max(80, rightWidth - 233), 20).build());
		addRenderableWidget(Button.builder(tr("setting.ignore_instruments", onOff(config.ignoreInstruments())), button -> {
			config.toggleIgnoreInstruments(); rebuildWidgets();
		}).bounds(rightX, settingsY + 24, 170, 20).build());
		addRenderableWidget(Button.builder(tr("setting.auto_play", onOff(config.autoPlay())), button -> {
			config.toggleAutoPlay(); rebuildWidgets();
		}).bounds(rightX + 174, settingsY + 24, Math.max(100, rightWidth - 174), 20).build());
	}

	private void select(Path path) {
		selectedPath = path;
		pendingPath = path;
		selectedSong = null;
		previewing = false;
		previewTick = 0;
		detailScroll = 0.0;
		rememberedSelectedPath = selectedPath;
		rememberedSelectedSong = null;
		rememberedDetailScroll = detailScroll;
		controller.setStatus(tr("status.loading", path.getFileName().toString()));

		CompletableFuture.supplyAsync(() -> {
			try {
				return SongParser.parse(path);
			} catch (IOException exception) {
				throw new CompletionException(exception);
			}
		}).whenCompleteAsync((song, throwable) -> {
			if (!path.equals(pendingPath)) return;
			if (throwable == null) {
				selectedSong = song;
				rememberedSelectedSong = song;
				controller.setStatus(tr("status.loaded", song.name()));
			} else {
				Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
				selectedSong = null;
				rememberedSelectedSong = null;
				controller.setStatus(tr("status.parse_failed", path.getFileName().toString(), cause.getMessage()));
			}
		}, Minecraft.getInstance()::execute);
	}

	private void togglePreview() {
		if (selectedSong == null) return;
		previewing = !previewing;
		previewTick = 0;
		rebuildWidgets();
	}

	private void deleteSelected() {
		if (selectedPath == null) return;
		try {
			Path root = controller.library().directory().toAbsolutePath().normalize();
			Path target = selectedPath.toAbsolutePath().normalize();
			if (target.startsWith(root) && !target.equals(root)) Files.deleteIfExists(target);
			selectedPath = null;
			selectedSong = null;
			previewing = false;
			detailScroll = 0.0;
			clearRememberedSelection();
			controller.setStatus(tr("status.deleted"));
			rebuildWidgets();
		} catch (IOException exception) {
			controller.setStatus(tr("status.delete_failed", exception.getMessage()));
		}
	}

	@Override
	public void tick() {
		if (!previewing || selectedSong == null) return;
		InstrumentSupport.preview(selectedSong.notesAt(previewTick));
		previewTick++;
		if (previewTick > selectedSong.length()) {
			previewing = false;
			previewTick = 0;
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xe0151820);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xff6d7f99);
		graphics.centeredText(font, title, width / 2, panelY + 9, 0xffffffff);

		int listX = panelX + 12;
		int listWidth = Math.min(210, panelWidth / 3);
		int maxPage = Math.max(0, (files.size() - 1) / pageSize);
		graphics.centeredText(font, tr("page", page + 1, maxPage + 1), listX + listWidth / 2, panelY + 36, 0xffc8d6e8);

		int rightX = listX + listWidth + 14;
		int rightWidth = panelX + panelWidth - 12 - rightX;
		graphics.text(font, controller.status(), rightX, panelY + 82, 0xffe7d98f);
		Song active = controller.song();
		if (active != null) {
			graphics.text(font, tr("selected_for_play", active.name()), rightX, panelY + 94, 0xff8fe7a0);
		}
		if (controller.active()) {
			graphics.text(font, tr("playback_info", controller.songTick(), controller.mappedBlocks()), rightX, panelY + 106, 0xff8fc8e7);
		}

		if (selectedSong != null) {
			Map<Integer, Long> instruments = new LinkedHashMap<>();
			selectedSong.requirements().stream().map(Note::instrument).distinct().forEach(instrument ->
					instruments.put(instrument, selectedSong.requirements().stream().filter(note -> note.instrument() == instrument).count()));
			detailContentHeight = 66 + instruments.size() * 19;
			detailScroll = Math.clamp(detailScroll, 0.0, maxDetailScroll());
			rememberedDetailScroll = detailScroll;

			graphics.enableScissor(detailX, detailTop, detailX + detailWidth, detailBottom);
			int y = detailTop + 4 - (int)Math.round(detailScroll);
			graphics.text(font, tr("song.name", selectedSong.name()), rightX, y, 0xffffffff);
			Component author = selectedSong.author().equals("Unknown") ? tr("value.unknown") : Component.literal(selectedSong.author());
			graphics.text(font, tr("song.author", author), rightX, y + 12, 0xffc5c5c5);
			graphics.text(font, tr("song.format_notes", selectedSong.format(), selectedSong.noteCount()), rightX, y + 24, 0xffc5c5c5);
			graphics.text(font, tr("song.length", String.format(Locale.ROOT, "%.1f", selectedSong.length() / 20.0)), rightX, y + 36, 0xffc5c5c5);
			graphics.text(font, tr("song.required_blocks", controller.requiredBlocks(selectedSong)), rightX, y + 48, 0xffffd783);

			int row = 0;
			for (Map.Entry<Integer, Long> entry : instruments.entrySet()) {
				int itemY = y + 66 + row * 19;
				graphics.item(InstrumentSupport.icon(entry.getKey()), rightX, itemY);
				graphics.text(font, tr("song.instrument_count", NotebotText.tr("instrument." + InstrumentSupport.name(entry.getKey())), entry.getValue()), rightX + 20, itemY + 4, 0xff9fe6a8);
				row++;
			}
			graphics.disableScissor();

			drawDetailScrollbar(graphics);

			if (previewing && selectedSong.length() > 0) {
				int barY = detailBottom + 2;
				int progress = (int)(rightWidth * Math.clamp(previewTick / (double)selectedSong.length(), 0.0, 1.0));
				graphics.fill(rightX, barY, rightX + rightWidth, barY + 4, 0xff30343b);
				graphics.fill(rightX, barY, rightX + progress, barY + 4, 0xff5d8fea);
			}
		} else {
			detailContentHeight = 0;
			detailScroll = 0.0;
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (selectedSong != null
				&& mouseX >= detailX && mouseX < detailX + detailWidth
				&& mouseY >= detailTop && mouseY < detailBottom
				&& maxDetailScroll() > 0.0) {
			detailScroll = Math.clamp(detailScroll - verticalAmount * 18.0, 0.0, maxDetailScroll());
			rememberedDetailScroll = detailScroll;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private double maxDetailScroll() {
		int viewportHeight = Math.max(1, detailBottom - detailTop - 4);
		return Math.max(0, detailContentHeight - viewportHeight);
	}

	private void drawDetailScrollbar(GuiGraphicsExtractor graphics) {
		double maxScroll = maxDetailScroll();
		if (maxScroll <= 0.0 || detailBottom <= detailTop) return;

		int trackX = detailX + detailWidth - 3;
		int trackTop = detailTop + 2;
		int trackHeight = Math.max(1, detailBottom - detailTop - 4);
		int thumbHeight = Math.max(12, (int)Math.round(trackHeight * (trackHeight / (double)detailContentHeight)));
		thumbHeight = Math.min(trackHeight, thumbHeight);
		int travel = trackHeight - thumbHeight;
		int thumbY = trackTop + (int)Math.round(travel * (detailScroll / maxScroll));
		graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0xff30343b);
		graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xff8aa5c7);
	}

	private void returnToSongList() {
		previewing = false;
		previewTick = 0;
		page = 0;
		rememberedPage = 0;
		clearRememberedSelection();
		files = controller.library().listSongs();
		rebuildWidgets();
	}

	private void clearRememberedSelection() {
		selectedPath = null;
		selectedSong = null;
		pendingPath = null;
		detailScroll = 0.0;
		rememberedSelectedPath = null;
		rememberedSelectedSong = null;
		rememberedDetailScroll = 0.0;
	}

	private static Component onOff(boolean value) {
		return tr(value ? "value.on" : "value.off");
	}

	private static Component tr(String key, Object... arguments) {
		return NotebotText.tr(key, arguments);
	}
}
