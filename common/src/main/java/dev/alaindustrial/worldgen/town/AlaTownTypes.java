package dev.alaindustrial.worldgen.town;

import dev.alaindustrial.Industrialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * The two registry entries of the ALA town (MOD-830), declared once for both loaders: the structure type
 * {@code alaindustrial:ala_town} the town's structure files name, and the piece type
 * {@code alaindustrial:ala_town_lab} its saved chunks name. Both are rows of the shared
 * {@code ModWorldgenTypes.STRUCTURE_TYPES} / {@code STRUCTURE_PIECES} lists that every loader replays (MOD-962) —
 * the same objects, so a saved town reads back on any loader.
 *
 * <p>Both ids go into saved chunks: they are not renamed after the first release (ADR-050).
 */
public final class AlaTownTypes {

	/** Registry id of the structure type, the {@code "type"} of {@code worldgen/structure/ala_town_*.json}. */
	public static final Identifier STRUCTURE_TYPE_ID = Industrialization.id("ala_town");

	/** Registry id of the lab piece's type, written into every saved town. */
	public static final Identifier LAB_PIECE_ID = Industrialization.id("ala_town_lab");

	/** The structure type: what decodes a town's structure file. */
	public static final StructureType<AlaTownStructure> STRUCTURE_TYPE = () -> AlaTownStructure.CODEC;

	/** The lab piece's type: what reads a saved lab piece back. */
	public static final StructurePieceType.StructureTemplateType LAB_PIECE = AlaTownLabPiece::new;

	private AlaTownTypes() {
	}
}
