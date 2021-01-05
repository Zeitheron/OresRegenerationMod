package org.zeith.comm3.oresregen;

import com.zeitheron.hammercore.HammerCore;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeith.comm3.oresregen.config.BaseConfigOR;
import org.zeith.comm3.oresregen.config.OreDefinition;
import org.zeith.comm3.oresregen.config.SoilConfigOR;
import org.zeith.comm3.oresregen.data.ChunkDataOR;
import org.zeith.comm3.oresregen.data.WorldDataOR;
import org.zeith.comm3.oresregen.proxy.CommonProxy;

import java.io.File;
import java.util.Map;
import java.util.function.Function;

@Mod(modid = InfoOR.MOD_ID, version = "@VERSION@", name = InfoOR.MOD_NAME, certificateFingerprint = HammerCore.CERTIFICATE_FINGERPRINT, updateJSON = "http://dccg.herokuapp.com/api/fmluc/433904", dependencies = "required-after:hammercore")
public class OresRegenerationMod
{
	@SidedProxy(serverSide = "org.zeith.comm3.oresregen.proxy.CommonProxy", clientSide = "org.zeith.comm3.oresregen.proxy.ClientProxy")
	public static CommonProxy proxy;

	@Mod.Instance
	public static OresRegenerationMod instance;

	public static final Logger LOG = LogManager.getLogger("OresRegeneration");

	public static File modCfg;

	public static Function<World, BlockPos> randomOrePositionGenerator = (world) ->
	{
		BlockPos pos = world.getSpawnPoint();
		pos = pos.add(0, -pos.getY(), 0);

		int rad = BaseConfigOR.regenRad;

		pos = pos.add(
				world.rand.nextInt(rad) - world.rand.nextInt(rad),
				0,
				world.rand.nextInt(rad) - world.rand.nextInt(rad)
		);

		return pos;
	};

	@Mod.EventHandler
	public void certificateViolation(FMLFingerprintViolationEvent e)
	{
		LOG.warn("*****************************");
		LOG.warn("WARNING: Somebody has been tampering with OresRegeneration jar!");
		LOG.warn("It is highly recommended that you redownload mod from https://www.curseforge.com/projects/433904 !");
		LOG.warn("*****************************");
		HammerCore.invalidCertificates.put("oresregen", "https://www.curseforge.com/projects/433904");
	}

	@NetworkCheckHandler
	public static boolean netCheck(Map<String, String> mods, Side side)
	{
		return true;
	}

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent e)
	{
		modCfg = new File(e.getModConfigurationDirectory(), InfoOR.MOD_NAME.replaceAll(" ", ""));
		if(!modCfg.isDirectory()) modCfg.mkdirs();
		WorldDataOR.register();
		ChunkDataOR.register();
		BaseConfigOR.loadMain(new File(modCfg, "main.cfg"));
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent e)
	{
		OreDefinition.initialize(new File(modCfg, "allowlist.json"), new File(modCfg, "blocklist.json"));
		SoilConfigOR.initSoils(new File(modCfg, "soils.json"));
		SoilConfigOR.initDimensionSoils(new File(modCfg, "dim_soils.json"));
	}
}