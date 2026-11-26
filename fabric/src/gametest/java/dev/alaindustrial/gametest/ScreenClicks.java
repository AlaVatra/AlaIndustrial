package dev.alaindustrial.gametest;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where the L3 screen pass right-clicks a block to open its menu ({@link ScreensClientGameTest}).
 *
 * <p>Most blocks are clicked on their south face, at the block's centre. The full-block solar panels are clicked on
 * their top, at the face itself, where a player looking down at a panel on the ground hits it (MOD-1033: the owner
 * reported that the two third rungs open no screen).
 */
final class ScreenClicks {

	/** Menus whose block is clicked on its top face. */
	private static final Set<String> TOP_CLICKED = Set.of("reinforced_solar_panel", "bronze_lunar_panel");

	private ScreenClicks() {}

	static BlockHitResult hitFor(String menuId, BlockPos pos) {
		if (TOP_CLICKED.contains(menuId)) {
			return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		}
		return new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
	}
}
