package net.runelite.client.plugins.mining;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.ExperienceChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Set;

@PluginDescriptor(
	name = "Mining",
	description = "Show helpful information when mining",
	tags = {"mining", "mine"},
	enabledByDefault = false
)
public class MiningPlugin extends Plugin
{

	@Inject
	private OverlayManager overlayManager;

	@Inject
	MiningRockOverlay oreOverlay;

	@Inject
	MiningWorldHopperOverlay worldHopperOverlay;

	@Inject
	MiningOverlay miningOverlay;

	@Inject
	MiningConfig config;

	@Inject
	private Client client;

	@Getter(AccessLevel.PACKAGE)
	private int miningLevel;

	@Getter(AccessLevel.PACKAGE)
	private final HashMap<TileObject, MinedRock> ores = new HashMap<>();

	@Provides
	MiningConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MiningConfig.class);
	}

	@Getter(AccessLevel.PACKAGE)
	private MiningSession session;

	@Getter(AccessLevel.PUBLIC)
	private MiningWorldTracker miningTracker;

	private static final Set<Integer> MOTHERLODE_MAP_REGIONS = ImmutableSet.of(14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193);

	@Override
	protected void startUp()
	{
		overlayManager.add(miningOverlay);
		overlayManager.add(oreOverlay);
		overlayManager.add(worldHopperOverlay);
		session = new MiningSession();
		if (config.trackWorldRock() !=  MiningRockType.WorldRock.None)
		{ // Setup world mining tracker for a certain type of rock
			miningTracker = new MiningWorldTracker(config.trackWorldRock());
		}
		else
		{
			miningTracker = null;
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(miningOverlay);
		overlayManager.remove(oreOverlay);
		overlayManager.remove(worldHopperOverlay);
		ores.clear();
		miningLevel = Experience.getLevelForXp(client.getSkillExperience(Skill.MINING));
		session = null;
		miningTracker = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getKey().equals("trackWorldRock"))
		{
			MiningRockType.WorldRock worldRock = config.trackWorldRock();
			if (worldRock == MiningRockType.WorldRock.None)
			{
				miningTracker = null;
			}
			else
			{ // Setup world mining tracker for a certain type of rock
				miningTracker = new MiningWorldTracker(config.trackWorldRock());
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		MiningRockType rock = MiningRockType.getTypeFromID(object.getId());
		if (rock != null)
		{
			for (TileObject o : ores.keySet())
			{
				if (o.getX() == object.getX() && o.getY() == object.getY())
				{ // Remove ground rock as it has respawned
					ores.remove(o);
					break;
				}
			}
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		Duration timeSinceStart = Duration.between(session.getIgnoreSpawn(), Instant.now());
		if (timeSinceStart.getSeconds() > 1)
		{ // Ignore anything spawned within 1 second of logging in or changing regions (prevents timers appearing on already mined rocks)
			GameObject object = event.getGameObject();
			MiningRockType rock = MiningRockType.getTypeFromID(object.getId());
			if (rock != null && miningLevel >= rock.getRequiredLevel())
			{ // Only display if player can actually mine the rock
				if (!ores.containsKey(object))
				{ // Add timer for ground rock
					ores.put(object, new MinedRock(rock));
				}
			}
		}
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		Duration timeSinceStart = Duration.between(session.getIgnoreSpawn(), Instant.now());
		if (timeSinceStart.getSeconds() > 1)
		{ // Ignore anything spawned within 1 second of logging in or changing regions (prevents timers appearing on already mined rocks)
			WallObject object = event.getWallObject();
			MiningRockType rock = MiningRockType.getTypeFromID(object.getId());
			if (rock != null && miningLevel >= rock.getRequiredLevel())
			{ // Only display if player can actually mine the rock
				if (!ores.containsKey(object))
				{ // Add timer for wall rock
					ores.put(object, new MinedRock(rock));
				}
			}
		}
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		WallObject object = event.getWallObject();
		MiningRockType rock = MiningRockType.getTypeFromID(object.getId());
		if (rock != null)
		{
			for (TileObject o : ores.keySet())
			{
				if (o.getX() == object.getX() && o.getY() == object.getY())
				{ // Remove wall rock as it has respawned
					ores.remove(o);
					break;
				}
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING)
		{ // Check if player is hopping worlds
			MiningRockType.WorldRock worldRock = config.trackWorldRock();
			if (worldRock != MiningRockType.WorldRock.None)
			{ // Check if player wants to track a rock respawn between worlds
				int world = client.getWorld();
				for (TileObject rock : ores.keySet())
				{ // Go through every tracked rock
					if (worldRock.getRockType() == ores.get(rock).getType())
					{ // If the type matches the multi-world rock then add to the mining tracker
						miningTracker.addTracked(world, rock, ores.get(rock));
					}
				}
			}
		}
		else if (state == GameState.LOADING)
		{ // Clear all ores when logging in or switching regions
			ores.clear();
			session.setIgnoreSpawn(Instant.now());
		}
		else if (state == GameState.LOGGED_IN)
		{
			int world = client.getWorld();
			if (miningTracker != null && miningTracker.getTrackedWorlds().containsKey(world))
			{ // Check if the current world exists in the tracked worlds
				MiningWorld track = miningTracker.getTrackedWorlds().get(world);
				track.clearNegatives();
				for (TileObject o : track.getRocks().keySet())
				{ // Load all the tracked ores into the to-render hashmap
					ores.put(o, track.getRocks().get(o));
				}
				miningTracker.getTrackedWorlds().remove(world); // We're on this world now, so don't track it in the world tracker anymore
			}
		}
	}

	@Subscribe
	public void onExperienceChanged(ExperienceChanged event)
	{ // Keeps the players mining level up to date
		if (event.getSkill() == Skill.MINING)
		{
			miningLevel = Experience.getLevelForXp(client.getSkillExperience(Skill.MINING));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.FILTERED)
		{
			return;
		}
		if (event.getMessage().startsWith("You manage to mine some"))
		{ // Check for when ever the player mines a rock
			String oreName = event.getMessage().substring(24).replace(".", "");
			MiningRockType rock = MiningRockType.getTypeFromName(oreName);
			if (rock != null)
			{ // If the rock exists then increase the amount mined
				session.increaseRockMine(rock);
			}

		}
	}

	/**
	 * Checks if the player has mined recently (within config controlled session timeout)
	 */
	@Schedule(
		period = 1,
		unit = ChronoUnit.SECONDS
	)
	public void checkMining()
	{
		for (MiningRockType rock : MiningRockType.values())
		{
			if (session.getLastOreMined()[rock.getIndex()] != null)
			{
				Duration statTimeout = Duration.ofMinutes(config.statTimeout());
				Duration sinceMined = Duration.between(session.getLastOreMined()[rock.getIndex()], Instant.now());
				if (sinceMined.compareTo(statTimeout) >= 0)
				{
					session.clearSessionFor(rock);
				}
			}
		}
	}

	/**
	 * Taken from the MLM plugin, checks if the player is currently in the motherloade mine
	 * @return 		If player is in the motherloade mine
	 */
	public boolean checkInMlm()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		int[] currentMapRegions = client.getMapRegions();

		// Verify that all regions exist in MOTHERLODE_MAP_REGIONS
		for (int region : currentMapRegions)
		{
			if (!MOTHERLODE_MAP_REGIONS.contains(region))
			{
				return false;
			}
		}

		return true;
	}

}
