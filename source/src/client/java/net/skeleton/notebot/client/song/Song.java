/*
 * Derived from BleachHack's Notebot under the GNU GPL v3 or later.
 */
package net.skeleton.notebot.client.song;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Song {
	private final Path path;
	private final String name;
	private final String author;
	private final String format;
	private final Map<Integer, List<Note>> notes;
	private final Set<Note> requirements;
	private final int length;

	public Song(Path path, String name, String author, String format, Map<Integer, List<Note>> notes) {
		this.path = path;
		this.name = name;
		this.author = author;
		this.format = format;

		Map<Integer, List<Note>> copy = new LinkedHashMap<>();
		notes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
				copy.put(entry.getKey(), List.copyOf(entry.getValue())));
		this.notes = Collections.unmodifiableMap(copy);

		Set<Note> required = new LinkedHashSet<>();
		copy.values().forEach(required::addAll);
		this.requirements = Collections.unmodifiableSet(required);
		this.length = copy.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
	}

	public Path path() {
		return path;
	}

	public String filename() {
		return path.getFileName().toString();
	}

	public String name() {
		return name;
	}

	public String author() {
		return author;
	}

	public String format() {
		return format;
	}

	public List<Note> notesAt(int tick) {
		return notes.getOrDefault(tick, List.of());
	}

	public Map<Integer, List<Note>> notes() {
		return notes;
	}

	public Set<Note> requirements() {
		return requirements;
	}

	public int length() {
		return length;
	}

	public int noteCount() {
		return notes.values().stream().mapToInt(List::size).sum();
	}

	public static void addNote(Map<Integer, List<Note>> notes, int tick, Note note) {
		notes.computeIfAbsent(Math.max(0, tick), ignored -> new ArrayList<>()).add(note);
	}
}
