package org.zeith.comm3.oresregen.config;

import com.zeitheron.hammercore.lib.zlib.json.JSONArray;
import com.zeitheron.hammercore.lib.zlib.json.JSONObject;
import com.zeitheron.hammercore.lib.zlib.json.JSONTokener;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.zeith.comm3.oresregen.OresRegenerationMod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SoilConfigOR
{
	public static final List<IBlockState> SOILS = new ArrayList<>();
	public static final Map<String, IBlockState> DIMENSION_SOILS = new HashMap<>();

	public static void initDimensionSoils(File file)
	{
		if(!file.isFile())
		{
			try(FileOutputStream fos = new FileOutputStream(file))
			{
				JSONObject obj = new JSONObject();
				obj.put(DimensionType.OVERWORLD.getName(), Blocks.STONE.getRegistryName().toString());
				obj.put(DimensionType.NETHER.getName(), Blocks.NETHERRACK.getRegistryName().toString());
				obj.put(DimensionType.THE_END.getName(), Blocks.END_STONE.getRegistryName().toString());
				fos.write(obj.toString(4).getBytes(StandardCharsets.UTF_8));
			} catch(IOException ioe)
			{
				OresRegenerationMod.LOG.error("Failed to create " + file.getName() + " file", ioe);
			}
		}

		try(FileInputStream in = new FileInputStream(file))
		{
			JSONObject obj = (JSONObject) new JSONTokener(in).nextValue();
			for(String dim : obj.keySet())
			{
				Block blk = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(obj.getString(dim)));
				if(blk != null) DIMENSION_SOILS.put(dim, blk.getDefaultState());
				else
					OresRegenerationMod.LOG.error("Failed to get unregistered soil block for dimension " + dim + ". Block: " + obj.getString(dim));
			}
		} catch(IOException ioe)
		{
			OresRegenerationMod.LOG.error("[LOAD] Unable to read " + file.getName() + " file", ioe);
		}
	}

	public static void initSoils(File file)
	{
		if(!file.isFile()) OreDefinition.generateExampleJson(file, arr ->
		{
			JSONObject obj = new JSONObject();
			obj.put("block", Blocks.STONE.getRegistryName().toString());
			JSONArray props = new JSONArray();
			JSONObject propsSub = new JSONObject();
			propsSub.put("variant", "stone");
			props.put(propsSub);
			propsSub = new JSONObject();
			propsSub.put("variant", "granite");
			props.put(propsSub);
			propsSub = new JSONObject();
			propsSub.put("variant", "andesite");
			props.put(propsSub);
			propsSub = new JSONObject();
			propsSub.put("variant", "diorite");
			props.put(propsSub);
			obj.put("properties", props);
			arr.put(obj);

			obj = new JSONObject();
			obj.put("block", Blocks.DIRT.getRegistryName().toString());
			arr.put(obj);

			obj = new JSONObject();
			obj.put("block", Blocks.GRAVEL.getRegistryName().toString());
			arr.put(obj);

			obj = new JSONObject();
			obj.put("block", Blocks.NETHERRACK.getRegistryName().toString());
			arr.put(obj);

			obj = new JSONObject();
			obj.put("block", Blocks.END_STONE.getRegistryName().toString());
			arr.put(obj);
		});

		try(FileInputStream in = new FileInputStream(file))
		{
			SOILS.addAll(OreDefinition.parse((JSONArray) new JSONTokener(in).nextValue(), "soil list"));
		} catch(IOException ioe)
		{
			OresRegenerationMod.LOG.error("[LOAD] Unable to read " + file.getName() + " file", ioe);
		}
	}

	public static IBlockState getSoil(World world)
	{
		return DIMENSION_SOILS.getOrDefault(world.provider.getDimensionType().getName(), Blocks.STONE.getDefaultState());
	}
}
