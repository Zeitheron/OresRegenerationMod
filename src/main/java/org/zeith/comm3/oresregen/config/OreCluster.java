package org.zeith.comm3.oresregen.config;

import com.zeitheron.hammercore.cfg.file1132.io.ConfigEntryCategory;

public class OreCluster
{
	public final int max;
	public final int maxY;
	public final String oreDictEntry;

	public OreCluster(int max, int maxY, String oreDictEntry)
	{
		this.max = max;
		this.maxY = maxY;
		this.oreDictEntry = oreDictEntry;
	}

	public OreCluster(ConfigEntryCategory cat, int max, int maxY)
	{
		boolean comments = cat.getName().equals("@default");

		this.max = cat.getIntEntry("Size", max, 1, 64).setDescription(comments ? "The maximum amount of ore blocks to generate as a single ore 'vein'" : null).getValue();
		this.maxY = cat.getIntEntry("MaxY", maxY, 1, 64).setDescription(comments ? "The maximum Y level at which the ore might generate." : null).getValue();
		this.oreDictEntry = comments ? null : cat.getName();
	}

	public OreCluster(ConfigEntryCategory cat, OreCluster def)
	{
		this.max = cat.getIntEntry("Size", def.max, 1, 64).getValue();
		this.maxY = cat.getIntEntry("MaxY", def.maxY, 1, 256).getValue();
		this.oreDictEntry = cat.getName().equals("@default") ? null : cat.getName();
	}
}