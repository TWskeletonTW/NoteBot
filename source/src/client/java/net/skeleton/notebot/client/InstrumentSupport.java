package net.skeleton.notebot.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.skeleton.notebot.client.song.Note;

import java.util.List;

public final class InstrumentSupport {
	private static final NoteBlockInstrument[] INSTRUMENTS = NoteBlockInstrument.values();

	private InstrumentSupport() {
	}

	public static NoteBlockInstrument instrument(int ordinal) {
		if (ordinal < 0 || ordinal >= INSTRUMENTS.length || !INSTRUMENTS[ordinal].isTunable()) {
			return NoteBlockInstrument.HARP;
		}
		return INSTRUMENTS[ordinal];
	}

	public static String name(int ordinal) {
		return instrument(ordinal).getSerializedName();
	}

	public static ItemStack icon(int ordinal) {
		Item item = switch (instrument(ordinal)) {
			case HARP -> Items.DIRT;
			case BASEDRUM -> Items.STONE;
			case SNARE -> Items.SAND;
			case HAT -> Items.GLASS;
			case BASS -> Items.OAK_WOOD;
			case FLUTE -> Items.CLAY;
			case BELL -> Items.GOLD_BLOCK;
			case GUITAR -> Items.WOOL.white();
			case CHIME -> Items.PACKED_ICE;
			case XYLOPHONE -> Items.BONE_BLOCK;
			case IRON_XYLOPHONE -> Items.IRON_BLOCK;
			case COW_BELL -> Items.SOUL_SAND;
			case DIDGERIDOO -> Items.PUMPKIN;
			case BIT -> Items.EMERALD_BLOCK;
			case BANJO -> Items.HAY_BLOCK;
			case PLING -> Items.GLOWSTONE;
			case TRUMPET, TRUMPET_EXPOSED, TRUMPET_WEATHERED, TRUMPET_OXIDIZED -> Items.COPPER_INGOT;
			default -> Items.NOTE_BLOCK;
		};
		return new ItemStack(item);
	}

	public static void preview(List<Note> notes) {
		Minecraft client = Minecraft.getInstance();
		for (Note note : notes) {
			NoteBlockInstrument instrument = instrument(note.instrument());
			float pitch = (float)Math.pow(2.0, (note.pitch() - 12) / 12.0);
			client.getSoundManager().play(SimpleSoundInstance.forUI(instrument.getSoundEvent().value(), pitch, 0.8F));
		}
	}
}
