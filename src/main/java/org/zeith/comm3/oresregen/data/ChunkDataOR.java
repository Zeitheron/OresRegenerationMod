package org.zeith.comm3.oresregen.data;

import com.zeitheron.hammercore.utils.ListUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
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
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.zeith.comm3.oresregen.InfoOR;
import org.zeith.comm3.oresregen.OresRegenerationMod;
import org.zeith.comm3.oresregen.config.BaseConfigOR;
import org.zeith.comm3.oresregen.config.OreDefinition;
import org.zeith.comm3.oresregen.config.SoilConfigOR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber
public class ChunkDataOR
		implements INBTSerializable<NBTTagList>
{
	@CapabilityInject(ChunkDataOR.class)
	public static Capability<ChunkDataOR> CHUNK_DATA;

	public Chunk chunk;

	public static ThreadLocal<List<BlockPos>> TMP_POS = ThreadLocal.withInitial(ArrayList::new);

	public final List<BlockPos> brokenMinables = new ArrayList<>();
	public final Map<BlockPos, IBlockState> brokenMinableMap = new HashMap<>();

	public ChunkDataOR()
	{
	}

	public ChunkDataOR(Chunk chunk)
	{
		this.chunk = chunk;
	}

	public void regenerate()
	{
		if(!brokenMinables.isEmpty())
		{
			BlockPos pos = ListUtils.random(brokenMinables, chunk.getWorld().rand);

			if(!chunk.getWorld().isAirBlock(pos))
			{
				brokenMinables.remove(pos);
				brokenMinableMap.remove(pos);
				return;
			}

			EntityPlayer closest = chunk.getWorld().getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32, false);

			List<BlockPos> tmp = TMP_POS.get();
			tmp.add(pos);
			for(int i = 0; i < tmp.size() && tmp.size() <= 16; ++i)
			{
				for(EnumFacing ef : EnumFacing.VALUES)
				{
					BlockPos t = tmp.get(i).offset(ef);
					if(isBroken(t) && !tmp.contains(t))
						tmp.add(t);
				}
			}

			if(closest == null || closest.getDistanceSq(pos) >= BaseConfigOR.blockRegenDistFromPlayer * BaseConfigOR.blockRegenDistFromPlayer)
			{
				if(tmp.size() < 16)
				{
					tmp.clear();
					brokenMinables.remove(pos);
					IBlockState state = brokenMinableMap.remove(pos);
					if(state == null) state = SoilConfigOR.getSoil(chunk.getWorld());
					chunk.getWorld().setBlockState(pos, state);
				} else
				{
					tmp.clear();
				}
			}

			tmp.clear();
		}
	}

	public void onBreak(BlockEvent.BreakEvent e)
	{
		int curY = e.getPos().getY();
		int heightMapY = e.getWorld().getHeight(e.getPos()).getY();

		boolean ignoreHM = e.getWorld().provider.getDimensionType() != DimensionType.OVERWORLD;

		if(!isBroken(e.getPos()) && (ignoreHM || curY < heightMapY - 4))
		{
			brokenMinables.add(e.getPos());
			brokenMinableMap.put(e.getPos(), OreDefinition.isOre(e.getState()) ? SoilConfigOR.getSoil(e.getWorld()) : e.getState());
		}
	}

	public void onPlace(BlockEvent.EntityPlaceEvent e)
	{
		if(isBroken(e.getPos()))
		{
			brokenMinables.remove(e.getPos());
			brokenMinableMap.remove(e.getPos());
		}
	}

	private boolean isBroken(BlockPos pos)
	{
		return brokenMinables.contains(pos);
	}

	public static boolean isBroken(World world, BlockPos pos)
	{
		return getData(world.getChunk(pos)).isBroken(pos);
	}

	@Override
	public NBTTagList serializeNBT()
	{
		NBTTagList nbt = new NBTTagList();
		brokenMinables.forEach(pos ->
		{
			NBTTagCompound comp = new NBTTagCompound();
			if(brokenMinableMap.containsKey(pos))
			{
				IBlockState state = brokenMinableMap.get(pos);
				comp.setString("Id", state.getBlock().getRegistryName().toString());
				if(state.getBlock().getMetaFromState(state) != 0)
					comp.setByte("Data", (byte) state.getBlock().getMetaFromState(state));
			}
			comp.setLong("Pos", pos.toLong());
			nbt.appendTag(comp);
		});
		return nbt;
	}

	@Override
	public void deserializeNBT(NBTTagList nbt)
	{
		brokenMinables.clear();
		brokenMinableMap.clear();

		for(NBTBase base : nbt)
			if(base instanceof NBTTagCompound)
			{
				NBTTagCompound tag = (NBTTagCompound) base;
				BlockPos pos = BlockPos.fromLong(tag.getLong("Pos"));
				brokenMinables.add(pos);
				if(tag.hasKey("Id"))
				{
					Block blk = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(tag.getString("Id")));
					if(blk != null)
						brokenMinableMap.put(pos, blk.getStateFromMeta(tag.getByte("Data")));
					else
						OresRegenerationMod.LOG.error("Failed to read ore by id " + tag.getString("Id") + "... Unregistered block at {" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "}?");
				}
			}
	}

	public static void register()
	{
		CapabilityManager.INSTANCE.register(ChunkDataOR.class, new Capability.IStorage<ChunkDataOR>()
		{
			@Override
			public NBTBase writeNBT(Capability<ChunkDataOR> capability, ChunkDataOR instance, EnumFacing side)
			{
				return instance.serializeNBT();
			}

			@Override
			public void readNBT(Capability<ChunkDataOR> capability, ChunkDataOR instance, EnumFacing side, NBTBase nbt)
			{
				instance.deserializeNBT((NBTTagList) nbt);
			}
		}, ChunkDataOR::new);
	}

	public static ChunkDataOR getData(Chunk chunk)
	{
		return chunk.getCapability(CHUNK_DATA, null);
	}

	@SubscribeEvent
	public static void attachCaps(AttachCapabilitiesEvent<Chunk> e)
	{
		e.addCapability(new ResourceLocation(InfoOR.MOD_ID, "data"), new ChunkDataProvider(e.getObject()));
	}

	static class ChunkDataProvider
			implements ICapabilityProvider, INBTSerializable<NBTTagList>
	{
		public final ChunkDataOR data;

		public ChunkDataProvider(Chunk world)
		{
			this.data = new ChunkDataOR(world);
		}

		@Override
		public boolean hasCapability(Capability<?> capability, EnumFacing facing)
		{
			return capability == CHUNK_DATA;
		}

		@Override
		public <T> T getCapability(Capability<T> capability, EnumFacing facing)
		{
			return capability == CHUNK_DATA ? (T) data : null;
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