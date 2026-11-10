package dev.alaindustrial.gametest;

import dev.alaindustrial.attachment.PlayerDataCarryOver;
import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.registry.ModPlayerAttachments.PlayerAttachmentDef;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.skill.SkillStore;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * MOD-962: a player's mod data survives opening the world with another loader, on every lane.
 *
 * <p><b>What runs is production.</b> A save is {@code ServerPlayer.saveWithoutId} (the loader's own key and, through
 * the common mixin, the neutral copy), a load is {@code ServerPlayer.load} (the loader's own key, read by the loader,
 * and the mixin's read), and the join is {@link PlayerDataCarryOver#applyOnJoin} — the
 * {@code ServerHookRoster.PLAYER_JOIN} entry; the whole join list is not run because its other hooks send mod payloads,
 * which NeoForge refuses for a mock player. The stores are read through {@code PlayerStatsStore} and
 * {@code SkillStore}. Mock players are not ticked and the join event never fires for them, so the steps are called
 * one by one.
 *
 * <p>The loaders' key shapes below are written out by hand, not taken from the production class: they are the
 * oracle (each verified by javap against the loader's jar), and on its own lane each shape is read by the loader
 * itself.
 */
public final class PlayerDataCarryOverScenarios {

	/** This class's world gametests for every lane (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PlayerDataCarryOverScenarios::neutralCopyAloneRestoresTheData,
						"mod962_player_data_neutral_copy_alone_restores_it"),
				RosterEntry.of(PlayerDataCarryOverScenarios::everyLoaderKeyIsRescued,
						"mod962_player_data_every_loader_key_is_rescued"),
				RosterEntry.of(PlayerDataCarryOverScenarios::neutralCopyBeatsAStaleOwnKey,
						"mod962_player_data_neutral_copy_beats_a_stale_own_key"));

		private Roster() {}
	}

	private PlayerDataCarryOverScenarios() {}

	/** The neutral copy's key (literal on purpose: the test does not read the production constant). */
	private static final String NEUTRAL = "alaindustrial:player_data";
	/** Every loader's top-level key in a player's tag. */
	private static final List<String> LOADER_KEYS = List.of("fabric:attachments", "neoforge:attachments", "ForgeCaps");

	/**
	 * @implements MOD-962 — a save writes the neutral copy, and a player loaded from a tag holding only that copy (no
	 *     loader's own key) has the data after joining.
	 * @covers R-PER-01
	 */
	public static void neutralCopyAloneRestoresTheData(GameTestHelper helper) {
		Map<PlayerAttachmentDef<?>, Object> values = progress();
		ServerPlayer source = AlaGameTestHelper.survivalPlayer(helper);
		setAll(source, values);
		CompoundTag saved = save(source);
		if (!(saved.get(NEUTRAL) instanceof CompoundTag neutral)) {
			helper.fail("the player save wrote no " + NEUTRAL + " compound: " + saved.keySet());
			return;
		}

		ServerPlayer fresh = AlaGameTestHelper.survivalPlayer(helper);
		CompoundTag tag = baseOf(fresh);
		tag.put(NEUTRAL, neutral.copy());
		load(fresh, tag);
		PlayerDataCarryOver.applyOnJoin(fresh);

		String problem = mismatch(fresh, values);
		if (problem != null) {
			helper.fail("from the neutral copy alone: " + problem);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-962 — a world moved before any save by a build with the neutral copy: the data under each
	 *     loader's own key is there after joining, whichever loader runs.
	 * @covers R-PER-01
	 */
	public static void everyLoaderKeyIsRescued(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		Map<PlayerAttachmentDef<?>, Object> values = progress();
		List<String> failures = new ArrayList<>();
		for (String loaderKey : LOADER_KEYS) {
			ServerPlayer fresh = AlaGameTestHelper.survivalPlayer(helper);
			CompoundTag tag = baseOf(fresh);
			putLoaderKey(tag, loaderKey, values, registries);
			load(fresh, tag);
			PlayerDataCarryOver.applyOnJoin(fresh);
			String problem = mismatch(fresh, values);
			if (problem != null) {
				failures.add(loaderKey + ": " + problem);
			}
		}
		if (!failures.isEmpty()) {
			helper.fail("data under a loader's key was not carried over: " + failures);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements MOD-962 — played on A, then on B, then back on A with A's old key still in the file: the newer
	 *     neutral copy wins over the stale own key (all three loaders' keys hold the stale value, so the own key is
	 *     stale on every lane), or the player's progress would roll back.
	 * @covers R-PER-01
	 */
	public static void neutralCopyBeatsAStaleOwnKey(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		Map<PlayerAttachmentDef<?>, Object> stale = earlyProgress();
		Map<PlayerAttachmentDef<?>, Object> newer = progress();

		ServerPlayer fresh = AlaGameTestHelper.survivalPlayer(helper);
		CompoundTag tag = baseOf(fresh);
		for (String loaderKey : LOADER_KEYS) {
			putLoaderKey(tag, loaderKey, stale, registries);
		}
		CompoundTag neutral = new CompoundTag();
		for (Map.Entry<PlayerAttachmentDef<?>, Object> entry : newer.entrySet()) {
			neutral.put(entry.getKey().id(), encode(registries, entry.getKey(), entry.getValue()));
		}
		tag.put(NEUTRAL, neutral);
		load(fresh, tag);
		PlayerDataCarryOver.applyOnJoin(fresh);

		String problem = mismatch(fresh, newer);
		if (problem != null) {
			helper.fail("the stale own key won over the newer neutral copy: " + problem);
			return;
		}
		// The next save must carry the newer value forward in the copy, not the stale one it replaced.
		if (!encode(registries, ModPlayerAttachments.PLAYER_STATS, newer.get(ModPlayerAttachments.PLAYER_STATS))
				.equals(save(fresh).getCompoundOrEmpty(NEUTRAL).get(ModPlayerAttachments.PLAYER_STATS.id()))) {
			helper.fail("the save after the join did not write the newer value into " + NEUTRAL);
			return;
		}
		helper.succeed();
	}

	/** Non-default values of every attachment. */
	private static Map<PlayerAttachmentDef<?>, Object> progress() {
		Map<Identifier, Long> byGenerator = new LinkedHashMap<>();
		byGenerator.put(Identifier.parse("alaindustrial:generator"), 4_000L);
		byGenerator.put(Identifier.parse("alaindustrial:solar_panel"), 250L);
		return all(new PlayerModStats(1_000L, 700L, 5, byGenerator, 360L, 55L),
				new PlayerSkills(SkillBuild.EMPTY.with(SkillBranch.ENERGY, SkillSlot.IN)
						.with(SkillBranch.ENERGY, SkillSlot.A1).with(SkillBranch.MECH, SkillSlot.IN)));
	}

	/** Other non-default values: what the player had earlier. */
	private static Map<PlayerAttachmentDef<?>, Object> earlyProgress() {
		return all(new PlayerModStats(10L, 7L, 1, Map.of(Identifier.parse("alaindustrial:generator"), 10L), 3L, 0L),
				new PlayerSkills(SkillBuild.EMPTY.with(SkillBranch.AGRO, SkillSlot.IN)));
	}

	/** One value per entry; fails loudly when an entry is added without a value here. */
	private static Map<PlayerAttachmentDef<?>, Object> all(PlayerModStats stats, PlayerSkills skills) {
		Map<PlayerAttachmentDef<?>, Object> values = new LinkedHashMap<>();
		values.put(ModPlayerAttachments.PLAYER_STATS, stats);
		values.put(ModPlayerAttachments.PLAYER_SKILLS, skills);
		if (!values.keySet().equals(new HashSet<>(ModPlayerAttachments.PLAYER_ATTACHMENTS))) {
			throw new IllegalStateException("PLAYER_ATTACHMENTS has an entry this test gives no value");
		}
		return values;
	}

	private static void setAll(ServerPlayer player, Map<PlayerAttachmentDef<?>, Object> values) {
		PlayerStatsStore.set(player, (PlayerModStats) values.get(ModPlayerAttachments.PLAYER_STATS));
		SkillStore.set(player, (PlayerSkills) values.get(ModPlayerAttachments.PLAYER_SKILLS));
	}

	/** The first store that does not hold its expected value, or null. */
	private static @Nullable String mismatch(ServerPlayer player, Map<PlayerAttachmentDef<?>, Object> values) {
		Object stats = PlayerStatsStore.get(player);
		if (!stats.equals(values.get(ModPlayerAttachments.PLAYER_STATS))) {
			return "player_stats is " + stats + ", expected " + values.get(ModPlayerAttachments.PLAYER_STATS);
		}
		Object skills = SkillStore.get(player);
		if (!skills.equals(values.get(ModPlayerAttachments.PLAYER_SKILLS))) {
			return "player_skills is " + skills + ", expected " + values.get(ModPlayerAttachments.PLAYER_SKILLS);
		}
		return null;
	}

	private static CompoundTag save(ServerPlayer player) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
				player.level().registryAccess());
		player.saveWithoutId(output);
		return output.buildResult();
	}

	private static void load(ServerPlayer player, CompoundTag tag) {
		player.load(TagValueInput.create(ProblemReporter.DISCARDING, player.level().registryAccess(), tag));
	}

	/** The player's own save with every carrier of mod data taken out: position, UUID and the rest stay its own. */
	private static CompoundTag baseOf(ServerPlayer player) {
		CompoundTag tag = save(player);
		LOADER_KEYS.forEach(tag::remove);
		tag.remove(NEUTRAL);
		return tag;
	}

	/** Writes the values the way the loader owning {@code loaderKey} does. */
	private static void putLoaderKey(CompoundTag tag, String loaderKey, Map<PlayerAttachmentDef<?>, Object> values,
			RegistryAccess registries) {
		CompoundTag entries = new CompoundTag();
		for (Map.Entry<PlayerAttachmentDef<?>, Object> entry : values.entrySet()) {
			Tag encoded = encode(registries, entry.getKey(), entry.getValue());
			switch (loaderKey) {
				// fabric:attachments → {"alaindustrial:<id>": codec}
				case "fabric:attachments" -> entries.put("alaindustrial:" + entry.getKey().id(), encoded);
				// neoforge:attachments → {"alaindustrial:<id>": the MapCodec's fields}
				case "neoforge:attachments" -> entries.put("alaindustrial:" + entry.getKey().id(),
						mapCodecFields(registries, entry.getKey(), entry.getValue()));
				// ForgeCaps → {"alaindustrial:player_attachments": {"<id>": codec}}
				case "ForgeCaps" -> entries.put(entry.getKey().id(), encoded);
				default -> throw new IllegalArgumentException(loaderKey);
			}
		}
		if (loaderKey.equals("ForgeCaps")) {
			CompoundTag caps = new CompoundTag();
			caps.put("alaindustrial:player_attachments", entries);
			tag.put(loaderKey, caps);
		} else {
			tag.put(loaderKey, entries);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> Tag encode(RegistryAccess registries, PlayerAttachmentDef<T> def, Object value) {
		return def.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), (T) value)
				.getOrThrow();
	}

	@SuppressWarnings("unchecked")
	private static <T> CompoundTag mapCodecFields(RegistryAccess registries, PlayerAttachmentDef<T> def,
			Object value) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.store(def.mapCodec(), (T) value);
		return output.buildResult();
	}
}
