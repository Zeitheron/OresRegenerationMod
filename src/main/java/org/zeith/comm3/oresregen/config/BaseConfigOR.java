package org.zeith.comm3.oresregen.config;

import com.zeitheron.hammercore.cfg.file1132.Configuration;
import com.zeitheron.hammercore.cfg.file1132.io.ConfigEntryCategory;

import java.io.File;

public class BaseConfigOR
{
	public static Configuration main;

	public static int regenRad;

	public static int blockRegenDistFromPlayer;

	public static ConfigEntryCategory clustersCfg;
	public static OreCluster defaultClusterSize;

	public static void loadMain(File cfgFile)
	{
		Configuration cfg = main = new Configuration(cfgFile);

		ConfigEntryCategory regen = cfg.getCategory("regen");

		regenRad = regen.getIntEntry("radius", 0, 0, 32_768).setDescription("The radius to regenerate ores in.").getValue();
		blockRegenDistFromPlayer = regen.getIntEntry("player distance", 32, 0, Integer.MAX_VALUE).setDescription("").getValue();

		ConfigEntryCategory clusters = clustersCfg = regen.getCategory("clusters");
		{
			defaultClusterSize = new OreCluster(clusters.getCategory("@default"), 9, 64);
			new OreCluster(clusters.getCategory("oreEmerald"), 2, 48);
			new OreCluster(clusters.getCategory("oreCoal"), 17, 128);
			new OreCluster(clusters.getCategory("oreIron"), 9, 96);
			new OreCluster(clusters.getCategory("oreGold"), 9, 32);
			new OreCluster(clusters.getCategory("oreRedstone"), 8, 24);
			new OreCluster(clusters.getCategory("oreDiamond"), 8, 16);
			new OreCluster(clusters.getCategory("oreLapis"), 7, 24);
		}

		if(cfg.hasChanged()) cfg.save();
	}
}