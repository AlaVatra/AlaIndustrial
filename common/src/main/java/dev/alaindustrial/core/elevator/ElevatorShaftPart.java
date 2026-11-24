package dev.alaindustrial.core.elevator;

/**
 * One cell of an elevator's glass shaft (MOD-769): a shaft frame or a half of a landing station. The winch reads the
 * tube down from itself ({@code ElevatorShaft}) and the cabin runs inside it, so "is the tube whole" is a type test,
 * and a cell of the tube is free for the cabin whatever its walls are. It lives in the core, as
 * {@code ReactorCasingPart} does, so the block types implement it and nothing in the core names a block (ADR-039).
 */
public interface ElevatorShaftPart {
}
