package dev.alaindustrial.block.entity;

import dev.alaindustrial.advancement.GeothermalMilestone;
import dev.alaindustrial.advancement.GeothermalStation;
import dev.alaindustrial.registry.ModCriteria;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The geothermal chain's two steps that a generator's own tick raises (MOD-1001): "First Lava" and "Station". The
 * third, the million, is counted by the owner's career statistics ({@code ModCriteria.fireLavaMillionIfReached}).
 *
 * <p>Both go to the generator's owner while they are online, like the reactor's steps. A generator with no owner
 * (the {@code /ala demo} stand, a structure) credits nobody and joins nobody's station.
 */
final class GeothermalAdvancements {

	/** Every running generator of every owner; not saved (see {@link GeothermalStation}). */
	private static final GeothermalStation<GeothermalGeneratorBlockEntity> STATION = new GeothermalStation<>();

	/**
	 * Generators that already sent "First Lava" to an online owner since they loaded. A guard against asking the
	 * advancement system twenty times a second, not a record of the award: the award is kept per player by the
	 * advancement system, so this is not saved and a reload costs one more call.
	 */
	private static final Set<GeothermalGeneratorBlockEntity> FIRST_LAVA_SENT =
			Collections.newSetFromMap(new WeakHashMap<>());

	private GeothermalAdvancements() {
	}

	/** {@code generator} burnt lava into EU this tick. */
	static void lavaBurnt(ServerLevel level, GeothermalGeneratorBlockEntity generator) {
		UUID owner = generator.getOwner();
		if (owner == null) {
			return;
		}
		boolean online = level.getServer().getPlayerList().getPlayer(owner) != null;
		if (online && FIRST_LAVA_SENT.add(generator)) {
			ModCriteria.fireGeothermalMilestone(level, owner, GeothermalMilestone.FIRST_LAVA);
		}
		MinecraftServer server = level.getServer();
		int running = STATION.burnt(owner, generator, level.getGameTime(),
				be -> !be.isRemoved() && be.getLevel() != null && be.getLevel().getServer() == server
						&& owner.equals(be.getOwner()),
				GeothermalMilestone.STATION_SIZE);
		if (online && running >= GeothermalMilestone.STATION_SIZE) {
			ModCriteria.fireGeothermalMilestone(level, owner, GeothermalMilestone.STATION);
		}
	}
}
