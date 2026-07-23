package net.skeleton.notebot.client.song;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoteTest {
    @Test
    void acceptsBoundaryPitches() {
        assertEquals(0, new Note(0, 0).pitch());
        assertEquals(24, new Note(24, 0).pitch());
    }

    @Test
    void rejectsPitchOutsideZeroToTwentyFour() {
        assertThrows(IllegalArgumentException.class, () -> new Note(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Note(25, 0));
    }

    @Test
    void rejectsNegativeInstrument() {
        assertThrows(IllegalArgumentException.class, () -> new Note(0, -1));
    }
}
