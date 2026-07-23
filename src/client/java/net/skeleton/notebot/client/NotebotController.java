/*
 * Derived from BleachHack's Notebot and NotebotStealer under the GNU GPL v3 or later.
 */
package net.skeleton.notebot.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.skeleton.notebot.client.song.Note;
import net.skeleton.notebot.client.song.Song;
import net.skeleton.notebot.client.song.SongLibrary;
import net.skeleton.notebot.client.song.SongParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class NotebotController {
	private final Minecraft client = Minecraft.getInstance();
	private final NotebotConfig config;
	private final SongLibrary library;
	private final Map<BlockPos, Note> assignments = new LinkedHashMap<>();
	private final Map<Integer, List<Note>> recordingNotes = new HashMap<>();
	private final Object recordingLock = new Object();
	private final Random random = new Random();

	private Song song;
	private boolean active;
	private int songTick = -10;
	private int tuneDelay;
	private Component status = NotebotText.tr("status.choose_song");

	private boolean recording;
	private boolean recordingStarted;
	private int recordingTick;

	public NotebotController(NotebotConfig config, SongLibrary library) {
		this.config = config;
		this.library = library;
	}

	public void tick() {
		synchronized (recordingLock) {
			if (recording && recordingStarted) recordingTick++;
		}
		if (!active) return;
		if (!readyInWorld()) {
			stop(NotebotText.tr("status.stopped_no_world"));
			return;
		}
		if (client.player.tickCount % 8 == 0) showMappedBlocks();

		if (config.tune() && tuneOneBlock()) return;

		if (songTick > song.length() + 10) {
			if (config.autoPlay() && loadRandomSong()) {
				start();
				return;
			}
			if (config.loop()) {
				songTick = -10;
			} else {
				stop(NotebotText.tr("status.finished", song.name()));
				return;
			}
		}

		songTick++;
		List<Note> current = song.notesAt(songTick);
		if (current.isEmpty()) return;

		for (Map.Entry<BlockPos, Note> entry : assignments.entrySet()) {
			BlockPos pos = entry.getKey();
			if (!isPlayableNoteBlock(pos)) continue;
			int pitch = getPitch(pos);
			int instrument = getInstrument(pos).ordinal();
			for (Note note : current) {
				if (note.pitch() == pitch && (config.ignoreInstruments() || note.instrument() == instrument)) {
					playBlock(pos);
					break;
				}
			}
		}
	}

	public void toggle() {
		if (active) stop(NotebotText.tr("status.stopped")); else start();
	}

	public boolean start() {
		if (!readyInWorld()) {
			message(NotebotText.tr("message.join_world"));
			return false;
		}
		if (!client.gameMode.getPlayerMode().isSurvival()) {
			message(NotebotText.tr("message.requires_survival"));
			return false;
		}
		if (song == null) {
			message(NotebotText.tr("message.choose_song_first"));
			return false;
		}

		mapNoteBlocks();
		active = true;
		songTick = -10;
		tuneDelay = 0;
		status = NotebotText.tr("status.playing", song.name());
		message(NotebotText.tr("message.playing_mapped", song.name(), assignments.size()));
		return true;
	}

	public void stop(Component reason) {
		active = false;
		assignments.clear();
		status = reason;
		message(reason);
	}

	public void selectSong(Song selected) {
		if (active) stop(NotebotText.tr("status.song_changed"));
		song = selected;
		status = NotebotText.tr("status.selected", selected.name());
		Component author = selected.author().equals("Unknown")
				? NotebotText.tr("value.unknown")
				: Component.literal(selected.author());
		message(NotebotText.tr(
				"message.selected_song",
				selected.name(),
				author,
				selected.format(),
				selected.noteCount(),
				String.format(java.util.Locale.ROOT, "%.1f", selected.length() / 20.0),
				requiredBlocks(selected)
		));
	}

	public void toggleRecording() {
		boolean wasRecording;
		synchronized (recordingLock) {
			wasRecording = recording;
			if (wasRecording) {
				recording = false;
			} else {
				recordingNotes.clear();
				recordingStarted = false;
				recordingTick = 0;
				recording = true;
			}
		}
		if (wasRecording) {
			saveRecording();
		} else {
			status = NotebotText.tr("status.recording");
			message(status);
		}
	}

	public void captureSound(SoundInstance instance) {
		String path = instance.getIdentifier().getPath();
		if (!path.contains("note_block")) return;

		int instrument = instrumentFromSound(path);
		int pitch = Math.clamp((int)Math.round(12.0 * (Math.log(instance.getPitch()) / Math.log(2.0)) + 12.0), 0, 24);
		synchronized (recordingLock) {
			if (!recording) return;
			if (!recordingStarted) {
				recordingStarted = true;
				recordingTick = 0;
			}
			Song.addNote(recordingNotes, recordingTick, new Note(pitch, instrument));
		}
	}

	private void mapNoteBlocks() {
		assignments.clear();
		List<BlockPos> blocks = BlockPos.withinManhattanStream(client.player.blockPosition(), 4, 4, 4)
				.filter(this::isPlayableNoteBlock)
				.map(BlockPos::immutable)
				.toList();

		Set<Integer> usedPitches = new HashSet<>();
		for (Note required : song.requirements()) {
			if (config.ignoreInstruments() && !usedPitches.add(required.pitch())) continue;
			for (BlockPos pos : blocks) {
				if (assignments.containsKey(pos)) continue;
				if (config.ignoreInstruments() || getInstrument(pos).ordinal() == required.instrument()) {
					assignments.put(pos, required);
					break;
				}
			}
		}

		int required = requiredBlocks(song);
		if (assignments.size() < required) {
			message(NotebotText.tr("message.missing_blocks", required - assignments.size()));
		}
	}

	private boolean tuneOneBlock() {
		for (Map.Entry<BlockPos, Note> entry : assignments.entrySet()) {
			int current = getPitch(entry.getKey());
			int wanted = entry.getValue().pitch();
			if (current < 0 || current == wanted) continue;

			NotebotConfig.TuneMode mode = config.tuneMode();
			if (mode == NotebotConfig.TuneMode.WAIT_1 && client.player.tickCount % 2 == 0) return true;
			if (mode == NotebotConfig.TuneMode.WAIT_2 && client.player.tickCount % 3 != 0) return true;

			int clicks = 1;
			if (mode == NotebotConfig.TuneMode.BATCH_5 || mode == NotebotConfig.TuneMode.ALL) {
				int wait = mode == NotebotConfig.TuneMode.BATCH_5 ? 3 : 5;
				if (tuneDelay++ < wait) return true;
				int needed = (wanted - current + 25) % 25;
				clicks = Math.min(mode == NotebotConfig.TuneMode.BATCH_5 ? 5 : 25, needed);
				tuneDelay = 0;
			}

			for (int click = 0; click < clicks; click++) tuneBlock(entry.getKey());
			return true;
		}
		return false;
	}

	private void tuneBlock(BlockPos pos) {
		client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

	private void playBlock(BlockPos pos) {
		client.gameMode.startDestroyBlock(pos, Direction.UP);
		client.player.swing(InteractionHand.MAIN_HAND);
	}

	private void showMappedBlocks() {
		for (Map.Entry<BlockPos, Note> entry : assignments.entrySet()) {
			BlockPos pos = entry.getKey();
			boolean tuned = getPitch(pos) == entry.getValue().pitch();
			client.level.addParticle(
					tuned ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER,
					pos.getX() + 0.5,
					pos.getY() + 1.15,
					pos.getZ() + 0.5,
					0.0, 0.02, 0.0
			);
		}
	}

	private boolean isPlayableNoteBlock(BlockPos pos) {
		BlockState state = client.level.getBlockState(pos);
		return state.getBlock() instanceof NoteBlock && state.getValue(NoteBlock.INSTRUMENT).isTunable()
				&& client.level.getBlockState(pos.above()).isAir();
	}

	private int getPitch(BlockPos pos) {
		return isPlayableNoteBlock(pos) ? client.level.getBlockState(pos).getValue(NoteBlock.NOTE) : -1;
	}

	private NoteBlockInstrument getInstrument(BlockPos pos) {
		return isPlayableNoteBlock(pos)
				? client.level.getBlockState(pos).getValue(NoteBlock.INSTRUMENT)
				: NoteBlockInstrument.HARP;
	}

	private boolean readyInWorld() {
		return client.player != null && client.level != null && client.gameMode != null;
	}

	private boolean loadRandomSong() {
		List<Path> songs = library.listSongs();
		if (songs.isEmpty()) return false;
		try {
			selectSong(SongParser.parse(songs.get(random.nextInt(songs.size()))));
			return true;
		} catch (IOException exception) {
			NotebotClient.LOGGER.warn("Unable to load random song", exception);
			return false;
		}
	}

	private void saveRecording() {
		Map<Integer, List<Note>> snapshot;
		synchronized (recordingLock) {
			snapshot = new HashMap<>(recordingNotes);
		}
		if (snapshot.isEmpty()) {
			status = NotebotText.tr("status.recording_empty");
			message(status);
			return;
		}
		try {
			library.ensureDirectory();
			String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
			Path output = library.directory().resolve("recording-" + timestamp + ".txt");
			List<String> lines = new ArrayList<>();
			lines.add("// Name: Recording " + timestamp);
			lines.add("// Author: Notebot Modern");
			snapshot.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
					entry.getValue().forEach(note -> lines.add(entry.getKey() + ":" + note.pitch() + ":" + note.instrument())));
			Files.write(output, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			status = NotebotText.tr("status.recording_saved", output.getFileName().toString());
			message(status);
		} catch (IOException exception) {
			status = NotebotText.tr("status.recording_save_failed", exception.getMessage());
			message(status);
			NotebotClient.LOGGER.error("Unable to save Notebot recording", exception);
		}
	}

	private int instrumentFromSound(String soundPath) {
		NoteBlockInstrument[] instruments = NoteBlockInstrument.values();
		for (int index = 0; index < instruments.length; index++) {
			if (instruments[index].isTunable() && soundPath.endsWith(instruments[index].getSerializedName())) return index;
		}
		return 0;
	}

	private void message(Component text) {
		if (client.gui != null) client.gui.chatListener().handleSystemMessage(Component.literal("[NoteBot] ").append(text), false);
		NotebotClient.LOGGER.info(text.getString());
	}

	public NotebotConfig config() { return config; }
	public SongLibrary library() { return library; }
	public Song song() { return song; }
	public boolean active() { return active; }
	public boolean recording() { return recording; }
	public int songTick() { return songTick; }
	public int mappedBlocks() { return assignments.size(); }
	public int requiredBlocks(Song target) {
		return config.ignoreInstruments()
				? (int)target.requirements().stream().map(Note::pitch).distinct().count()
				: target.requirements().size();
	}
	public Component status() { return status; }
	public void setStatus(Component status) { this.status = status; }
}
