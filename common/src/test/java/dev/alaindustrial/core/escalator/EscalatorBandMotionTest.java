package dev.alaindustrial.core.escalator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 tests of the drawn band's travel (MOD-969, owner's check 2026-11-24: "the band jerks back and forth when it
 * starts and stops"). The travel is sampled ten times per tick, as frames between ticks would read it, through
 * starts, stops and a restart while braking, at every phase the band can be in when the stop comes.
 *
 * @implements MOD-969-L1-BAND drawn band starts and stops without a jump
 */
class EscalatorBandMotionTest {

	private static final float LV = 0.10F;
	private static final float MV = 0.16F;
	private static final int FRAMES = 10;
	private static final float EPS = 1.0E-4F;

	/** Run {@code ticks} ticks towards {@code target}, appending the travel at every frame of every tick. */
	private static void run(EscalatorBandMotion band, float target, int ticks, List<Float> out) {
		for (int t = 0; t < ticks; t++) {
			band.tick(target);
			for (int f = 0; f < FRAMES; f++) {
				out.add(band.travel(f / (float) FRAMES));
			}
		}
	}

	/** The frame-to-frame steps with the one-block wrap taken out. */
	private static List<Float> steps(List<Float> frames) {
		List<Float> out = new ArrayList<>();
		for (int i = 1; i < frames.size(); i++) {
			float d = frames.get(i) - frames.get(i - 1);
			if (d > 0.5F) {
				d -= 1.0F;
			} else if (d < -0.5F) {
				d += 1.0F;
			}
			out.add(d);
		}
		return out;
	}

	/** Every step goes one way ({@code sign}) or stands, and none is longer than a frame at {@code speed}. */
	private static void assertSmooth(List<Float> frames, float sign, float speed, String what) {
		List<Float> steps = steps(frames);
		for (int i = 0; i < steps.size(); i++) {
			float d = steps.get(i) * sign;
			assertTrue(d >= -EPS, what + ": frame " + (i + 1) + " jumps back by " + (-d));
			assertTrue(d <= speed / FRAMES + EPS, what + ": frame " + (i + 1) + " jumps ahead by " + d);
		}
	}

	private static boolean onPitch(float travel) {
		float k = travel / EscalatorBandMotion.PITCH;
		return Math.abs(k - Math.round(k)) < 1.0E-4F;
	}

	@Test
	void startsFromRestWithoutAJump() {
		for (float speed : new float[] {LV, MV, -LV}) {
			EscalatorBandMotion band = new EscalatorBandMotion();
			List<Float> frames = new ArrayList<>();
			frames.add(band.travel(0.0F));
			run(band, speed, 80, frames);
			assertSmooth(frames, Math.signum(speed), Math.abs(speed), "start at " + speed);
			assertEquals(speed, band.speed(), Math.abs(speed) * 0.01F, "the band reaches its speed");
		}
	}

	@Test
	void stopsOnTheStillTreadsPlacesFromEveryPhase() {
		for (float speed : new float[] {LV, MV, -LV, -MV}) {
			for (int runFor = 1; runFor <= 60; runFor++) {
				EscalatorBandMotion band = new EscalatorBandMotion();
				List<Float> frames = new ArrayList<>();
				run(band, speed, runFor, frames);
				run(band, 0.0F, (int) EscalatorBandMotion.MAX_BRAKE_TICKS + 5, frames);
				String what = "stop after " + runFor + " ticks at " + speed;
				assertSmooth(frames, Math.signum(speed), Math.abs(speed), what);
				assertTrue(band.atRest(), what + ": the band must come to rest");
				assertTrue(onPitch(band.travel(0.0F)) && onPitch(band.travel(1.0F)),
						what + ": it must rest where the still treads stand, at " + band.travel(0.0F));
				// The last frames stand: no snap once it has stopped.
				List<Float> tail = steps(frames.subList(frames.size() - 3 * FRAMES, frames.size()));
				tail.forEach(d -> assertEquals(0.0F, d, EPS, what + ": a stopped band must not move"));
			}
		}
	}

	@Test
	void restartWhileBrakingCarriesOnTheSameWay() {
		for (int brakeFor = 1; brakeFor < 30; brakeFor += 3) {
			EscalatorBandMotion band = new EscalatorBandMotion();
			List<Float> frames = new ArrayList<>();
			run(band, LV, 37, frames);
			run(band, 0.0F, brakeFor, frames);
			run(band, LV, 40, frames);
			run(band, 0.0F, 50, frames);
			assertSmooth(frames, 1.0F, LV, "restart after " + brakeFor + " braking ticks");
			assertTrue(band.atRest() && onPitch(band.travel(1.0F)), "and rest on the pitch again");
		}
	}

	/**
	 * Negative control: the travel before this fix. The speed eased to zero and the band halted wherever the ease
	 * left it, while the still model draws the treads at travel 0; for most phases the two disagree, which is the
	 * jump the owner saw when the model came back.
	 */
	@Test
	void theOldEaseHaltedOffThePitch() {
		int offPitch = 0;
		for (int runFor = 1; runFor <= 60; runFor++) {
			float drawn = 0.0F;
			float travel = 0.0F;
			for (int t = 0; t < runFor + 80; t++) {
				float target = t < runFor ? LV : 0.0F;
				drawn += (target - drawn) * EscalatorBandMotion.EASE;
				if (target == 0.0F && Math.abs(drawn) < 1.0E-3F) {
					drawn = 0.0F;
				}
				travel = (travel + drawn) % 1.0F;
			}
			if (!onPitch(travel)) {
				offPitch++;
			}
		}
		assertTrue(offPitch > 50, "the old ease halts off the treads' places almost always, here " + offPitch);
	}
}
