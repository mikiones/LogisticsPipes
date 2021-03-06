package logisticspipes.modules;

import java.util.Collection;
import java.util.Objects;

import logisticspipes.interfaces.IPipeServiceProvider;
import logisticspipes.interfaces.IWorldProvider;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.pathfinder.IPipeInformationProvider.ConnectionPipeType;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;

import net.minecraft.nbt.NBTTagCompound;

import network.rs485.logisticspipes.world.WorldCoordinatesWrapper;

//IHUDModuleHandler,
public class ModuleSatellite extends LogisticsModule {

	private final CoreRoutedPipe pipe;

	public ModuleSatellite(CoreRoutedPipe pipeItemsSatelliteLogistics) {
		pipe = pipeItemsSatelliteLogistics;
	}

	@Override
	public void registerHandler(IWorldProvider world, IPipeServiceProvider service) {}

	@Override
	public final int getX() {
		return pipe.getX();
	}

	@Override
	public final int getY() {
		return pipe.getY();
	}

	@Override
	public final int getZ() {
		return pipe.getZ();
	}

	private SinkReply _sinkReply = new SinkReply(FixedPriority.ItemSink, 0, true, false, 1, 0, null);

	@Override
	public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault, boolean includeInTransit,
			boolean forcePassive) {
		if (bestPriority > _sinkReply.fixedPriority.ordinal() || (bestPriority == _sinkReply.fixedPriority.ordinal() && bestCustomPriority >= _sinkReply.customPriority)) {
			return null;
		}
		return new SinkReply(_sinkReply, spaceFor(item, includeInTransit));
	}

	private int spaceFor(ItemIdentifier item, boolean includeInTransit) {
		WorldCoordinatesWrapper worldCoordinates = new WorldCoordinatesWrapper(pipe.container);

		int count = worldCoordinates.getConnectedAdjacentTileEntities(ConnectionPipeType.ITEM)
				.map(adjacent -> SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(adjacent))
				.filter(Objects::nonNull)
				.map(util -> util.roomForItem(item, 9999))
				.reduce(Integer::sum).orElse(0);

		if (includeInTransit) {
			count -= pipe.countOnRoute(item);
		}
		return count;
	}

	@Override
	public LogisticsModule getSubModule(int slot) {
		return null;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {}

	@Override
	public void tick() {}

	@Override
	public boolean hasGenericInterests() {
		return false;
	}

	@Override
	public Collection<ItemIdentifier> getSpecificInterests() {
		return pipe.getSpecificInterests();
	}

	@Override
	public boolean interestedInAttachedInventory() {
		return false;
		// when we are default we are interested in everything anyway, otherwise we're only interested in our filter.
	}

	@Override
	public boolean interestedInUndamagedID() {
		return false;
	}

	@Override
	public boolean recievePassive() {
		return false;
	}

}
