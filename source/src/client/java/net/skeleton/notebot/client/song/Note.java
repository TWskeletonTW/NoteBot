/*
 * Derived from BleachHack's Notebot under the GNU GPL v3 or later.
 */
package net.skeleton.notebot.client.song;

public record Note(int pitch, int instrument) {
	public Note {
		if (pitch < 0 || pitch > 24) {
			throw new IllegalArgumentException("Pitch must be between 0 and 24: " + pitch);
		}
		if (instrument < 0) {
			throw new IllegalArgumentException("Instrument must not be negative: " + instrument);
		}
	}
}
