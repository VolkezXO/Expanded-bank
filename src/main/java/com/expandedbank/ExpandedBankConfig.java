package com.expandedbank;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("expandedbank")
public interface ExpandedBankConfig extends Config
{
	@ConfigItem(
			keyName = "alwaysExpand",
			name = "Always Expanded",
			description = "When the bank is open, it is always expanded and the chatbox will always be hidden."
	)
	default boolean alwaysExpand()
	{
		return false;
	}
}