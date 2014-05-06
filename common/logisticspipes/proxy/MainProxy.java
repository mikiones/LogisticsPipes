package logisticspipes.proxy;

import java.io.File;
import java.util.EnumMap;
import java.util.WeakHashMap;

import logisticspipes.Configs;
import logisticspipes.LogisticsEventListener;
import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.crafting.FakePlayer;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.packets.pipe.ParticleFX;
import logisticspipes.pipefxhandlers.PipeFXRenderHandler;
import logisticspipes.proxy.interfaces.IProxy;
import logisticspipes.routing.debug.RoutingTableDebugUpdateThread;
import logisticspipes.ticks.RoutingTableUpdateThread;
import logisticspipes.utils.PlayerCollectionList;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.FMLOutboundHandler.OutboundTarget;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;

public class MainProxy {
	
	@SidedProxy(clientSide="logisticspipes.proxy.side.ClientProxy", serverSide="logisticspipes.proxy.side.ServerProxy")
	public static IProxy proxy;
	public static EnumMap<Side, FMLEmbeddedChannel> channels;
	
	private static WeakHashMap<Thread, Side> threadSideMap = new WeakHashMap<Thread, Side>();
	
	private static Side getEffectiveSide() {
		Thread thr = Thread.currentThread();
		if(threadSideMap.containsKey(thr)) {
			return threadSideMap.get(thr);
		}
		Side side = getEffectiveSide(thr);
		if(threadSideMap.size() > 50) {
			threadSideMap.clear();
		}
		threadSideMap.put(thr, side);
		return side;
	}
	
	private static Side getEffectiveSide(Thread thr) {
        if (thr.getName().equals("Server thread") || (thr instanceof RoutingTableUpdateThread) || (thr instanceof RoutingTableDebugUpdateThread))
        {
            return Side.SERVER;
        }
        if(SimpleServiceLocator.ccProxy != null && SimpleServiceLocator.ccProxy.isLuaThread(thr)) {
        	return Side.SERVER;
        }
        return Side.CLIENT;
    }
	
	public static boolean isClient(World world) {
		try{
			return world.isRemote;
		} catch(NullPointerException n) {
			LogisticsPipes.log.severe("isClient called with a null world - using slow thread based fallback");
			n.printStackTrace();
		}
		return isClient();
	}
	
	@Deprecated 
	/**
	 * isClient is slow, find a world and check isServer(world)
	 * @return
	 */
	public static boolean isClient() {
		return getEffectiveSide() == Side.CLIENT;
	}
	
	public static boolean isServer(World world) {
		try{
			return !world.isRemote;
		} catch(NullPointerException n) {
			LogisticsPipes.log.severe("isServer called with a null world - using slow thread based fallback");
			n.printStackTrace();
		}
		return isServer();
	}

	
	@Deprecated 
	/**
	 * isServer is slow, find a world and check isServer(world)
	 * @return
	 */
	public static boolean isServer() {
		return getEffectiveSide() == Side.SERVER;
	}

	public static World getClientMainWorld() {
		return proxy.getWorld();
	}
	
	public static int getDimensionForWorld(World world) {
		return proxy.getDimensionForWorld(world);
	}

	public static void createChannels() {
		channels = NetworkRegistry.INSTANCE.newChannel("LogisticsPipes", new PacketHandler());
	}

	public static void sendPacketToServer(ModernPacket packet) {
		if(packet.isCompressable() || needsToBeCompressed(packet)) {
			SimpleServiceLocator.clientBufferHandler.addPacketToCompressor(packet);
		} else {
			channels.get(Side.CLIENT).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(OutboundTarget.TOSERVER);
			channels.get(Side.CLIENT).writeOutbound(packet);
		}
	}

	public static void sendPacketToPlayer(ModernPacket packet, EntityPlayer player) {
		if(packet.isCompressable() || needsToBeCompressed(packet)) {
			SimpleServiceLocator.serverBufferHandler.addPacketToCompressor(packet, player);
		} else {
			channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.PLAYER);
			channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(player);
			channels.get(Side.SERVER).writeOutbound(packet);
		}
	}

	public static void sendPacketToAllWatchingChunk(int X, int Z, int dimensionId, ModernPacket packet) {
		ChunkCoordIntPair chunk = new ChunkCoordIntPair(X >> 4, Z >> 4);
		PlayerCollectionList players = LogisticsEventListener.watcherList.get(chunk);
		if(players != null) {
			for(EntityPlayer player:players.players()) {
				if(MainProxy.getDimensionForWorld(player.worldObj) == dimensionId) {
					sendPacketToPlayer(packet, player);
				}
			}
			return;
		}
	}
	
	public static void sendToPlayerList(ModernPacket packet, PlayerCollectionList players) {
		if(players.isEmpty()) return;
		if(packet.isCompressable() || needsToBeCompressed(packet)) {
			for(EntityPlayer player:players.players()) {
				SimpleServiceLocator.serverBufferHandler.addPacketToCompressor(packet, player);
			}
		} else {
			for(EntityPlayer player:players.players()) {
				sendPacketToPlayer(packet, player);
			}
		}
	}

	public static void sendToAllPlayers(ModernPacket packet) {
		if(packet.isCompressable() || needsToBeCompressed(packet)) {
			for(World world: DimensionManager.getWorlds()) {
				for(Object playerObject:world.playerEntities) {
					EntityPlayer player = (EntityPlayer) playerObject;
					SimpleServiceLocator.serverBufferHandler.addPacketToCompressor(packet, player);
				}
			}
		} else {
			channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALL);
			channels.get(Side.SERVER).writeOutbound(packet);
		}
	}

	private static boolean needsToBeCompressed(ModernPacket packet) {
		if(packet.getData() != null) {
			if(packet.getData().length > 32767) {
				return true; // Packet is to big
			}
		}
		return false;
	}

	public static void sendSpawnParticlePacket(int particle, int xCoord, int yCoord, int zCoord, World dimension, int amount) {
		if(!Configs.ENABLE_PARTICLE_FX) return;
		if(MainProxy.isServer(dimension)) {
			MainProxy.sendPacketToAllWatchingChunk(xCoord, zCoord, MainProxy.getDimensionForWorld(dimension), PacketHandler.getPacket(ParticleFX.class).setInteger2(amount).setInteger(particle).setPosX(xCoord).setPosY(yCoord).setPosZ(zCoord));
		} else {
			LogisticsPipes.log.severe("Server only method on Client (Particle Spawning)");
		}
	}
	
	public static void spawnParticle(int particle, int xCoord, int yCoord, int zCoord, int amount) {
		if(!Configs.ENABLE_PARTICLE_FX || !Minecraft.isFancyGraphicsEnabled()) return;
		PipeFXRenderHandler.spawnGenericParticle(particle, xCoord, yCoord, zCoord, amount);
	}
	
	public static EntityPlayer getFakePlayer(TileEntity tile) {
		return new FakePlayer(tile);
	}

	public static File getLPFolder() {
		return new File(DimensionManager.getCurrentSaveRootDirectory(), "LogisticsPipes");
	}
}

