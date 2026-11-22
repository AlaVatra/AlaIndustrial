package dev.alaindustrial.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.compat.CriterionPlayer;
import java.util.Optional;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Fires when the player reaches one of the steps of the geothermal advancement chain (MOD-1001).
 *
 * <p>One trigger with a {@code milestone} field, like {@link ReactorMilestoneTrigger}: the three steps differ only
 * in which line raised them, and a registry id each would be three registrations for no gain.
 *
 * <p><b>Who gets credited.</b> A generator burns lava on its own tick with nobody necessarily near it. It records
 * the player who placed it, so the step goes to that owner, and only while they are online; see
 * {@code ModCriteria.fireGeothermalMilestone}.
 */
public class GeothermalMilestoneTrigger extends SimpleCriterionTrigger<GeothermalMilestoneTrigger.TriggerInstance> {

	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	/** Called with the step the player just reached. */
	public void trigger(ServerPlayer player, GeothermalMilestone milestone) {
		trigger(player, instance -> instance.matches(milestone));
	}

	public record TriggerInstance(
			Optional<Holder<LootItemCondition>> player,
			String milestone)
			implements SimpleCriterionTrigger.SimpleInstance {

		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
				CriterionPlayer.FIELD.forGetter(TriggerInstance::player),
				Codec.STRING.fieldOf("milestone").forGetter(TriggerInstance::milestone)
		).apply(i, TriggerInstance::new));

		boolean matches(GeothermalMilestone actual) {
			// An unreadable name never matches: a typo in a datapack leaves that advancement unearnable rather
			// than handing it out on the first lava to burn.
			return GeothermalMilestone.byId(milestone) == actual;
		}
	}
}
