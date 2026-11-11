package dev.alaindustrial.core.forge;

import dev.alaindustrial.item.fluid.BucketFluids;
import dev.alaindustrial.item.fluid.ItemFluid;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * A one-bucket fluid container in an item, whole bucket or nothing, one item at a time — the shape of Forge's
 * {@code FluidBucketWrapper}. Two kinds, one class:
 * <ul>
 *   <li><b>Vanilla and our own buckets</b> ({@link #bucket}). Forge 26.3 ships the bucket's item capability
 *   commented out and {@code FluidUtil.getFluidHandler(ItemStack)} returns empty, so our bridge recognises
 *   buckets itself, through {@link BucketFluids} (a fish bucket is not a water bucket; our oil bucket is one).</li>
 *   <li><b>The vacuum capsule</b> ({@link #capsule}, MOD-063), attached to the capsule stacks as their
 *   {@code FLUID_HANDLER_ITEM}, the twin of NeoForge's {@code CapsuleResourceHandler}: any fluid, emptied to a
 *   component-free capsule that stacks with crafted ones. Forge's {@code FluidHandlerItemStack} is not in the
 *   universal jar, hence this class.</li>
 * </ul>
 * The handler works on the stack it was given and swaps it for the other item; the caller takes the result from
 * {@link #getContainer()}.
 */
final class BucketFluidHandlerItem implements IFluidHandlerItem {

	private final boolean capsule;
	private ItemStack container;

	private BucketFluidHandlerItem(ItemStack container, boolean capsule) {
		this.container = container;
		this.capsule = capsule;
	}

	static BucketFluidHandlerItem bucket(ItemStack container) {
		return new BucketFluidHandlerItem(container, false);
	}

	static BucketFluidHandlerItem capsule(ItemStack container) {
		return new BucketFluidHandlerItem(container, true);
	}

	/** Whether {@code stack} is a bucket this class can read: empty, or holding a fluid whose bucket it is. */
	static boolean isBucket(ItemStack stack) {
		return stack.is(Items.BUCKET) || BucketFluids.content(stack) != Fluids.EMPTY;
	}

	@Override
	public ItemStack getContainer() {
		return container;
	}

	@Override
	public int getTanks() {
		return 1;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		Fluid fluid = content();
		return fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, FluidType.BUCKET_VOLUME);
	}

	@Override
	public int getTankCapacity(int tank) {
		return FluidType.BUCKET_VOLUME;
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return !stack.isEmpty();
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if (container.getCount() != 1 || resource.isEmpty() || resource.getAmount() < FluidType.BUCKET_VOLUME
				|| !isEmptyContainer()) {
			return 0;
		}
		ItemStack filled = filled(resource.getFluid());
		if (filled.isEmpty()) {
			return 0;
		}
		if (action.execute()) {
			container = filled;
		}
		return FluidType.BUCKET_VOLUME;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if (resource.isEmpty() || resource.getFluid() != content()) {
			return FluidStack.EMPTY;
		}
		return drain(resource.getAmount(), action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		Fluid fluid = content();
		if (container.getCount() != 1 || fluid == Fluids.EMPTY || maxDrain < FluidType.BUCKET_VOLUME) {
			return FluidStack.EMPTY;
		}
		if (action.execute()) {
			container = capsule ? new ItemStack(ModContent.VACUUM_CAPSULE.get()) : new ItemStack(Items.BUCKET);
		}
		return new FluidStack(fluid, FluidType.BUCKET_VOLUME);
	}

	private Fluid content() {
		if (capsule) {
			return container.is(ModContent.FILLED_VACUUM_CAPSULE.get()) ? ItemFluid.get(container) : Fluids.EMPTY;
		}
		return BucketFluids.content(container);
	}

	private boolean isEmptyContainer() {
		return capsule ? container.is(ModContent.VACUUM_CAPSULE.get()) : container.is(Items.BUCKET);
	}

	private ItemStack filled(Fluid fluid) {
		if (!capsule) {
			return BucketFluids.filledBucket(fluid);
		}
		ItemStack filled = new ItemStack(ModContent.FILLED_VACUUM_CAPSULE.get());
		ItemFluid.set(filled, fluid);
		return filled;
	}
}
