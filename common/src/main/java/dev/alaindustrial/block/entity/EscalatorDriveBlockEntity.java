package dev.alaindustrial.block.entity;

import dev.alaindustrial.block.EscalatorCarry;
import dev.alaindustrial.block.EscalatorChain;
import dev.alaindustrial.block.EscalatorDriveBlock;
import dev.alaindustrial.block.EscalatorLandingBlock;
import dev.alaindustrial.block.EscalatorMode;
import dev.alaindustrial.block.EscalatorSectionBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.escalator.EscalatorBandMotion;
import dev.alaindustrial.core.escalator.EscalatorConfig;
import dev.alaindustrial.core.escalator.EscalatorRules;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The escalator drive (MOD-969): the one block of an escalator with a block entity. It finds its chain, decides
 * whether the band moves, pays for it and writes what it decided onto every section's block state, where the
 * sections and every client read it.
 *
 * <p><b>Energy only while carrying.</b> The band runs while someone rode it within the last
 * {@link EscalatorConfig#escalatorIdleTicks} ticks; then the drive sleeps on the machine core's idle gate and spends
 * nothing. A rider wakes it: stepping onto a sleeping section or onto the top landing calls {@link #onContact}. While
 * the band runs, the drive itself looks for riders on its chain every few ticks.
 *
 * <p><b>The draw</b> is the drive's fixed part plus a part per section, in thousandths of an EU
 * ({@link EscalatorRules#milliEuPerTick}); the thousandths add up here and leave the buffer as whole EU.
 *
 * <p><b>The chain</b> is read again every {@link EscalatorConfig#escalatorScanIntervalTicks} ticks and at once when
 * a section, the landing or a neighbour changes ({@link #requestRescan}). Its sections are written only when their
 * state differs, with {@code UPDATE_CLIENTS} and no neighbour updates.
 */
public final class EscalatorDriveBlockEntity extends MachineBlockEntity {

	/** Ticks between two looks for riders while the band runs. */
	private static final int RIDER_CHECK_TICKS = 5;

	/** Whether the band runs down (towards the bottom section). Saved. */
	private boolean reversed;

	/** The last chain the drive read; transient, rebuilt on load by the first tick. */
	private EscalatorChain chain = EscalatorChain.none();
	private boolean rescanRequested = true;
	/** Game time of the last read of the chain; transient. */
	private long lastScanTick = Long.MIN_VALUE / 2;
	private int riderCheckCooldown;
	/** Game time of the last rider seen, or far in the past; transient, a loaded band has nobody on it. */
	private long lastContactTick = Long.MIN_VALUE / 2;
	/** Thousandths of an EU drawn but not yet taken out of the buffer. */
	private long milliEuOwed;

	// --- client animation (never saved, never synced: derived from the sections' block states) ---
	private EscalatorChain clientChain = EscalatorChain.none();
	private int clientScanCooldown;
	private final EscalatorBandMotion band = new EscalatorBandMotion();

	public EscalatorDriveBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ESCALATOR_DRIVE_BE.get(), pos, state, tierOf(state), 0, bufferOf(tierOf(state)),
				tierOf(state).maxVoltage(), 0L);
	}

	private static EnergyTier tierOf(BlockState state) {
		return state.getBlock() instanceof EscalatorDriveBlock drive ? drive.tier() : EnergyTier.LV;
	}

	private static long bufferOf(EnergyTier tier) {
		return tier == EnergyTier.MV ? EscalatorConfig.escalatorBufferMv : EscalatorConfig.escalatorBufferLv;
	}

	private boolean mv() {
		return tierOf(getBlockState()) == EnergyTier.MV;
	}

	/** Longest chain this drive pulls. */
	public int maxSections() {
		return mv() ? EscalatorConfig.escalatorMaxSectionsMv : EscalatorConfig.escalatorMaxSectionsLv;
	}

	/** Draw of the running band in thousandths of an EU per tick, for the current chain. */
	public long milliEuPerTick() {
		int base = mv() ? EscalatorConfig.escalatorEuPerTickMv : EscalatorConfig.escalatorEuPerTickLv;
		return EscalatorRules.milliEuPerTick(base, EscalatorConfig.escalatorMilliEuPerSection,
				chain.formed() ? chain.sections() : 0);
	}

	// --- the player's hands ---

	/** Flip the band's direction (a wrench click); the sections follow on this tick. */
	public void reverse() {
		reversed = !reversed;
		setChanged();
		requestRescan();
	}

	public boolean isReversed() {
		return reversed;
	}

	/** Someone stepped onto a sleeping band: run it. */
	public void onContact(long gameTime) {
		lastContactTick = gameTime;
		wake();
	}

	/** Look at the chain again on the next tick instead of at the next periodic check. */
	public void requestRescan() {
		rescanRequested = true;
		wake();
	}

	/** The chain as last read; for the tests and the status line. */
	public EscalatorChain chain() {
		return chain;
	}

	public EscalatorMode mode() {
		return getBlockState().getValue(EscalatorDriveBlock.MODE);
	}

	// --- server tick ---

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		long now = level.getGameTime();
		// By the clock, not by a countdown: a sleeping drive runs only one tick in IDLE_SLEEP_TICKS.
		if (rescanRequested || now - lastScanTick >= EscalatorConfig.escalatorScanIntervalTicks) {
			rescanRequested = false;
			lastScanTick = now;
			chain = EscalatorChain.scan(level, pos, maxSections());
			alignLanding(level);
		}
		if (chain.formed() && --riderCheckCooldown <= 0 && mode() == EscalatorMode.RUNNING) {
			riderCheckCooldown = RIDER_CHECK_TICKS;
			if (hasRider(level)) {
				lastContactTick = now;
			}
		}
		long cost = milliEuPerTick();
		boolean powered = energy.getAmount() * 1000L - milliEuOwed >= cost;
		EscalatorMode target;
		if (!chain.formed() || level.hasNeighborSignal(pos) || !powered) {
			target = EscalatorMode.OFF;
		} else if (now - lastContactTick <= EscalatorConfig.escalatorIdleTicks) {
			target = EscalatorMode.RUNNING;
		} else {
			target = EscalatorMode.READY;
		}
		if (target == EscalatorMode.RUNNING) {
			pay(cost);
			carryItems(level);
		} else {
			recordEuRate(0);
		}
		setMode(level, pos, state, target);
		// Moving: every tick. Stopped or asleep: the idle gate, woken by a rider, a scan request or energy arriving.
		return target == EscalatorMode.RUNNING ? 0 : IDLE_SLEEP_TICKS;
	}

	private void pay(long milliEu) {
		milliEuOwed += milliEu;
		long whole = milliEuOwed / 1000L;
		if (whole > 0) {
			long taken = energy.drainInternal(whole);
			milliEuOwed -= taken * 1000L;
			setChanged();
		}
		recordEuRate((int) Math.min(Integer.MAX_VALUE, (milliEu + 999L) / 1000L));
	}

	/** Whether anything the band would carry stands on one of this chain's sections. */
	private boolean hasRider(Level level) {
		List<BlockPos> sections = chain.sectionPositions(maxSections());
		AABB box = chainBox();
		if (box == null) {
			return false;
		}
		return !level.getEntities((Entity) null, box, entity -> {
			if (!EscalatorCarry.rides(entity)) {
				return false;
			}
			BlockPos owner = EscalatorCarry.ownerCell(level, entity);
			return owner != null && sections.contains(owner);
		}).isEmpty();
	}

	/** Push the dropped items on the band (see {@link EscalatorCarry#carryItem}); an item rides like anyone else. */
	private void carryItems(Level level) {
		AABB box = chainBox();
		if (box == null) {
			return;
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
			if (EscalatorCarry.carryItem(level, item)) {
				lastContactTick = level.getGameTime();
			}
		}
	}

	/** The cells of the chain and the two above each, as one box; {@code null} with no chain. */
	private @Nullable AABB chainBox() {
		List<BlockPos> sections = chain.sectionPositions(maxSections());
		if (sections.isEmpty()) {
			return null;
		}
		BlockPos first = sections.get(0);
		BlockPos last = sections.get(sections.size() - 1);
		return new AABB(Math.min(first.getX(), last.getX()), first.getY(), Math.min(first.getZ(), last.getZ()),
				Math.max(first.getX(), last.getX()) + 1, last.getY() + 2, Math.max(first.getZ(), last.getZ()) + 1);
	}

	/** Keep the landing's striped edge facing down the band. */
	private void alignLanding(Level level) {
		if (!chain.formed() || chain.bottom() == null) {
			return;
		}
		BlockPos at = EscalatorChain.landingPos(chain.bottom(), chain.facing(), chain.sections());
		BlockState landing = level.getBlockState(at);
		if (landing.getBlock() instanceof EscalatorLandingBlock
				&& landing.getValue(EscalatorLandingBlock.FACING) != chain.facing()) {
			level.setBlock(at, landing.setValue(EscalatorLandingBlock.FACING, chain.facing()), Block.UPDATE_CLIENTS);
		}
	}

	private void setMode(Level level, BlockPos pos, BlockState state, EscalatorMode mode) {
		if (state.getValue(EscalatorDriveBlock.MODE) != mode) {
			level.setBlock(pos, state.setValue(EscalatorDriveBlock.MODE, mode), Block.UPDATE_CLIENTS);
		}
		applyMode(level, chain, chain.formed() ? mode : EscalatorMode.OFF, reversed, mv());
	}

	/**
	 * Write {@code mode}, the direction and the tier onto both halves of every section of {@code chain} whose state
	 * differs.
	 */
	public static void applyMode(Level level, EscalatorChain chain, EscalatorMode mode, boolean reversed,
			boolean fast) {
		for (BlockPos at : chain.sectionPositions(EscalatorSectionBlock.WALK_LIMIT)) {
			writeSection(level, at, mode, reversed, fast);
		}
	}

	/**
	 * Write {@code mode} (and the direction and tier when given) onto the section whose lower half is {@code lower}
	 * and onto its balustrade half, each only when it differs; {@code UPDATE_CLIENTS}, no neighbour updates.
	 */
	public static void writeSection(Level level, BlockPos lower, EscalatorMode mode, @Nullable Boolean reversed,
			@Nullable Boolean fast) {
		for (BlockPos at : new BlockPos[] {lower, lower.above()}) {
			BlockState section = level.getBlockState(at);
			if (!(section.getBlock() instanceof EscalatorSectionBlock)) {
				continue;
			}
			BlockState next = section.setValue(EscalatorSectionBlock.MODE, mode);
			if (reversed != null) {
				next = next.setValue(EscalatorSectionBlock.REVERSED, reversed);
			}
			if (fast != null) {
				next = next.setValue(EscalatorSectionBlock.FAST, fast);
			}
			if (next != section) {
				level.setBlock(at, next, Block.UPDATE_CLIENTS);
			}
		}
	}

	// --- energy contract ---

	/** Intake on every face; the drive gives nothing back. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return EnergyRole.IN;
	}

	/** What the band draws per tick while it moves, whole EU (MOD-789, ADR-049). */
	@Override
	public long perTickDemand() {
		return (milliEuPerTick() + 999L) / 1000L;
	}

	// --- persistence ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("Reversed", reversed);
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		reversed = input.getBooleanOr("Reversed", false);
	}

	// --- what the player reads ---

	/** The empty-hand status line: running or ready with length and direction, or why the band stands. */
	public Component statusMessage() {
		String prefix = "message.alaindustrial.escalator.";
		if (!chain.formed()) {
			return chain.problem() == EscalatorChain.Problem.TOO_LONG
					? Component.translatable(prefix + "too_long", maxSections())
					: Component.translatable(prefix + chain.problem().key);
		}
		if (level != null && level.hasNeighborSignal(worldPosition)) {
			return Component.translatable(prefix + "redstone");
		}
		if (mode() == EscalatorMode.OFF) {
			return Component.translatable(prefix + "no_energy");
		}
		String state = mode() == EscalatorMode.RUNNING ? "running" : "ready";
		return Component.translatable(prefix + state, chain.sections(), directionName());
	}

	/** The line a wrench click shows. */
	public Component directionMessage() {
		return Component.translatable("message.alaindustrial.escalator.direction", directionName());
	}

	private Component directionName() {
		return Component.translatable("message.alaindustrial.escalator." + (reversed ? "down" : "up"));
	}

	// --- client animation ---

	/**
	 * Client tick, from the drive's own ticker. The drawn band's travel (blocks along the run) winds up towards the
	 * band's speed and brakes onto the still treads' places ({@link EscalatorBandMotion}); a clock-based travel would
	 * jump whenever the speed changes. Everything comes from the sections' block states.
	 */
	public void clientTick(Level clientLevel, BlockState state) {
		if (--clientScanCooldown <= 0) {
			clientScanCooldown = 10;
			clientChain = EscalatorChain.scan(clientLevel, worldPosition, EscalatorSectionBlock.WALK_LIMIT);
		}
		float target = 0.0F;
		if (clientChain.bottom() != null) {
			BlockState bottom = clientLevel.getBlockState(clientChain.bottom());
			if (bottom.getBlock() instanceof EscalatorSectionBlock
					&& bottom.getValue(EscalatorSectionBlock.MODE) == EscalatorMode.RUNNING) {
				float speed = (float) EscalatorCarry.speed(bottom);
				target = bottom.getValue(EscalatorSectionBlock.REVERSED) ? -speed : speed;
			}
		}
		band.tick(target);
	}

	/** The drawn band's travel at {@code partialTicks}, blocks along the run, in {@code [0, 1)} give or take a tick. */
	public float travel(float partialTicks) {
		return band.travel(partialTicks);
	}

	/** The chain as this client sees it. */
	public EscalatorChain clientChain() {
		return clientChain;
	}
}
