package dev.alaindustrial.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The shape of one escalator (MOD-969): which sections, which landing, which drive. Read from the blocks every
 * time, never stored in them: the sections and the landing have no block entity, and the drive keeps only the last
 * answer for its own tick.
 *
 * <p>Section {@code i} of a chain whose bottom section is {@code B} and whose sections face {@code F} (the climbing
 * direction) stands at {@code B + i·F + i·UP} (its lower half; the balustrade half is the block above); the top
 * landing stands one block past the last section at its height. The drive is the bottom landing: the floor block one
 * step down the diagonal from the bottom section, {@code B - F - UP}, facing {@code F} (sketch B, owner 2026-11-22).
 *
 * @param bottom   the lowest section, or {@code null} when the drive has none above it
 * @param facing   the climbing direction of the chain
 * @param sections how many sections follow the diagonal from {@code bottom} (counted up to {@code max + 1})
 * @param landing  whether a top landing stands past the last section
 * @param problem  why the chain cannot run, or {@link Problem#NONE}
 */
public record EscalatorChain(@Nullable BlockPos bottom, Direction facing, int sections, boolean landing,
		Problem problem) {

	/** Why a chain cannot run; the drive names it to the player (lang key suffix). */
	public enum Problem {
		NONE("ok"),
		NO_SECTION("no_section"),
		NO_LANDING("no_landing"),
		TOO_LONG("too_long");

		public final String key;

		Problem(String key) {
			this.key = key;
		}
	}

	private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** A chain the drive found nothing of. */
	public static EscalatorChain none() {
		return new EscalatorChain(null, Direction.NORTH, 0, false, Problem.NO_SECTION);
	}

	public boolean formed() {
		return problem == Problem.NONE;
	}

	/** Section {@code i} of the chain that starts at {@code bottom} and climbs towards {@code facing}. */
	public static BlockPos section(BlockPos bottom, Direction facing, int i) {
		return bottom.relative(facing, i).above(i);
	}

	/** Where the top landing of a chain of {@code sections} stands. */
	public static BlockPos landingPos(BlockPos bottom, Direction facing, int sections) {
		return section(bottom, facing, sections - 1).relative(facing);
	}

	/** Every section of this chain, bottom first; empty when there is no bottom. */
	public List<BlockPos> sectionPositions(int limit) {
		List<BlockPos> out = new ArrayList<>();
		if (bottom == null) {
			return out;
		}
		for (int i = 0; i < Math.min(sections, limit); i++) {
			out.add(section(bottom, facing, i));
		}
		return out;
	}

	/**
	 * The chain the drive at {@code drive} would run, looking at most {@code maxSections + 1} sections up so that a
	 * chain too long for it is named as such rather than cut short. The drive's own facing names the band's side.
	 */
	public static EscalatorChain scan(BlockGetter level, BlockPos drive, int maxSections) {
		BlockState driveState = level.getBlockState(drive);
		if (!(driveState.getBlock() instanceof EscalatorDriveBlock)) {
			return none();
		}
		Direction facing = driveState.getValue(EscalatorDriveBlock.FACING);
		BlockPos bottom = above(drive, facing);
		if (!isSection(level.getBlockState(bottom), facing)) {
			return none();
		}
		int count = 0;
		while (count <= maxSections && isSection(level.getBlockState(section(bottom, facing, count)), facing)) {
			count++;
		}
		if (count > maxSections) {
			return new EscalatorChain(bottom, facing, count, false, Problem.TOO_LONG);
		}
		boolean landing = level.getBlockState(landingPos(bottom, facing, count)).getBlock()
				instanceof EscalatorLandingBlock;
		return new EscalatorChain(bottom, facing, count, landing, landing ? Problem.NONE : Problem.NO_LANDING);
	}

	/**
	 * The facing a section placed at {@code pos} takes from the band below it: that of the section or the drive one
	 * step down the diagonal whose facing points at {@code pos}; {@code null} when there is none.
	 */
	public static @Nullable Direction facingFromBelow(BlockGetter level, BlockPos pos) {
		for (Direction side : SIDES) {
			BlockState state = level.getBlockState(below(pos, side));
			if (isSection(state, side) || (state.getBlock() instanceof EscalatorDriveBlock
					&& state.getValue(EscalatorDriveBlock.FACING) == side)) {
				return side;
			}
		}
		return null;
	}

	/** The facing a drive placed at {@code pos} takes from a bottom section already standing above it, or null. */
	public static @Nullable Direction facingFromAbove(BlockGetter level, BlockPos pos) {
		for (Direction side : SIDES) {
			if (isSection(level.getBlockState(above(pos, side)), side)) {
				return side;
			}
		}
		return null;
	}

	/** The section one step down the diagonal from {@code pos}. */
	static BlockPos below(BlockPos pos, Direction facing) {
		return pos.relative(facing.getOpposite()).below();
	}

	/** The section one step up the diagonal from {@code pos}. */
	static BlockPos above(BlockPos pos, Direction facing) {
		return pos.relative(facing).above();
	}

	/** Whether {@code state} is the stair half of a section climbing towards {@code facing}. */
	static boolean isSection(BlockState state, Direction facing) {
		return EscalatorSectionBlock.isLower(state) && state.getValue(EscalatorSectionBlock.FACING) == facing;
	}

	/**
	 * The drive that runs the chain {@code sectionPos} belongs to, or {@code null}: walk down the diagonal to the
	 * bottom section; the drive is the next step down, facing the same way. At most {@code limit} steps, so a cut
	 * chain cannot send the walk far.
	 */
	public static @Nullable BlockPos findDrive(BlockGetter level, BlockPos sectionPos, Direction facing, int limit) {
		BlockPos bottom = sectionPos;
		for (int i = 0; i < limit && isSection(level.getBlockState(below(bottom, facing)), facing); i++) {
			bottom = below(bottom, facing);
		}
		BlockPos drive = below(bottom, facing);
		BlockState state = level.getBlockState(drive);
		return state.getBlock() instanceof EscalatorDriveBlock && state.getValue(EscalatorDriveBlock.FACING) == facing
				? drive : null;
	}

	/** The last section below a top landing at {@code landing}, or {@code null} when none points at it. */
	public static @Nullable BlockPos sectionUnderLanding(BlockGetter level, BlockPos landing) {
		for (Direction side : SIDES) {
			BlockPos at = landing.relative(side.getOpposite());
			if (isSection(level.getBlockState(at), side)) {
				return at;
			}
		}
		return null;
	}
}
