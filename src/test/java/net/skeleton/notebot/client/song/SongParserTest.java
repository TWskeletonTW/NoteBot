package net.skeleton.notebot.client.song;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SongParserTest {
    @TempDir
    Path tempDir;

    @Test
    void foldPitchWrapsIntoValidRange() {
        assertEquals(0, SongParser.foldPitch(0));
        assertEquals(24, SongParser.foldPitch(24));
        assertEquals(11, SongParser.foldPitch(-1));
        assertEquals(0, SongParser.foldPitch(-12));
        assertEquals(13, SongParser.foldPitch(25));
        assertEquals(24, SongParser.foldPitch(36));
    }

    @Test
    void mapNbsInstrumentTranslatesKnownIndices() {
        assertEquals(0, SongParser.mapNbsInstrument(0));
        assertEquals(4, SongParser.mapNbsInstrument(1));
        assertEquals(1, SongParser.mapNbsInstrument(2));
        assertEquals(0, SongParser.mapNbsInstrument(99));
    }

    @Test
    void percussionInstrumentMapsMidiDrumKeys() {
        assertEquals(2, SongParser.percussionInstrument(38));
        assertEquals(2, SongParser.percussionInstrument(40));
        assertEquals(3, SongParser.percussionInstrument(44));
        assertEquals(1, SongParser.percussionInstrument(60));
    }

    @Test
    void programInstrumentMapsGeneralMidiRanges() {
        assertEquals(15, SongParser.programInstrument(0));
        assertEquals(9, SongParser.programInstrument(15));
        assertEquals(6, SongParser.programInstrument(23));
        assertEquals(7, SongParser.programInstrument(31));
        assertEquals(4, SongParser.programInstrument(39));
        assertEquals(0, SongParser.programInstrument(55));
        assertEquals(5, SongParser.programInstrument(63));
        assertEquals(8, SongParser.programInstrument(71));
        assertEquals(13, SongParser.programInstrument(79));
        assertEquals(14, SongParser.programInstrument(95));
        assertEquals(0, SongParser.programInstrument(120));
    }

    @Test
    void parsesNotelistWithHeaderAndNotes() throws IOException {
        Path path = tempDir.resolve("song.notelist");
        Files.writeString(path, String.join("\n",
                "// Name: My Song",
                "// Author: Composer",
                "0:12:0",
                "4:30:1",
                "malformed line",
                "8:5:2"));

        Song song = SongParser.parse(path);

        assertEquals("My Song", song.name());
        assertEquals("Composer", song.author());
        assertEquals("Notelist", song.format());
        // The out-of-range pitch (30) is clamped to 24 rather than rejected.
        assertEquals(List.of(new Note(24, 1)), song.notesAt(4));
        assertEquals(List.of(new Note(12, 0)), song.notesAt(0));
        assertEquals(List.of(new Note(5, 2)), song.notesAt(8));
        assertEquals(3, song.noteCount());
    }

    @Test
    void notelistFallsBackToFilenameWhenNoHeader() throws IOException {
        Path path = tempDir.resolve("unnamed.txt");
        Files.writeString(path, "0:1:0");

        Song song = SongParser.parse(path);

        assertEquals("unnamed", song.name());
        assertEquals("Unknown", song.author());
    }

    @Test
    void parsesMinimalNbsFile() throws IOException {
        Path path = tempDir.resolve("song.nbs");
        Files.write(path, buildMinimalNbs());

        Song song = SongParser.parse(path);

        assertEquals("Test Song", song.name());
        assertEquals("Some Author", song.author());
        assertEquals("NBS v3", song.format());
        assertEquals(List.of(new Note(12, 0)), song.notesAt(0));
        assertEquals(1, song.noteCount());
    }

    @Test
    void rejectsTruncatedNbsFile() throws IOException {
        Path path = tempDir.resolve("broken.nbs");
        Files.write(path, new byte[]{0, 0, 3});

        assertThrows(IOException.class, () -> SongParser.parse(path));
    }

    @Test
    void parsesMidiNotesWithTempoAndInstrumentMapping() throws IOException, InvalidMidiDataException {
        int resolution = 24;
        Sequence sequence = new Sequence(Sequence.PPQ, resolution);
        Track track = sequence.createTrack();

        // Default tempo (500,000 micros/quarter): 12 ticks -> 250,000 micros -> game tick 5.
        track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 40, 0), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 66, 100), 12));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 66, 0), 24));

        // Percussion channel (9): key 38 maps to instrument 2, at game tick 0.
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 9, 38, 100), 0));

        Path path = tempDir.resolve("song.mid");
        MidiSystem.write(sequence, 1, path.toFile());

        Song song = SongParser.parse(path);

        assertEquals("MIDI", song.format());
        // program 40 falls in the <=55 bucket, which maps to instrument 0.
        assertEquals(List.of(new Note(12, 0)), song.notesAt(5));
        // Pitch is always (key - 54) folded into range, even for percussion: foldPitch(38 - 54) = 8.
        assertEquals(List.of(new Note(8, 2)), song.notesAt(0));
    }

    private static byte[] buildMinimalNbs() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUnsignedShort(out, 0); // new-format marker
        out.write(3); // version
        out.write(10); // vanilla instrument count (unused by the parser)
        writeUnsignedShort(out, 1); // song length (unused by the parser)
        writeUnsignedShort(out, 1); // layer count (unused by the parser)
        writeString(out, "Test Song");
        writeString(out, ""); // internal author
        writeString(out, "Some Author"); // original author
        writeString(out, ""); // description
        writeUnsignedShort(out, 2000); // tempo: 20 ticks/second
        out.write(new byte[23]); // unused header fields
        writeString(out, ""); // imported filename

        writeUnsignedShort(out, 1); // tick jump -> tick 0
        writeUnsignedShort(out, 1); // layer jump -> first note in layer
        out.write(0); // NBS instrument 0 -> mapped instrument 0
        out.write(45); // pitch byte: 45 - 33 = 12
        writeUnsignedShort(out, 0); // end of layer
        writeUnsignedShort(out, 0); // end of note data

        return out.toByteArray();
    }

    private static void writeUnsignedShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private static void writeString(ByteArrayOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        out.write(length & 0xff);
        out.write((length >> 8) & 0xff);
        out.write((length >> 16) & 0xff);
        out.write((length >> 24) & 0xff);
        out.write(bytes);
    }
}
