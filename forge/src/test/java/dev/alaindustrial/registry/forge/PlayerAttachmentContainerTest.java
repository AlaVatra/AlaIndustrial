package dev.alaindustrial.registry.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments.PlayerAttachmentDef;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.stats.PlayerModStats;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The Forge player-attachment container without the Forge runtime (MOD-962, slice 2.5): built with no capability,
 * since a {@code CapabilityToken} resolves only inside Forge, and saved with an empty registry access.
 */
class PlayerAttachmentContainerTest {

	private static final PlayerModStats STATS = new PlayerModStats(1_200L, 800L, 3,
			Map.of(Identifier.fromNamespaceAndPath("alaindustrial", "generator"), 1_200L), 40L, 5L);
	private static final PlayerSkills SKILLS = PlayerSkills.EMPTY.with(SkillBranch.ENERGY, SkillSlot.IN);

	@Test
	void savedValuesLoadBackEqual() {
		PlayerAttachmentContainer saved = new PlayerAttachmentContainer(null);
		saved.set(ModPlayerAttachments.PLAYER_STATS, STATS);
		saved.set(ModPlayerAttachments.PLAYER_SKILLS, SKILLS);

		CompoundTag nbt = saved.serializeNBT(RegistryAccess.EMPTY);
		PlayerAttachmentContainer loaded = new PlayerAttachmentContainer(null);
		loaded.deserializeNBT(RegistryAccess.EMPTY, nbt);

		assertEquals(STATS, loaded.get(ModPlayerAttachments.PLAYER_STATS));
		assertEquals(SKILLS, loaded.get(ModPlayerAttachments.PLAYER_SKILLS));
	}

	@Test
	void aReadInstallsNothingAndSavesNothing() {
		PlayerAttachmentContainer container = new PlayerAttachmentContainer(null);
		assertNull(container.get(ModPlayerAttachments.PLAYER_STATS));
		assertNull(container.get(ModPlayerAttachments.PLAYER_SKILLS));

		CompoundTag nbt = container.serializeNBT(RegistryAccess.EMPTY);
		assertTrue(nbt.isEmpty(), "a player who never had an attachment must not get one in the file: " + nbt);
	}

	@Test
	void oneBrokenEntryDoesNotLoseTheOthers() {
		PlayerAttachmentContainer saved = new PlayerAttachmentContainer(null);
		saved.set(ModPlayerAttachments.PLAYER_SKILLS, SKILLS);
		CompoundTag nbt = saved.serializeNBT(RegistryAccess.EMPTY);
		nbt.put(ModPlayerAttachments.PLAYER_STATS.id(), StringTag.valueOf("not a compound"));

		PlayerAttachmentContainer loaded = new PlayerAttachmentContainer(null);
		loaded.deserializeNBT(RegistryAccess.EMPTY, nbt);

		assertNull(loaded.get(ModPlayerAttachments.PLAYER_STATS));
		assertEquals(SKILLS, loaded.get(ModPlayerAttachments.PLAYER_SKILLS));
	}

	@Test
	void loadingReplacesWhatWasThere() {
		PlayerAttachmentContainer container = new PlayerAttachmentContainer(null);
		container.set(ModPlayerAttachments.PLAYER_STATS, STATS);
		container.deserializeNBT(RegistryAccess.EMPTY, new CompoundTag());
		assertNull(container.get(ModPlayerAttachments.PLAYER_STATS));
	}

	@Test
	void copyRuleOfTheOtherTwoLoaders() {
		PlayerAttachmentDef<String> kept = def(true);
		PlayerAttachmentDef<String> lost = def(false);
		assertTrue(PlayerAttachmentContainer.shouldCopy(kept, true), "copyOnDeath survives a death");
		assertFalse(PlayerAttachmentContainer.shouldCopy(lost, true), "without copyOnDeath a death resets it");
		assertTrue(PlayerAttachmentContainer.shouldCopy(kept, false), "a return from the End copies everything");
		assertTrue(PlayerAttachmentContainer.shouldCopy(lost, false), "a return from the End copies everything");
	}

	@Test
	void deathCloneTakesOverTheCopyOnDeathEntries() {
		PlayerAttachmentContainer original = new PlayerAttachmentContainer(null);
		original.set(ModPlayerAttachments.PLAYER_STATS, STATS);
		original.set(ModPlayerAttachments.PLAYER_SKILLS, SKILLS);
		PlayerAttachmentContainer fresh = new PlayerAttachmentContainer(null);

		fresh.copyFrom(original, true);

		for (PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			Object expected = def.copyOnDeath() ? original.get(def) : null;
			assertSame(expected, fresh.get(def), def.id());
		}
	}

	@Test
	void anEmptyOriginalCopiesNothing() {
		PlayerAttachmentContainer fresh = new PlayerAttachmentContainer(null);
		fresh.copyFrom(new PlayerAttachmentContainer(null), false);
		assertTrue(fresh.serializeNBT(RegistryAccess.EMPTY).isEmpty());
	}

	private static PlayerAttachmentDef<String> def(boolean copyOnDeath) {
		return new PlayerAttachmentDef<>("test_" + copyOnDeath, "", com.mojang.serialization.Codec.STRING,
				com.mojang.serialization.Codec.STRING.fieldOf("value"),
				net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.cast(), copyOnDeath, false, accessor -> {
				}, supplier -> {
				});
	}
}
