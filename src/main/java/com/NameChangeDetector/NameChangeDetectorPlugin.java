package com.NameChangeDetector;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import com.google.inject.Provides;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.ArrayUtils;

//Some code taken from while-loop/runelite-plugin runewatch to help with menu entry creation.
//https://github.com/while-loop/runelite-plugins/blob/runewatch/src/main/java/com/runewatch/RuneWatchPlugin.java


@Slf4j
@PluginDescriptor(
	name = "Clanmate Name Change Detector"
)
public class NameChangeDetectorPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private NameChangeDetectorConfig config;

	@Inject
	private NameChangeManager nameChangeManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private static final String INVESTIGATE = "Previous names";

	private static final List<Integer> MENU_WIDGET_IDS = ImmutableList.of(
		WidgetInfo.FRIENDS_CHAT.getGroupId(),
		WidgetInfo.CHATBOX.getGroupId(),
		WidgetInfo.RAIDING_PARTY.getGroupId(),
		WidgetInfo.IGNORE_LIST.getGroupId(),
		WidgetInfo.CLAN_MEMBER_LIST.getGroupId()
	);

	private static final ImmutableList<String> AFTER_OPTIONS = ImmutableList.of(
		"Message", "Add ignore", "Remove friend", "Delete", "Kick", "Reject"
	);

	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says " + config.greeting(), null);
		}
	}

	@Provides
	NameChangeDetectorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NameChangeDetectorConfig.class);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event) {
//		if (!config.menuOption() || (!hotKeyPressed && config.useHotkey())) {
//			return;
//		}

		int groupId = WidgetInfo.TO_GROUP(event.getActionParam1());
		String option = event.getOption();

		if (!MENU_WIDGET_IDS.contains(groupId) || !AFTER_OPTIONS.contains(option)) {
			return;
		}

		for (MenuEntry me : client.getMenuEntries()) {
			// don't add menu option if we've already added investigate
			if (INVESTIGATE.equals(me.getOption())) {
				return;
			}
		}

		final MenuEntry lookup = new MenuEntry();
		lookup.setOption(INVESTIGATE);
		lookup.setTarget(event.getTarget());
		lookup.setType(MenuAction.RUNELITE.getId());
		lookup.setParam0(event.getActionParam0());
		lookup.setParam1(event.getActionParam1());
		lookup.setIdentifier(event.getIdentifier());

		MenuEntry[] newMenu = ObjectArrays.concat(client.getMenuEntries(), lookup);
		ArrayUtils.swap(newMenu, newMenu.length - 1, newMenu.length - 2);
		client.setMenuEntries(newMenu);
	}

	//https://github.com/while-loop/runelite-plugins/blob/2b3ded2bb6d12546bf46884c6ec0d876cce99636/src/main/java/com/runewatch/RuneWatchPlugin.java#L303
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		int groupId = WidgetInfo.TO_GROUP(event.getWidgetId());
		String option = event.getMenuOption();
		MenuAction action = event.getMenuAction();


		if ((action == MenuAction.RUNELITE || action == MenuAction.RUNELITE_PLAYER) && option.equals(INVESTIGATE)) {
			final String target;
			if (action == MenuAction.RUNELITE_PLAYER) {
				// The player id is included in the event, so we can use that to get the player name,
				// which avoids having to parse out the combat level and any icons preceding the name.
				Player player = client.getCachedPlayers()[event.getId()];
				if (player != null) {
					target = player.getName();
				} else {
					target = null;
				}
			} else {
				target = Text.removeTags(event.getMenuTarget());
			}

			if (target != null) {
				//https://api.wiseoldman.net/players/username/harming/names
				//https://crystalmathlabs.com/tracker/api.php?type=previousname&player=Harming
				//caseManager.get(event.getMenuTarget(), (rwCase) -> alertPlayerWarning(target, true, false));
				List<String> names = nameChangeManager.getPreviousNames(target);
				printPreviousNames(target, names);
			}
		}

	}


	private void printPreviousNames(String currentRsn, List<String> names) {
		currentRsn = Text.toJagexName(currentRsn);

		ChatMessageBuilder response = new ChatMessageBuilder();

		long countOfNames = names.stream().count();

		if( countOfNames > 0){
			response.append(ChatColorType.HIGHLIGHT).append(currentRsn).append(ChatColorType.NORMAL);
			response.append(" has also gone by: ").append(ChatColorType.HIGHLIGHT);
			int timesRan = 1;
			for (String name : names)
			{
				response.append(name);
				if(timesRan != countOfNames){
					response.append(", ");
				}
				timesRan++;
			}
			
		}else{
			response.append("No previous names were found for ")
				.append(ChatColorType.HIGHLIGHT);
			response.append(currentRsn);
		}



		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(response.build())
			.build());
	}
}