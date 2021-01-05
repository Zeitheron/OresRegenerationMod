package org.zeith.comm3.oresregen.config;

import com.zeitheron.hammercore.lib.zlib.json.JSONArray;
import com.zeitheron.hammercore.lib.zlib.json.JSONObject;
import com.zeitheron.hammercore.lib.zlib.json.JSONTokener;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import org.zeith.comm3.oresregen.OresRegenerationMod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class OreDefinition
{
	public static final Map<IBlockState, OreCluster> CLUSTER_MAP = new HashMap<>();
	public static final List<IBlockState> allowlist = new ArrayList<>();
	public static final List<IBlockState> blocklist = new ArrayList<>();

	public static void generateExampleJson(File file, Consumer<JSONArray> additional)
	{
		JSONArray arr = new JSONArray();

		JSONObject obj = new JSONObject();
		obj.put("#comment", "Block example of allowlisting. Since this is the example, it is disabled. Also note that 'disable' is optional.");
		obj.put("disable", true);
		obj.put("block", "minecraft:iron_ore");
		arr.put(obj);

		obj = new JSONObject();
		obj.put("#comment", "Block state example of allowlisting. Since this is the example, it is disabled. Also note that 'disable' is optional.");
		obj.put("#comment2", "You can supply only partial properties about the state, this way the search engine might add extra block states that fit your needs.");
		obj.put("disable", true);
		obj.put("block", "minecraft:wool");
		JSONObject props = new JSONObject();
		props.put("color", "purple");
		obj.put("properties", props);
		arr.put(obj);

		obj = new JSONObject();
		obj.put("#comment", "Block state example of allowlisting for multiple block states. Since this is the example, it is disabled. Also note that 'disable' is optional.");
		obj.put("#comment2", "You can supply only partial properties about the state, this way the search engine might add extra block states that fit your needs.");
		obj.put("disable", true);
		obj.put("block", "minecraft:wool");
		JSONArray propsArr = new JSONArray();
		props = new JSONObject();
		props.put("color", "blue");
		propsArr.put(props);
		props = new JSONObject();
		props.put("color", "cyan");
		propsArr.put(props);
		obj.put("properties", propsArr);
		arr.put(obj);

		if(additional != null)
			additional.accept(arr);

		try(FileOutputStream fos = new FileOutputStream(file))
		{
			fos.write(arr.toString(4).getBytes(StandardCharsets.UTF_8));
		} catch(IOException ioe)
		{
			OresRegenerationMod.LOG.error("Failed to create " + file.getName() + " file", ioe);
		}
	}

	private static void addStates(Block blk, JSONObject props, List<IBlockState> arr, String ctx)
	{
		if(props == null)
		{
			arr.addAll(blk.getBlockState().getValidStates());
			OresRegenerationMod.LOG.info("[LOAD] Added block state " + blk.getRegistryName() + "[*] to " + ctx);
		} else
		{
			Map<String, String> propsStr = new HashMap<>();
			for(String key : props.keySet())
				propsStr.put(key.toLowerCase(), props.getString(key).toLowerCase());

			BlockStateContainer ctr = blk.getBlockState();

			// An array list of all properties that we are willing to check for
			List<IProperty> ip = new ArrayList<>();
			ctr.getProperties()
					.stream()
					.filter(j -> propsStr.containsKey(j.getName().toLowerCase()))
					.forEach(ip::add);

			ctr.getValidStates()
					.stream()
					.filter(state ->
					{
						for(IProperty prop : ip)
							if(!prop.getName(state.getValue(prop)).equalsIgnoreCase(propsStr.get(prop.getName().toLowerCase())))
								return false;
						return true;
					})
					.peek(state -> OresRegenerationMod.LOG.info("[LOAD] Added block state " + state.getBlock().getRegistryName() + "[" + state.getBlock().getMetaFromState(state) + "] to " + ctx))
					.forEach(arr::add);
		}
	}

	public static List<IBlockState> parse(JSONArray array, String ctx)
	{
		OresRegenerationMod.LOG.info("[LOAD] Loading " + ctx + "...");
		long start = System.currentTimeMillis();

		List<IBlockState> arr = new ArrayList<>();
		for(int i = 0; i < array.length(); ++i)
		{
			JSONObject obj = array.getJSONObject(i);

			if(!obj.optBoolean("disable", false))
			{
				String blockId = obj.getString("block");
				Block blk = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
				if(blk != null)
				{
					Object props = obj.opt("properties");
					if(props instanceof JSONArray)
					{
						JSONArray a = (JSONArray) props;
						for(int j = 0; j < a.length(); ++j)
							addStates(blk, a.getJSONObject(j), arr, ctx);
					} else
						addStates(blk, (JSONObject) props, arr, ctx);
				} else
					OresRegenerationMod.LOG.warn("[LOAD] Warning! Found unregistered block '" + blockId + "' in " + ctx);
			} else
				OresRegenerationMod.LOG.debug("[LOAD] Skipping " + ctx + " entry " + obj.optString("block") + " since it is disabled.");
		}

		OresRegenerationMod.LOG.info("[LOAD] Load of " + ctx + " finished in " + (System.currentTimeMillis() - start) + " ms. Loaded " + arr.size() + " block states.");
		return arr;
	}

	public static void initialize(File allowlistFile, File blocklistFile)
	{
		OresRegenerationMod.LOG.info("Starting to load OreDefinition...");
		long start = System.currentTimeMillis();

		if(!allowlistFile.isFile()) generateExampleJson(allowlistFile, null);
		if(!blocklistFile.isFile()) generateExampleJson(blocklistFile, null);

		try(FileInputStream in = new FileInputStream(allowlistFile))
		{
			allowlist.addAll(parse((JSONArray) new JSONTokener(in).nextValue(), "allowlist"));
		} catch(IOException ioe)
		{
			OresRegenerationMod.LOG.error("[LOAD] Unable to read " + allowlistFile.getName() + " file", ioe);
		}

		try(FileInputStream in = new FileInputStream(blocklistFile))
		{
			blocklist.addAll(parse((JSONArray) new JSONTokener(in).nextValue(), "blocklist"));
		} catch(IOException ioe)
		{
			OresRegenerationMod.LOG.error("[LOAD] Unable to read " + blocklistFile.getName() + " file", ioe);
		}

		long startOD = System.currentTimeMillis();
		OresRegenerationMod.LOG.info("[LOAD] Starting to look through Ore Dictionary for potential ores...");
		AtomicBoolean needsSave = new AtomicBoolean(false);
		Set<Block> processed = new HashSet<>();
		for(String ore : OreDictionary.getOreNames())
			if(ore.startsWith("ore"))
			{
				AtomicReference<OreCluster> definedCluster = new AtomicReference<>();

				OreDictionary.getOres(ore).forEach(stack ->
				{
					Block blk = Block.getBlockFromItem(stack.getItem());
					if(!processed.contains(blk))
					{
						processed.add(blk);
						blk.getBlockState().getValidStates().forEach(state ->
						{
							if(!blocklist.contains(state))
							{
								if(definedCluster.get() == null)
								{
									definedCluster.set(new OreCluster(BaseConfigOR.clustersCfg.getCategory(ore), BaseConfigOR.defaultClusterSize));
									needsSave.set(true);
								}

								OresRegenerationMod.LOG.info("[LOAD-OreDictionary] Added " + blk.getRegistryName() + "[" + blk.getMetaFromState(state) + "] to ore allowlist!");
								allowlist.add(state);
								CLUSTER_MAP.put(state, definedCluster.get());
							} else
								OresRegenerationMod.LOG.info("[LOAD-OreDictionary] Skipped " + blk.getRegistryName() + "[" + blk.getMetaFromState(state) + "] since it is blocklisted!");
						});
					}
				});
			}
		if(needsSave.get() && BaseConfigOR.main.hasChanged())
			BaseConfigOR.main.save();
		OresRegenerationMod.LOG.info("[LOAD] Ore Dictionary lookup finished in " + (System.currentTimeMillis() - startOD));

		OresRegenerationMod.LOG.info("OreDefinition load finished in about " + (System.currentTimeMillis() - start) + " ms. Enjoy :3");
	}

	public static boolean isOre(IBlockState state)
	{
		return !blocklist.contains(state) && allowlist.contains(state);
	}

	public static OreCluster getCluster(IBlockState state)
	{
		return CLUSTER_MAP.getOrDefault(state, BaseConfigOR.defaultClusterSize);
	}
}