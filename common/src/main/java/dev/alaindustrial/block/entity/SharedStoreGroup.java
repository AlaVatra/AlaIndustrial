package dev.alaindustrial.block.entity;

import dev.alaindustrial.core.energy.EnergyBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Touching blocks of one kind that share one store (MOD-764, generalised by MOD-811): the store is the sum of the
 * members' own buffers, and any member with a receiver beside it is an outlet that gathers the group's energy into
 * its own buffer, from where the cable network pulls it like from any generator. The piezo path and the row of
 * thermoelectric generators are two {@link Rules} of this one mechanism.
 *
 * <p><b>Not a transport.</b> Energy moves only between members' own buffers, with the untracked buffer writes, and
 * leaves the group only through an outlet's ordinary port — so the network sees one more generator, never a
 * conductor, and nothing is counted twice: what two outlets hand on is what the members made.
 *
 * <p><b>Never saved.</b> A group is a view of the world, rebuilt by breadth-first walk at most once per
 * {@link #REFRESH_TICKS} and whenever a member calls {@link #invalidate}. Each member keeps its own charge, so
 * breaking one loses only what that one held.
 *
 * <p><b>One object per group.</b> The walk hands the object it built to every member, and a member that already
 * holds a fresh group reuses it. That is what lets two outlets of one group spend one shared per-tick budget, and
 * it partitions a chain longer than the member cap: the walk does not take a member of another fresh group.
 */
public final class SharedStoreGroup {
	/** Ticks a group stays fresh; an outlet that appears without a block update is noticed within this. */
	public static final int REFRESH_TICKS = 40;
	/** Hard ceiling for the walk, whatever the operator's config says. */
	public static final int HARD_MAX_MEMBERS = 256;

	/** What the group is doing. */
	public enum Status {
		/** Has an outlet and room to store more. */
		OK,
		/** No receiver anywhere: energy is stored, nothing leaves. */
		NO_OUTLET,
		/** Every member is full. */
		FULL;

		public static Status byOrdinal(int ordinal) {
			Status[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OK;
		}
	}

	/** A block entity that pools its buffer with touching blocks of its own kind. */
	public interface Member {
		BlockPos getBlockPos();

		boolean isRemoved();

		EnergyBuffer getEnergyStorage();

		/** Wake the member's ticker: new energy is waiting to be handed on. */
		void wake();

		/** The group this member was last handed, fresh or not. */
		@Nullable SharedStoreGroup group();

		/** Which of its faces lead to a receiver, as the group's {@link Rules#outlets} probe answered. */
		void setOutletMask(int mask);

		/** Called by the walk on every member, with the group's totals at build time. */
		void joinGroup(SharedStoreGroup group, Status status, int size, int outlets, long stored, long capacity);
	}

	/** Where a member's receivers are: a bit mask of its own choosing, 0 for none. */
	@FunctionalInterface
	public interface OutletProbe {
		int mask(Level level, BlockPos pos);
	}

	/**
	 * What differs between two kinds of group.
	 *
	 * @param joinFaces the faces across which two members join
	 * @param memberType the block entity class every member is an instance of
	 * @param maxMembers the operator's cap on one group (capped again by {@link #HARD_MAX_MEMBERS})
	 * @param outputPerTick EU per tick the whole group may gather into its outlets, all outlets together
	 * @param outlets where a member's receivers are; another member must never count as one
	 */
	public record Rules(List<Direction> joinFaces, Class<? extends Member> memberType, IntSupplier maxMembers,
			IntSupplier outputPerTick, OutletProbe outlets) {
		public Rules {
			joinFaces = List.copyOf(joinFaces);
		}
	}

	private final Rules rules;
	private final List<Member> members;
	private final List<Member> outlets;
	private final long builtTick;
	private boolean invalid;
	private long budgetTick = Long.MIN_VALUE;
	private long budgetLeft;

	private SharedStoreGroup(Rules rules, List<Member> members, List<Member> outlets, long builtTick) {
		this.rules = rules;
		this.members = members;
		this.outlets = outlets;
		this.builtTick = builtTick;
	}

	/** The fresh group {@code member} belongs to, walking the world again only when the cached one went stale. */
	public static SharedStoreGroup of(Level level, Member member, Rules rules) {
		SharedStoreGroup cached = member.group();
		long now = level.getGameTime();
		if (cached != null && cached.rules == rules && cached.isFresh(now)) {
			return cached;
		}
		return build(level, member, rules, now);
	}

	private boolean isFresh(long now) {
		return !invalid && now - builtTick < REFRESH_TICKS && now >= builtTick;
	}

	/** Make every member rebuild on its next use — a member joined, left or got a new neighbour. */
	public void invalidate() {
		invalid = true;
	}

	private static SharedStoreGroup build(Level level, Member start, Rules rules, long now) {
		int cap = Math.min(HARD_MAX_MEMBERS, Math.max(1, rules.maxMembers().getAsInt()));
		List<Member> members = new ArrayList<>();
		Deque<Member> queue = new ArrayDeque<>();
		List<BlockPos> seen = new ArrayList<>();
		queue.add(start);
		seen.add(start.getBlockPos());
		while (!queue.isEmpty() && members.size() < cap) {
			Member member = queue.poll();
			members.add(member);
			for (Direction dir : rules.joinFaces()) {
				BlockPos next = member.getBlockPos().relative(dir);
				if (seen.contains(next) || !level.isLoaded(next)) {
					continue;
				}
				if (rules.memberType().isInstance(level.getBlockEntity(next))) {
					Member neighbour = (Member) level.getBlockEntity(next);
					if (!neighbour.isRemoved() && !belongsToOtherFreshGroup(neighbour, rules, now)) {
						seen.add(next);
						queue.add(neighbour);
					}
				}
			}
		}
		List<Member> outlets = new ArrayList<>();
		long stored = 0;
		long capacity = 0;
		for (Member member : members) {
			int mask = rules.outlets().mask(level, member.getBlockPos());
			member.setOutletMask(mask);
			if (mask != 0) {
				outlets.add(member);
			}
			stored += member.getEnergyStorage().getAmount();
			capacity += member.getEnergyStorage().getCapacity();
		}
		SharedStoreGroup group = new SharedStoreGroup(rules, List.copyOf(members), List.copyOf(outlets), now);
		Status status = outlets.isEmpty() ? Status.NO_OUTLET : stored >= capacity ? Status.FULL : Status.OK;
		for (Member member : members) {
			member.joinGroup(group, status, members.size(), outlets.size(), stored, capacity);
		}
		return group;
	}

	private static boolean belongsToOtherFreshGroup(Member member, Rules rules, long now) {
		SharedStoreGroup other = member.group();
		return other != null && other.rules == rules && other.isFresh(now) && !other.members.isEmpty();
	}

	/**
	 * Move up to {@code room} EU from the members that are not outlets into {@code outlet}, out of the group's
	 * per-tick budget. Plain internal moves on the server thread: a drain from one member and the same amount
	 * received by another is energy changing hands inside one store, not a transfer anything else could observe.
	 *
	 * <p>An outlet never drains another outlet: each one hands on its own energy plus what it gathered, so two
	 * outlets do not pass the same EU back and forth and the one that ticks last does not starve the other's line.
	 */
	public long gatherInto(Member outlet, long room, long now) {
		if (budgetTick != now) {
			budgetTick = now;
			budgetLeft = Math.max(0, rules.outputPerTick().getAsInt());
		}
		long want = Math.min(room, budgetLeft);
		long got = 0;
		for (Member member : members) {
			if (got >= want) {
				break;
			}
			if (member == outlet || member.isRemoved() || outlets.contains(member)) {
				continue;
			}
			got += member.getEnergyStorage().drainInternal(want - got);
		}
		long kept = outlet.getEnergyStorage().receiveInternal(got);
		budgetLeft -= kept;
		return kept;
	}

	/** Wake the outlets so new energy is handed on now rather than at their next idle check. */
	public void wakeOutlets() {
		for (Member outlet : outlets) {
			if (!outlet.isRemoved()) {
				outlet.wake();
			}
		}
	}

	public boolean isOutlet(Member member) {
		return outlets.contains(member);
	}

	public int size() {
		return members.size();
	}
}
