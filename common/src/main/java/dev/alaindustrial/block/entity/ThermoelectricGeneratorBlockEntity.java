package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ThermoAxis;
import dev.alaindustrial.block.ThermoelectricGeneratorBlock;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.GeneratorConfig;
import dev.alaindustrial.core.environment.ThermoelectricOutput;
import dev.alaindustrial.menu.ThermoelectricGeneratorMenu;
import dev.alaindustrial.registry.ModContent;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * LV thermoelectric generator (MOD-811, spec: alaindustrial:thermoelectric_generator): a passive, fuel-free
 * generator. Once a second it reads the temperature of the six neighbouring cells ({@link WorldTemperature}),
 * and every tick it makes the sum of its working axes times {@link GeneratorConfig#thermoelectricMilliEuPerDegree}
 * thousandths of an EU ({@link ThermoelectricOutput}); the fraction is banked and leaves as whole EU, so 0.5 EU/t
 * is 1 EU every second tick. Nothing burns and nothing is used up.
 *
 * <p><b>Energy leaves through every face</b>: the block has no {@code FACING}, so the base generator's role is
 * {@code OUT} everywhere and a cable connects on any free face.
 *
 * <p><b>A row shares one cable.</b> Generators touching on any face are one {@link SharedStoreGroup} ({@link #ROW},
 * the piezo path's mechanism): every member keeps its own buffer, and a member with a receiver beside it gathers the
 * row's energy into its buffer before the base's push, out of a per-tick budget the whole row shares. The face a
 * neighbouring generator takes is neutral, so it is never half of a working axis.
 *
 * <p><b>Persistence.</b> Nothing of its own: the energy buffer is the base's, the face picture is re-read on the
 * first tick after a load, and the banked fraction (under one EU) is transient — so old and new saves load alike
 * and no data-version rung is needed.
 *
 * <p>Sync channels: the base four (PROGRESS the number of working axes 0..3, MAX_PROGRESS the mode), then the six
 * face temperatures in {@code Direction} order (signed, -4..4) and the effective rate in hundredths of an EU/t.
 */
public class ThermoelectricGeneratorBlockEntity extends AbstractGeneratorBlockEntity
		implements MenuProvider, SharedStoreGroup.Member {
	/** Machine-slot count (indices before the upgrade block) — the client menu stub sizes its container from this. */
	public static final int SLOT_COUNT = 0;

	/** Running: at least one axis has a hot and a cold end. */
	public static final int MODE_OK = 0;
	/** Idle: no axis has a hot face across from a cold one. */
	public static final int MODE_NO_PAIR = 1;

	/** Ticks between two reads of the neighbours (spec: once a second). */
	public static final int SCAN_INTERVAL = 20;

	/** LV output packet cap (32 EU/t), shared with the other LV generators. */
	private static final int MAX_EXTRACT = 32;

	private static final Direction[] FACES = Direction.values();

	/**
	 * The row: generators join across all six faces, up to {@link GeneratorConfig#thermoelectricRowMaxGenerators},
	 * and the outlets gather at most {@link GeneratorConfig#thermoelectricRowOutputPerTick} together per tick.
	 */
	public static final SharedStoreGroup.Rules ROW = new SharedStoreGroup.Rules(List.of(FACES),
			ThermoelectricGeneratorBlockEntity.class, () -> GeneratorConfig.thermoelectricRowMaxGenerators,
			() -> GeneratorConfig.thermoelectricRowOutputPerTick, ThermoelectricGeneratorBlockEntity::outletMask);

	/** Ticks until the next read of the neighbours; 0 reads on the very next tick (first tick after a load). */
	private int scanCountdown;
	/** The six face temperatures of the last read, in {@code Direction} order. */
	private final int[] faceTemperatures = new int[ThermoelectricOutput.FACES];
	/** Thousandths of an EU banked toward the next whole EU. Transient: under one EU, never serialised. */
	private int remainderMilli;
	/** The effective rate in hundredths of an EU/t, after the global multiplier (channel RATE_CENTI). */
	private int rateCenti;

	/** The row this generator was last handed; rebuilt by {@link SharedStoreGroup#of}, never saved. */
	private @Nullable SharedStoreGroup row;
	/** Faces with a receiver beside them, bit {@code Direction.get3DDataValue()}; 0 on a member that is no outlet. */
	private int outletMask;
	private int rowSize = 1;
	private int rowOutlets;

	public ThermoelectricGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.THERMOELECTRIC_GENERATOR_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				GeneratorConfig.thermoelectricBuffer, MAX_EXTRACT);
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		if (scanCountdown <= 0) {
			scan(level, pos, state);
			scanCountdown = SCAN_INTERVAL;
		}
		scanCountdown--;
		int milliPerTick = ThermoelectricOutput.milliEuPerTick(faceTemperatures,
				GeneratorConfig.thermoelectricMilliEuPerDegree);
		rateCenti = (int) Math.round(milliPerTick * (double) Config.globalEuRateMultiplier / 10.0);
		if (milliPerTick <= 0) {
			remainderMilli = 0;
			return 0;
		}
		ThermoelectricOutput.Step step = ThermoelectricOutput.step(remainderMilli, milliPerTick);
		remainderMilli = step.remainderMilli();
		return step.eu();
	}

	/** Read the six neighbours, set the mode and the working-axis count, and repaint the faces if they changed. */
	private void scan(Level level, BlockPos pos, BlockState state) {
		for (Direction face : FACES) {
			faceTemperatures[face.get3DDataValue()] = WorldTemperature.temperatureAt(level, pos.relative(face));
		}
		int working = ThermoelectricOutput.workingAxes(faceTemperatures);
		this.progress = working;
		this.maxProgress = working > 0 ? MODE_OK : MODE_NO_PAIR;
		BlockState painted = state;
		for (int axis = 0; axis < ThermoelectricGeneratorBlock.AXES.size(); axis++) {
			ThermoAxis shown = ThermoAxis.of(ThermoelectricOutput.axisState(faceTemperatures[2 * axis],
					faceTemperatures[2 * axis + 1]));
			if (painted.hasProperty(ThermoelectricGeneratorBlock.AXES.get(axis))) {
				painted = painted.setValue(ThermoelectricGeneratorBlock.AXES.get(axis), shown);
			}
		}
		// Only a changed picture touches the world: a farm of steady generators sends no block updates at all.
		if (painted != state) {
			level.setBlock(pos, painted, Block.UPDATE_CLIENTS);
		}
	}

	// --- the row ----------------------------------------------------------------------------------

	/** An outlet of the row takes the other members' energy into its own buffer, before the base pushes it on. */
	@Override
	protected boolean gatherBeforePush(Level level) {
		SharedStoreGroup current = SharedStoreGroup.of(level, this, ROW);
		if (!current.isOutlet(this)) {
			return false;
		}
		long room = energy.getCapacity() - energy.getAmount();
		return room > 0 && current.gatherInto(this, room, level.getGameTime()) > 0;
	}

	/**
	 * Which faces lead to a receiver, bit {@code Direction.get3DDataValue()}: a cable, a machine, a storage — any
	 * port that takes energy in. Another thermoelectric generator never does (it accepts nothing), and is skipped
	 * outright, since the row already shares its store.
	 */
	static int outletMask(Level level, BlockPos pos) {
		EnergyLookup lookup = EnergyLookup.get();
		int mask = 0;
		for (Direction dir : FACES) {
			BlockPos next = pos.relative(dir);
			if (!level.isLoaded(next) || level.getBlockEntity(next) instanceof ThermoelectricGeneratorBlockEntity) {
				continue;
			}
			EnergyPort port = lookup.find(level, next, dir.getOpposite());
			if (port != null && port.supportsInsertion()) {
				mask |= 1 << dir.get3DDataValue();
			}
		}
		return mask;
	}

	@Override
	public @Nullable SharedStoreGroup group() {
		return row;
	}

	@Override
	public void setOutletMask(int mask) {
		outletMask = mask;
	}

	@Override
	public void joinGroup(SharedStoreGroup group, SharedStoreGroup.Status status, int size, int outlets, long stored,
			long capacity) {
		row = group;
		rowSize = size;
		rowOutlets = outlets;
	}

	/** Generators in this one's row at its last build, itself included. */
	public int rowSize() {
		return rowSize;
	}

	/** Members of this one's row with a receiver beside them, at its last build. */
	public int rowOutlets() {
		return rowOutlets;
	}

	/** True when this generator had a receiver beside it at the row's last build. */
	public boolean isRowOutlet() {
		return outletMask != 0;
	}

	/** The six face temperatures of the last read, in {@code Direction} order (a copy). */
	public int[] faceTemperatures() {
		return Arrays.copyOf(faceTemperatures, faceTemperatures.length);
	}

	/** The temperature the generator last read on {@code face}: hot {@code 1..4}, cold {@code -1..-4}, else 0. */
	public int faceTemperature(Direction face) {
		return faceTemperatures[face.get3DDataValue()];
	}

	/**
	 * GUI sync channels (MOD-712): the base four, then the six face temperatures — {@code Direction} order, signed,
	 * which a 16-bit channel carries as is — and the effective rate in hundredths of an EU/t, computed on the server
	 * because the global multiplier is a server knob the client never sees.
	 */
	public enum Channel {
		ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS,
		FACE_DOWN, FACE_UP, FACE_NORTH, FACE_SOUTH, FACE_WEST, FACE_EAST,
		RATE_CENTI
	}

	/** Width of {@link #getDataAccess()}, which the menu's client stub sizes itself from. */
	public static final int DATA_COUNT = Channel.values().length;

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.FACE_DOWN, () -> faceTemperatures[Direction.DOWN.get3DDataValue()])
				.read(Channel.FACE_UP, () -> faceTemperatures[Direction.UP.get3DDataValue()])
				.read(Channel.FACE_NORTH, () -> faceTemperatures[Direction.NORTH.get3DDataValue()])
				.read(Channel.FACE_SOUTH, () -> faceTemperatures[Direction.SOUTH.get3DDataValue()])
				.read(Channel.FACE_WEST, () -> faceTemperatures[Direction.WEST.get3DDataValue()])
				.read(Channel.FACE_EAST, () -> faceTemperatures[Direction.EAST.get3DDataValue()])
				.read(Channel.RATE_CENTI, () -> rateCenti)
				.build();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ThermoelectricGeneratorMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
