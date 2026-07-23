package net.skeleton.notebot.client.song;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongTest {
    @Test
    void tracksLengthAsHighestTick() {
        Map<Integer, List<Note>> notes = new HashMap<>();
        Song.addNote(notes, 5, new Note(1, 0));
        Song.addNote(notes, 12, new Note(2, 0));

        Song song = new Song(Path.of("song.txt"), "Name", "Author", "Notelist", notes);

        assertEquals(12, song.length());
        assertEquals(2, song.noteCount());
    }

    @Test
    void clampsNegativeTicksToZero() {
        Map<Integer, List<Note>> notes = new HashMap<>();
        Song.addNote(notes, -3, new Note(1, 0));

        Song song = new Song(Path.of("song.txt"), "Name", "Author", "Notelist", notes);

        assertEquals(1, song.notesAt(0).size());
        assertTrue(song.notesAt(-3).isEmpty());
    }

    @Test
    void requirementsDeduplicateIdenticalNotes() {
        Map<Integer, List<Note>> notes = new HashMap<>();
        Song.addNote(notes, 0, new Note(3, 0));
        Song.addNote(notes, 1, new Note(3, 0));
        Song.addNote(notes, 2, new Note(4, 0));

        Song song = new Song(Path.of("song.txt"), "Name", "Author", "Notelist", notes);

        assertEquals(2, song.requirements().size());
        assertEquals(3, song.noteCount());
    }

    @Test
    void notesAtMissingTickIsEmpty() {
        Song song = new Song(Path.of("song.txt"), "Name", "Author", "Notelist", new HashMap<>());

        assertTrue(song.notesAt(0).isEmpty());
        assertEquals(0, song.length());
    }
}
