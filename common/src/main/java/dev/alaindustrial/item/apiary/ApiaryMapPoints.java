package dev.alaindustrial.item.apiary;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The hive attachments an apiary map lists (MOD-923): up to {@link #MAX} positions with their dimension, in the order
 * they were written. Immutable — every edit returns a new list, so a stack's component is replaced, never mutated.
 *
 * <p>The cap is the owner's (round 3): a station serves up to eight hives, and one map holds them. It is a constant,
 * not a config knob, because the station's screen is laid out for exactly that many rows.
 */
public record ApiaryMapPoints(List<GlobalPos> points) {

	/** How many attachments one map holds. */
	public static final int MAX = 8;

	public static final ApiaryMapPoints EMPTY = new ApiaryMapPoints(List.of());

	public static final Codec<ApiaryMapPoints> CODEC = GlobalPos.CODEC.sizeLimitedListOf(MAX)
			.xmap(ApiaryMapPoints::new, ApiaryMapPoints::points);

	public static final StreamCodec<ByteBuf, ApiaryMapPoints> STREAM_CODEC = GlobalPos.STREAM_CODEC
			.apply(ByteBufCodecs.list(MAX)).map(ApiaryMapPoints::new, ApiaryMapPoints::points);

	public ApiaryMapPoints {
		points = List.copyOf(points);
	}

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public boolean isFull() {
		return points.size() >= MAX;
	}

	public boolean contains(GlobalPos pos) {
		return points.contains(pos);
	}

	public boolean isValidIndex(int index) {
		return index >= 0 && index < points.size();
	}

	public GlobalPos get(int index) {
		return points.get(index);
	}

	/** This list with {@code pos} appended; the same list when it is already there or the map is full. */
	public ApiaryMapPoints with(GlobalPos pos) {
		if (contains(pos) || isFull()) {
			return this;
		}
		List<GlobalPos> next = new ArrayList<>(points);
		next.add(pos);
		return new ApiaryMapPoints(next);
	}

	/** This list without {@code pos}; the same list when it is not there. */
	public ApiaryMapPoints without(GlobalPos pos) {
		int index = points.indexOf(pos);
		return index < 0 ? this : without(index);
	}

	/** This list without the row at {@code index}; the same list for an index out of range. */
	public ApiaryMapPoints without(int index) {
		if (!isValidIndex(index)) {
			return this;
		}
		List<GlobalPos> next = new ArrayList<>(points);
		next.remove(index);
		return new ApiaryMapPoints(next);
	}
}
