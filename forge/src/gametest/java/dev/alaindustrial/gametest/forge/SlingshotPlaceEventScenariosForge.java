package dev.alaindustrial.gametest.forge;

import dev.alaindustrial.entity.TorchProjectile;
import dev.alaindustrial.gametest.AlaGameTestHelper;
import dev.alaindustrial.registry.ModEntityTypes;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;

/**
 * The slingshot's torch against Forge's {@link BlockEvent.EntityPlaceEvent} (MOD-871 on Forge, MOD-962 stage 3), the
 * Forge twin of the NeoForge lane's {@code SlingshotPlaceEventScenarios}: the body is the same, only the listener
 * differs — on Forge a {@code Predicate} listener returning {@code true} cancels the event.
 *
 * <p>The event bus cannot drop a lambda listener, so one listener is installed for the whole run and cancels only
 * the cells a scenario arms.
 */
public final class SlingshotPlaceEventScenariosForge {

	private static final Set<BlockPos> ARMED = ConcurrentHashMap.newKeySet();
	private static final AtomicBoolean LISTENING = new AtomicBoolean();

	private SlingshotPlaceEventScenariosForge() {
	}

	/** A listener that cancels the place event refuses the torch: nothing stays in the cell and the torch drops. */
	public static void cancelledPlaceEventDropsTheTorch(GameTestHelper helper) {
		listen();
		BlockPos target = new BlockPos(3, 2, 3);
		BlockPos cell = target.above();
		helper.setBlock(target, Blocks.STONE);
		BlockPos armed = helper.absolutePos(cell).immutable();
		ARMED.add(armed);
		ServerLevel level = helper.getLevel();
		ServerPlayer shooter = AlaGameTestHelper.survivalPlayer(helper);
		shooter.setPos(helper.absoluteVec(new Vec3(7.5, 1.0, 7.5)));
		TorchProjectile torch = new TorchProjectile(level, shooter, new ItemStack(Items.TORCH));
		torch.setPos(helper.absoluteVec(Vec3.atCenterOf(cell.above())));
		torch.setDeltaMovement(new Vec3(0, -0.6, 0));
		level.addFreshEntity(torch);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getEntities(ModEntityTypes.TORCH_PROJECTILE.type()).isEmpty(),
					"a torch is still in flight");
			helper.assertTrue(helper.getBlockState(cell).isAir(),
					"the cell holds " + helper.getBlockState(cell) + " although a listener cancelled the placement");
			List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, cell, 3.0);
			int count = drops.stream().mapToInt(e -> e.getItem().is(Items.TORCH) ? e.getItem().getCount() : 0).sum();
			helper.assertTrue(count == 1, count + " torches dropped, expected exactly 1");
			ARMED.remove(armed);
		});
	}

	private static void listen() {
		if (LISTENING.compareAndSet(false, true)) {
			BlockEvent.EntityPlaceEvent.BUS.addListener((BlockEvent.EntityPlaceEvent event) ->
					ARMED.contains(event.getPos()) && event.getEntity() instanceof ServerPlayer
							&& event.getPlacedBlock().is(Blocks.TORCH));
		}
	}
}
