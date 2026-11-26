package dev.alaindustrial.core.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-1026: the role a foreign mod's {@code EnergyHandler} gets on NeoForge. Its interface has no "can it ever take /
 * give" predicate (Fabric's Team Reborn storage and Forge's {@code IEnergyStorage} have one), so every foreign
 * handler was filed as both producer and consumer: a machine that only takes energy was a generator of the line too.
 *
 * <p>The role now comes from a dry run in a nested, aborted transaction, and a direction is ruled out only where the
 * handler would have had to answer yes if it had it: it holds energy and gives none, it has room and takes none.
 * A handler that cannot be told — empty, or full — keeps both roles, as before.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class ForeignEnergyRoleTest {

	@Test
	void aMachineThatOnlyTakesIsNoGenerator(MinecraftServer server) {
		assertNotNull(server);
		EnergyPort port = NeoForgeEnergyLookup.foreign(new SimpleEnergyHandler(1000, 100, 0, 400));
		assertTrue(port.supportsInsertion(), "it takes energy");
		assertFalse(port.supportsExtraction(), "it holds 400 and gives none: it is no generator");
	}

	@Test
	void aGeneratorThatOnlyGivesIsNoConsumer(MinecraftServer server) {
		assertNotNull(server);
		EnergyPort port = NeoForgeEnergyLookup.foreign(new SimpleEnergyHandler(1000, 0, 100, 400));
		assertTrue(port.supportsExtraction(), "it gives energy");
		assertFalse(port.supportsInsertion(), "it has room and takes none: it is no consumer");
	}

	@Test
	void aBatteryIsBothAndAHandlerThatCannotBeToldStaysBoth(MinecraftServer server) {
		assertNotNull(server);
		EnergyPort battery = NeoForgeEnergyLookup.foreign(new SimpleEnergyHandler(1000, 100, 100, 400));
		assertTrue(battery.supportsInsertion() && battery.supportsExtraction(), "a battery takes and gives");
		EnergyPort emptyMachine = NeoForgeEnergyLookup.foreign(new SimpleEnergyHandler(1000, 100, 0, 0));
		assertTrue(emptyMachine.supportsInsertion() && emptyMachine.supportsExtraction(),
				"an empty machine cannot show it gives nothing: both, as before");
		EnergyPort fullGenerator = NeoForgeEnergyLookup.foreign(new SimpleEnergyHandler(1000, 0, 100, 1000));
		assertTrue(fullGenerator.supportsInsertion() && fullGenerator.supportsExtraction(),
				"a full generator cannot show it takes nothing: both, as before");
	}

	/**
	 * A battery read while it has already given its per-tick limit looks like a pure consumer. That answer is for
	 * the moment only: the port says its role is provisional, so the network probes it again on a clock
	 * ({@code ProvisionalRoleClock}), and the next read, once the battery gives again, is a battery. Before the fix
	 * the first answer stood until the cables changed, and a network whose only source was that battery slept for
	 * good.
	 */
	@Test
	void aRoleReadAtAWrongMomentIsProvisionalAndAReadLaterWidensIt(MinecraftServer server) {
		assertNotNull(server);
		ToggledBattery battery = new ToggledBattery();
		battery.spent(true);
		EnergyPort spent = NeoForgeEnergyLookup.foreign(battery);
		assertTrue(spent.supportsInsertion(), "a spent battery still takes");
		assertFalse(spent.supportsExtraction(), "a spent battery gives nothing this tick");
		assertTrue(spent.roleIsProvisional(), "the narrowed role is a guess from one try and must be probed again");

		battery.spent(false);
		EnergyPort later = NeoForgeEnergyLookup.foreign(battery);
		assertTrue(later.supportsInsertion() && later.supportsExtraction(), "probed again, the battery gives again");
		assertTrue(later.roleIsProvisional(), "every role read by a try stays provisional");

		EnergyPort machine = NeoForgeEnergyLookup.foreign(new SimpleEnergyHandler(1000, 100, 0, 400));
		assertTrue(machine.supportsInsertion() && !machine.supportsExtraction(),
				"a pure consumer is still read as one at every probe, so it never keeps the network awake");
	}

	/** A foreign battery whose extraction can be switched off, as when it has spent its per-tick limit. */
	private static final class ToggledBattery extends SimpleEnergyHandler {
		ToggledBattery() {
			super(1000, 100, 100, 400);
		}

		void spent(boolean spent) {
			maxExtract = spent ? 0 : 100;
		}
	}

	@Test
	void theDryRunInsideAnOpenTransactionChangesNothing(MinecraftServer server) {
		assertNotNull(server);
		SimpleEnergyHandler machine = new SimpleEnergyHandler(1000, 100, 0, 400);
		try (Transaction outer = Transaction.openRoot()) {
			EnergyPort port = NeoForgeEnergyLookup.foreign(machine);
			assertFalse(port.supportsExtraction());
			assertEquals(10, machine.insert(10, outer), "the outer transaction still works after the dry run");
			outer.commit();
		}
		assertEquals(410, machine.getAmountAsLong(), "only the outer insert of 10 stuck; the dry run left nothing");
	}
}
