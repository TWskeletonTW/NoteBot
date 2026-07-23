/*
 * NBS/Notelist support is derived from BleachHack under the GNU GPL v3 or later.
 */
package net.skeleton.notebot.client.song;

import net.skeleton.notebot.client.NotebotClient;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SongParser {
	private static final int MAX_STRING_BYTES = 1_048_576;

	private SongParser() {
	}

	public static Song parse(Path path) throws IOException {
		String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (filename.endsWith(".mid") || filename.endsWith(".midi")) {
			return parseMidi(path);
		}
		if (filename.endsWith(".nbs")) {
			return parseNbs(path);
		}
		return parseNotelist(path);
	}

	private static Song parseNotelist(Path path) throws IOException {
		Map<Integer, List<Note>> notes = new HashMap<>();
		String name = baseName(path);
		String author = "Unknown";

		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String line = raw.trim();
			if (line.startsWith("// Name: ")) {
				name = line.substring(9).trim();
			} else if (line.startsWith("// Author: ")) {
				author = line.substring(11).trim();
			} else if (!line.isEmpty() && !line.startsWith("//")) {
				String[] parts = line.split(":");
				if (parts.length < 3) {
					NotebotClient.LOGGER.warn("Ignoring malformed note in {}: {}", path, line);
					continue;
				}
				try {
					int tick = Integer.parseInt(parts[0].trim());
					int pitch = Math.clamp(Integer.parseInt(parts[1].trim()), 0, 24);
					int instrument = Math.max(0, Integer.parseInt(parts[2].trim()));
					Song.addNote(notes, tick, new Note(pitch, instrument));
				} catch (NumberFormatException exception) {
					NotebotClient.LOGGER.warn("Ignoring malformed note in {}: {}", path, line);
				}
			}
		}

		return new Song(path, name, author, "Notelist", notes);
	}

	private static Song parseMidi(Path path) throws IOException {
		Map<Integer, List<Note>> notes = new HashMap<>();
		try {
			Sequence sequence = MidiSystem.getSequence(path.toFile());
			List<MidiEvent> events = new ArrayList<>();
			for (Track track : sequence.getTracks()) {
				for (int index = 0; index < track.size(); index++) {
					events.add(track.get(index));
				}
			}
			events.sort(Comparator.comparingLong(MidiEvent::getTick));

			List<TempoChange> tempos = collectTempos(events);
			int[] programs = new int[16];
			for (MidiEvent event : events) {
				MidiMessage message = event.getMessage();
				if (!(message instanceof ShortMessage shortMessage)) {
					continue;
				}

				int channel = shortMessage.getChannel();
				if (shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE) {
					programs[channel] = shortMessage.getData1();
					continue;
				}
				if (shortMessage.getCommand() != ShortMessage.NOTE_ON || shortMessage.getData2() <= 0) {
					continue;
				}

				int tick = (int)Math.round(tickToMicros(sequence, event.getTick(), tempos) / 50_000.0);
				int pitch = foldPitch(shortMessage.getData1() - 54);
				int instrument = channel == 9
						? percussionInstrument(shortMessage.getData1())
						: programInstrument(programs[channel]);
				Song.addNote(notes, tick, new Note(pitch, instrument));
			}
		} catch (Exception exception) {
			throw new IOException("Unable to parse MIDI file " + path, exception);
		}

		return new Song(path, baseName(path), "Unknown", "MIDI", notes);
	}

	private static List<TempoChange> collectTempos(List<MidiEvent> events) {
		List<TempoChange> tempos = new ArrayList<>();
		tempos.add(new TempoChange(0, 500_000));
		for (MidiEvent event : events) {
			if (event.getMessage() instanceof MetaMessage meta && meta.getType() == 0x51 && meta.getData().length >= 3) {
				byte[] data = meta.getData();
				int microsPerQuarter = (data[0] & 0xff) << 16 | (data[1] & 0xff) << 8 | data[2] & 0xff;
				tempos.add(new TempoChange(event.getTick(), microsPerQuarter));
			}
		}
		tempos.sort(Comparator.comparingLong(TempoChange::tick));
		return tempos;
	}

	private static long tickToMicros(Sequence sequence, long targetTick, List<TempoChange> tempos) {
		if (sequence.getDivisionType() != Sequence.PPQ) {
			return Math.round(targetTick * 1_000_000.0 / (sequence.getDivisionType() * sequence.getResolution()));
		}

		long micros = 0;
		long previousTick = 0;
		int tempo = 500_000;
		for (TempoChange change : tempos) {
			if (change.tick() > targetTick) {
				break;
			}
			micros += (change.tick() - previousTick) * tempo / sequence.getResolution();
			previousTick = change.tick();
			tempo = change.microsPerQuarter();
		}
		return micros + (targetTick - previousTick) * tempo / sequence.getResolution();
	}

	private static Song parseNbs(Path path) throws IOException {
		Map<Integer, List<Note>> notes = new HashMap<>();
		String name = baseName(path);
		String author = "Unknown";
		int version;

		try (InputStream input = Files.newInputStream(path)) {
			int firstLength = readUnsignedShort(input);
			if (firstLength == 0) {
				version = readUnsignedByte(input);
				readUnsignedByte(input); // vanilla instrument count
				readUnsignedShort(input); // song length
				readUnsignedShort(input); // layer count
			} else {
				version = 0;
				readUnsignedShort(input); // layer count
			}

			String internalName = readString(input);
			String internalAuthor = readString(input);
			String originalAuthor = readString(input);
			readString(input); // description
			if (!internalName.isBlank()) {
				name = internalName;
			}
			if (!originalAuthor.isBlank()) {
				author = originalAuthor;
			} else if (!internalAuthor.isBlank()) {
				author = internalAuthor;
			}

			float tempo = Math.max(0.01F, readUnsignedShort(input) / 100.0F);
			skipFully(input, 23);
			readString(input); // imported filename
			if (version >= 4) {
				skipFully(input, 4);
			}

			double tick = -1;
			int tickJump;
			while ((tickJump = readUnsignedShort(input)) != 0) {
				tick += tickJump * (20.0 / tempo);
				while (readUnsignedShort(input) != 0) {
					int instrument = mapNbsInstrument(readUnsignedByte(input));
					int pitch = foldPitch(readUnsignedByte(input) - 33);
					Song.addNote(notes, (int)Math.round(tick), new Note(pitch, instrument));
					if (version >= 4) {
						skipFully(input, 4);
					}
				}
			}
		}

		return new Song(path, name, author, "NBS v" + version, notes);
	}

	static int foldPitch(int pitch) {
		while (pitch < 0) {
			pitch += 12;
		}
		while (pitch > 24) {
			pitch -= 12;
		}
		return pitch;
	}

	static int mapNbsInstrument(int instrument) {
		return switch (instrument) {
			case 1 -> 4;
			case 2 -> 1;
			case 3 -> 2;
			case 4 -> 3;
			case 5 -> 7;
			case 6 -> 5;
			case 7 -> 6;
			case 8 -> 8;
			case 9 -> 9;
			case 10 -> 10;
			case 11 -> 11;
			case 12 -> 12;
			case 13 -> 13;
			case 14 -> 14;
			case 15 -> 15;
			default -> 0;
		};
	}

	static int percussionInstrument(int midiKey) {
		if (midiKey == 38 || midiKey == 40) return 2;
		if (midiKey >= 42 && midiKey <= 46) return 3;
		return 1;
	}

	static int programInstrument(int program) {
		if (program <= 7) return 15;
		if (program <= 15) return 9;
		if (program <= 23) return 6;
		if (program <= 31) return 7;
		if (program <= 39) return 4;
		if (program <= 55) return 0;
		if (program <= 63) return 5;
		if (program <= 71) return 8;
		if (program <= 79) return 13;
		if (program <= 95) return 14;
		return 0;
	}

	private static String baseName(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private static int readUnsignedByte(InputStream input) throws IOException {
		int value = input.read();
		if (value < 0) throw new EOFException();
		return value;
	}

	private static int readUnsignedShort(InputStream input) throws IOException {
		return readUnsignedByte(input) | readUnsignedByte(input) << 8;
	}

	private static int readInt(InputStream input) throws IOException {
		return readUnsignedByte(input)
				| readUnsignedByte(input) << 8
				| readUnsignedByte(input) << 16
				| readUnsignedByte(input) << 24;
	}

	private static String readString(InputStream input) throws IOException {
		int length = readInt(input);
		if (length < 0 || length > MAX_STRING_BYTES) {
			throw new IOException("Invalid NBS string length: " + length);
		}
		byte[] bytes = input.readNBytes(length);
		if (bytes.length != length) throw new EOFException();
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void skipFully(InputStream input, int bytes) throws IOException {
		if (input.readNBytes(bytes).length != bytes) {
			throw new EOFException();
		}
	}

	private record TempoChange(long tick, int microsPerQuarter) {
	}
}
