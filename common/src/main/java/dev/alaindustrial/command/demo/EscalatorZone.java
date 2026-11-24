package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.EscalatorDriveBlock;
import dev.alaindustrial.block.EscalatorLandingBlock;
import dev.alaindustrial.block.EscalatorSectionBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Zone <b>escalator</b> (MOD-969, the free rows z=0..1 east of the bench): a short working escalator climbing east.
 * Its LV drive is the bottom landing, set into the floor layer one step down the diagonal from the bottom section,
 * with a charged buffer; two sections (each with its glass balustrade half) and the top landing. Step on and it runs;
 * the MV drive stands beside it on the floor, unconnected, as the tier readout.
 *
 * <p>The balustrade halves are written by the zone too, after the stair halves have put them up, so the ledger owns
 * every cell the escalator takes.
 *
 * <p>Domain (coding standard, section 1): Decor, the domain of its blocks.
 */
final class EscalatorZone implements DemoZone {
	private static final int X = 82;

	@Override
	public void build(StandWriter w) {
		w.place(w.origin().offset(X - 1, 0, 0), ModContent.ESCALATOR_DRIVE.get().defaultBlockState()
				.setValue(EscalatorDriveBlock.FACING, Direction.EAST));
		w.chargeBuffer(X - 1, 0, 0);
		for (int i = 0; i < 2; i++) {
			BlockState lower = ModContent.ESCALATOR_SECTION.get().defaultBlockState()
					.setValue(EscalatorSectionBlock.FACING, Direction.EAST);
			w.place(w.origin().offset(X + i, 1 + i, 0), lower);
			w.place(w.origin().offset(X + i, 2 + i, 0),
					lower.setValue(EscalatorSectionBlock.HALF, DoubleBlockHalf.UPPER));
		}
		w.place(w.origin().offset(X + 2, 2, 0), ModContent.ESCALATOR_LANDING.get().defaultBlockState()
				.setValue(EscalatorLandingBlock.FACING, Direction.EAST));
		w.place(w.origin().offset(X - 2, 1, 1), ModContent.ESCALATOR_DRIVE_MV.get().defaultBlockState()
				.setValue(EscalatorDriveBlock.FACING, Direction.EAST));
	}
}
