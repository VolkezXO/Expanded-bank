package com.volkezxo;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
		name = "Expanded bank",
		description = "Expands the bank and seed vault interfaces. (Resizable only)"
)
public class ExpandedBankPlugin extends Plugin
{
	private static final int CHATBOX_BUTTON_HEIGHT = 23;
	private static final int CHATBOX_NORMAL_HEIGHT = 165;
	private static final int BANK_X = 12;
	private static final int BANK_Y = 2;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ExpandedBankConfig config;

	private boolean bankExpanded = false;
	private boolean needBankExpansionUpdate = false;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Expanded Bank plugin started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Expanded Bank plugin stopped!");
		bankExpanded = false;
		needBankExpansionUpdate = false;
		restoreLayout();
	}

	@Provides
	ExpandedBankConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExpandedBankConfig.class);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		int groupId = event.getGroupId();
		if (groupId == WidgetID.BANK_GROUP_ID || groupId == InterfaceID.SEED_VAULT)
		{
			needBankExpansionUpdate = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		int groupId = event.getGroupId();
		if (groupId == WidgetID.BANK_GROUP_ID || groupId == InterfaceID.SEED_VAULT)
		{
			bankExpanded = false;
			needBankExpansionUpdate = false;
			restoreLayout();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		int scriptId = event.getScriptId();
		if (scriptId == 909 || scriptId == 175 || scriptId == 178 ||
				scriptId == ScriptID.MESSAGE_LAYER_OPEN || scriptId == ScriptID.MESSAGE_LAYER_CLOSE)
		{
			if (bankExpanded || needBankExpansionUpdate)
			{
				handleBankExpansion();
			}
		}
	}

	private void handleBankExpansion()
	{
		if (!client.isResized()) return;

		Widget bankWidget = client.getWidget(ComponentID.BANK_CONTAINER);
		Widget seedVaultWidget = client.getWidget(InterfaceID.SEED_VAULT, 1);

		boolean bankOpen = (bankWidget != null && !bankWidget.isSelfHidden());
		boolean seedVaultOpen = (seedVaultWidget != null && !seedVaultWidget.isSelfHidden());

		if (!bankOpen && !seedVaultOpen)
		{
			if (bankExpanded)
			{
				bankExpanded = false;
				needBankExpansionUpdate = false;
				restoreLayout();
			}
			return;
		}

		needBankExpansionUpdate = false;
		boolean justExpanded = !bankExpanded;
		bankExpanded = true;
		Widget activeWidget = bankOpen ? bankWidget : seedVaultWidget;

		if (justExpanded && bankOpen && activeWidget.getOnLoadListener() != null)
		{
			clientThread.invokeLater(() -> {
				client.createScriptEventBuilder(activeWidget.getOnLoadListener())
						.setSource(activeWidget)
						.build()
						.run();
			});
		}

		Widget hudContainer = getHudContainer();
		Widget viewport = getViewport();

		if (hudContainer != null && viewport != null)
		{
			Widget osbParent = hudContainer.getParent();
			boolean isChatClosed = isChatboxClosed();
			boolean shouldExpand = config.alwaysExpand() || isChatClosed;

			if (shouldExpand)
			{
				if (!isChatClosed)
				{
					setChatboxVisualsHidden(true);
				}

				if (osbParent.getOriginalHeight() != viewport.getHeight())
				{
					osbParent.setOriginalWidth(viewport.getWidth());
					osbParent.setOriginalHeight(viewport.getHeight());
					osbParent.setXPositionMode(0);
					osbParent.setYPositionMode(0);
					osbParent.revalidateScroll();
				}

				if (hudContainer.getOriginalHeight() != CHATBOX_BUTTON_HEIGHT)
				{
					hudContainer.setOriginalHeight(CHATBOX_BUTTON_HEIGHT);
					hudContainer.revalidateScroll();
				}

				activeWidget.setOriginalX(BANK_X);
				activeWidget.setOriginalY(BANK_Y);
				activeWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
				activeWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);

				int availableHeight = viewport.getHeight() - CHATBOX_BUTTON_HEIGHT - BANK_Y - 1;
				if (activeWidget.getOriginalHeight() != availableHeight)
				{
					activeWidget.setOriginalHeight(availableHeight);
					activeWidget.setHeightMode(WidgetSizeMode.ABSOLUTE);
					activeWidget.revalidateScroll();
				}
			}
			else
			{
				setChatboxVisualsHidden(false);

				if (hudContainer.getOriginalHeight() != CHATBOX_NORMAL_HEIGHT)
				{
					hudContainer.setOriginalHeight(CHATBOX_NORMAL_HEIGHT);
					hudContainer.revalidateScroll();
				}

				if (activeWidget.getXPositionMode() != WidgetPositionMode.ABSOLUTE_CENTER)
				{
					activeWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
					activeWidget.setOriginalX(0);
					activeWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
					activeWidget.setOriginalY(0);
					activeWidget.setHeightMode(WidgetSizeMode.MINUS);
					activeWidget.setOriginalHeight(0);
					activeWidget.revalidateScroll();
				}
			}
		}
	}

	private void restoreLayout()
	{
		Widget hudContainer = getHudContainer();
		Widget viewport = getViewport();

		if (hudContainer != null && viewport != null)
		{
			Widget osbParent = hudContainer.getParent();

			if (osbParent.getOriginalHeight() != viewport.getHeight())
			{
				osbParent.setOriginalHeight(viewport.getHeight());
				osbParent.revalidateScroll();
			}

			boolean chatClosed = isChatboxClosed();
			if (!chatClosed)
			{
				setChatboxVisualsHidden(false);
			}

			int targetHeight = chatClosed ? CHATBOX_BUTTON_HEIGHT : CHATBOX_NORMAL_HEIGHT;
			if (hudContainer.getOriginalHeight() != targetHeight)
			{
				hudContainer.setOriginalHeight(targetHeight);
				hudContainer.revalidateScroll();
			}
		}
	}

	private void setChatboxVisualsHidden(boolean hidden)
	{
		Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
		Widget chatBg = client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);

		if (chatArea != null && chatArea.isHidden() != hidden)
		{
			chatArea.setHidden(hidden);
			chatArea.revalidate();
		}
		if (chatBg != null && chatBg.isHidden() != hidden)
		{
			chatBg.setHidden(hidden);
			chatBg.revalidate();
		}
	}

	private Widget getHudContainer()
	{
		Widget classic = client.getWidget(InterfaceID.ToplevelOsrsStretch.HUD_CONTAINER_FRONT);
		Widget modern = client.getWidget(InterfaceID.ToplevelPreEoc.HUD_CONTAINER_FRONT);
		return (classic != null && !classic.isHidden()) ? classic : modern;
	}

	private Widget getViewport()
	{
		Widget classic = client.getWidget(InterfaceID.ToplevelOsrsStretch.VIEWPORT);
		Widget modern = client.getWidget(InterfaceID.ToplevelPreEoc.VIEWPORT);
		return (classic != null && !classic.isHidden()) ? classic : modern;
	}

	private boolean isChatboxClosed()
	{
		Widget chatboxFrame = client.getWidget(ComponentID.CHATBOX_FRAME);
		return chatboxFrame == null || chatboxFrame.isHidden();
	}
}