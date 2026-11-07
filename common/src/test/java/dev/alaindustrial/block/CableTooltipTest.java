package dev.alaindustrial.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Tier;
import dev.alaindustrial.core.tooltip.TooltipRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The cable grades' tooltip (MOD-716, ADR-040): the tier follows the grade's own voltage class and waits for
 * [SHIFT] (MOD-958), the safety line follows its sleeve, and the loss is the trimmed percentage the shipped tooltip
 * has always printed.
 */
class CableTooltipTest {

	private static List<String> keys(List<MachineTooltipSpec.Line> lines) {
		List<String> keys = new ArrayList<>();
		for (MachineTooltipSpec.Line line : lines) {
			keys.add(line.key());
		}
		return keys;
	}

	@Test
	void bareCopperIsLvWithAShockWarning() {
		MachineTooltipSpec spec = CableTooltip.of(CableType.COPPER);
		assertEquals(List.of("tooltip.alaindustrial.buffer", "tooltip.alaindustrial.cable_shock_warning",
				"tooltip.alaindustrial.hold_shift"), keys(spec.lines(false, true)));
		assertEquals(TooltipRole.WARNING, spec.basic().get(1).role());
		// The shock warning is a warning (MOD-958): it stands after the [SHIFT] details, never hidden by them.
		assertEquals(List.of("tooltip.alaindustrial.buffer", "tooltip.alaindustrial.tier_lv",
				"tooltip.alaindustrial.cable_loss", "tooltip.alaindustrial.cable_shock_warning",
				"tooltip.alaindustrial.hold_shift"),
				keys(spec.lines(true, true)));
		assertEquals(List.of("tooltip.alaindustrial.tier_lv", "tooltip.alaindustrial.cable_shock_warning",
				"tooltip.alaindustrial.hold_shift"), keys(spec.lines(true, false)));
		assertEquals(List.of("tooltip.alaindustrial.hold_shift"), keys(spec.lines(false, false)));
	}

	@Test
	void eachGradeNamesItsOwnTier() {
		assertEquals(Tier.LV, CableTooltip.of(CableType.COPPER).tier());
		assertEquals(Tier.MV, CableTooltip.of(CableType.GOLD).tier());
		assertEquals(Tier.HV, CableTooltip.of(CableType.INSULATED_ELECTRUM).tier());
		assertEquals(Tier.HV, CableTooltip.of(CableType.ELECTRUM).tier());
		assertEquals(TooltipRole.STATE_ON, CableTooltip.of(CableType.INSULATED_TIN).basic().get(1).role());
		assertEquals("tooltip.alaindustrial.cable_safe",
				CableTooltip.of(CableType.INSULATED_TIN).basic().get(1).key());
	}

	@Test
	void bufferAndLossComeFromTheGrade() {
		MachineTooltipSpec spec = CableTooltip.of(CableType.TIN);
		assertEquals(CableType.TIN.segmentBuffer(), spec.basic().get(0).argValues()[0]);
		assertEquals(CableTooltip.lossPercent(CableType.TIN), spec.detailed().get(0).argValues()[0]);
		assertEquals("tooltip.alaindustrial.cable_loss", spec.detailed().get(0).key());
	}

	@Test
	void lossIsTrimmed() {
		String copper = CableTooltip.lossPercent(CableType.COPPER);
		assertEquals(false, copper.endsWith("0") && copper.contains("."), copper);
		assertEquals(false, copper.endsWith("."), copper);
		assertEquals(CableType.COPPER.lossPerBlock() * 100.0, Double.parseDouble(copper), 1e-3);
	}

	/**
	 * MOD-761: on a dedicated server the tooltip shows the server's buffer and loss, not the player's own file —
	 * the grade picks among the received values exactly as the network picks among {@code Config}'s.
	 */
	@Test
	void theTooltipShowsTheServersNumbers() {
		try {
			ServerBalance.receive(KnobSnapshot.of(Map.of("goldCableBuffer", 777, "goldCableLossPerBlock", 0.25,
					"insulationLossMultiplier", 0.2)).encode());
			MachineTooltipSpec spec = CableTooltip.of(CableType.INSULATED_GOLD);
			assertEquals(777L, spec.basic().get(0).argValues()[0]);
			assertEquals("5", CableTooltip.lossPercent(CableType.INSULATED_GOLD));
		} finally {
			ServerBalance.reset();
		}
	}
}
