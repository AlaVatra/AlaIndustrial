package dev.alaindustrial.item.wearable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The Jetpack Mk2's air-control boost (MOD-977) repeats vanilla's air acceleration as constants,
 * {@link JetpackMotion#AIR_SPEED} and {@link JetpackMotion#AIR_SPEED_SPRINTING}, because
 * {@code Player.getFlyingSpeed()} is protected. If a new game version changes that method, the boost settles at a
 * different multiple than {@code jetpackMk2HorizontalMultiplier}. This lane runs the game's own {@link Player}
 * class, so it reads the numbers that method loads instead of waiting for someone to run javap.
 *
 * <p>Read from the bytecode, not by calling the method: the lane's server loads no level, so no player can be
 * built here. The walking branch of {@code getFlyingSpeed} loads the sprinting value, then the walking one; the
 * creative-flight branch reads {@code Abilities} and loads no float constant.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class JetpackAirSpeedTest {

	@Test
	void airSpeedConstantsMatchTheGame() throws IOException {
		byte[] bytes;
		try (InputStream in = Player.class.getResourceAsStream("Player.class")) {
			assertNotNull(in, "cannot read Player.class from the game jar");
			bytes = in.readAllBytes();
		}
		ClassModel player = ClassFile.of().parse(bytes);
		MethodModel flyingSpeed = player.methods().stream()
				.filter(m -> m.methodName().equalsString("getFlyingSpeed") && m.methodType().equalsString("()F"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Player.getFlyingSpeed()F is gone: re-check the Mk2 boost"));
		List<Object> loaded = flyingSpeed.code().orElseThrow().elementStream()
				.filter(e -> e instanceof ConstantInstruction.LoadConstantInstruction)
				.map(e -> (Object) ((ConstantInstruction.LoadConstantInstruction) e).constantValue())
				.toList();
		assertEquals(List.of(JetpackMotion.AIR_SPEED_SPRINTING, JetpackMotion.AIR_SPEED), loaded,
				"Player.getFlyingSpeed() loads other air speeds (sprinting, walking): update JetpackMotion");
	}
}
