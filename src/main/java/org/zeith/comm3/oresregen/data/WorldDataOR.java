package org.zeith.comm3.oresregen.data;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.zeith.comm3.oresregen.InfoOR;
import org.zeith.comm3.oresregen.OresRegenerationMod;
import org.zeith.comm3.oresregen.config.BaseConfigOR;
import org.zeith.comm3.oresregen.config.OreCluster;
import org.zeith.comm3.oresregen.config.OreDefinition;
import org.zeith.comm3.oresregen.config.SoilConfigOR;
import org.zeith.comm3.oresregen.gen.WorldGenMinableOR;

import java.util.Collection;

@Mod.EventBusSubscriber
public class WorldDataOR
		implements INBTSerializable<NBTTagList>
{
	@CapabilityInject(WorldDataOR.class)
	public static Capability<WorldDataOR> WORLD_DATA;

	public World world;

	public Object2IntArrayMap<IBlockState> storage = new Object2IntArrayMap<>();

	public WorldDataOR()
	{
	}

	public WorldDataOR(World world)
	{
		this.world = world;
	}

	public void add(IBlockState state, int amount)
	{
		storage.put(state, storage.getInt(state) + amount);
	}

	private void remove(IBlockState state, int amount)
	{
		storage.put(state, Math.max(storage.getInt(state) - amount, 0));
	}

	public void update()
	{
		if(world.getTotalWorldTime() % 40 == 0 && world instanceof WorldServer)
		{
			WorldServer ws = (WorldServer) world;
			Collection<Chunk> chunks = ws.getChunkProvider().getLoadedChunks();
			if(chunks.size() > 0)
				chunks.stream().skip(ws.rand.nextInt(chunks.size())).limit(2L).forEach(loaded -> ChunkDataOR.getData(loaded).regenerate());
		}

		if(!storage.isEmpty() && world.getTotalWorldTime() % 100 == 0)
		{
			IBlockState state = storage.keySet().stream().skip(world.rand.nextInt(storage.size())).findFirst().orElse(null);
			if(state != null)
			{
				OreCluster cluster = OreDefinition.getCluster(state);
				int count = storage.getInt(state);
				if(count >= cluster.max)
				{
					BlockPos pos = OresRegenerationMod.randomOrePositionGenerator.apply(world);
					if(pos != null)
					{
						pos = pos.add(0, 1 + world.rand.nextInt(cluster.maxY), 0);

						EntityPlayer closest = world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32, false);

						if(closest == null || closest.getDistanceSq(pos) >= BaseConfigOR.blockRegenDistFromPlayer * BaseConfigOR.blockRegenDistFromPlayer)
						{
							int generated = new WorldGenMinableOR(state, cluster.max, SoilConfigOR.SOILS::contains)
									.gen(world, world.rand, pos);
							if(generated > 0)
							{
								count -= generated;
								storage.put(state, count);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public NBTTagList serializeNBT()
	{
		NBTTagList nbt = new NBTTagList();

		storage.forEach((state, amount) ->
		{
			if(amount == 0) return;

			NBTTagCompound tag = new NBTTagCompound();
			tag.setString("Id", state.getBlock().getRegistryName().toString());
			byte meta = (byte) state.getBlock().getMetaFromState(state);
			if(meta != 0) tag.setByte("Data", meta);
			tag.setInteger("Count", amount);
			nbt.appendTag(tag);
		});

		return nbt;
	}

	@Override
	public void deserializeNBT(NBTTagList nbt)
	{
		storage.clear();
		for(int i = 0; i < nbt.tagCount(); ++i)
		{
			NBTTagCompound tag = nbt.getCompoundTagAt(i);
			Block blk = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(tag.getString("Id")));
			if(blk != null)
			{
				IBlockState state = blk.getStateFromMeta(tag.getByte("Data"));
				add(state, tag.getInteger("Count"));
			} else
				OresRegenerationMod.LOG.error("Failed to read ore by id " + tag.getString("Id") + "... Unregistered block?");
		}
	}

	public static void register()
	{
		CapabilityManager.INSTANCE.register(WorldDataOR.class, new Capability.IStorage<WorldDataOR>()
		{
			@Override
			public NBTBase writeNBT(Capability<WorldDataOR> capability, WorldDataOR instance, EnumFacing side)
			{
				return instance.serializeNBT();
			}

			@Override
			public void readNBT(Capability<WorldDataOR> capability, WorldDataOR instance, EnumFacing side, NBTBase nbt)
			{
				instance.deserializeNBT((NBTTagList) nbt);
			}
		}, WorldDataOR::new);
	}

	public static WorldDataOR getData(World world)
	{
		return world.getCapability(WORLD_DATA, null);
	}

	@SubscribeEvent
	public static void attachCaps(AttachCapabilitiesEvent<World> e)
	{
		e.addCapability(new ResourceLocation(InfoOR.MOD_ID, "data"), new WorldDataProvider(e.getObject()));
	}

	@SubscribeEvent
	public static void breakBlock(BlockEvent.BreakEvent e)
	{
		IBlockState state = e.getState();

		boolean soil = SoilConfigOR.SOILS.contains(state);
		boolean ore = OreDefinition.isOre(state);

		if(ore)
			getData(e.getWorld()).add(state, 1);

		if(soil || ore)
		{
			Chunk c = e.getWorld().getChunk(e.getPos());
			ChunkDataOR.getData(c).onBreak(e);
		}
	}

	@SubscribeEvent
	public static void placeBlock(BlockEvent.EntityPlaceEvent e)
	{
		IBlockState state = e.getPlacedBlock();

		boolean soil = SoilConfigOR.SOILS.contains(state);
		boolean ore = OreDefinition.isOre(state);

		if(ore)
			getData(e.getWorld()).remove(state, 1);

		if(soil || ore)
		{
			Chunk c = e.getWorld().getChunk(e.getPos());
			ChunkDataOR.getData(c).onPlace(e);
		}
	}

	@SubscribeEvent
	public static void worldTick(TickEvent.WorldTickEvent e)
	{
		if(e.phase != TickEvent.Phase.END)
			return;
		getData(e.world).update();
	}

	static class WorldDataProvider
			implements ICapabilityProvider, INBTSerializable<NBTTagList>
	{
		public final WorldDataOR data;

		public WorldDataProvider(World world)
		{
			this.data = new WorldDataOR(world);
		}

		@Override
		public boolean hasCapability(Capability<?> capability, EnumFacing facing)
		{
			return capability == WORLD_DATA;
		}

		@Override
		public <T> T getCapability(Capability<T> capability, EnumFacing facing)
		{
			return capability == WORLD_DATA ? (T) data : null;
		}

		@Override
		public NBTTagList serializeNBT()
		{
			return data.serializeNBT();
		}

		@Override
		public void deserializeNBT(NBTTagList nbt)
		{
			data.deserializeNBT(nbt);
		}
	}
}