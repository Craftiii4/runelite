/*
 * Copyright (c) 2018, Craftiii4 <Craftiii4@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.mining;

import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.TileObject;

import java.util.HashMap;

/**
 * Holds each world currently being tracked for respawn timers
 */
public class MiningWorldTracker
{

	@Getter(AccessLevel.PUBLIC)
	private MiningRockType.WorldRock trackingRock;

	@Getter(AccessLevel.PUBLIC)
	private HashMap<Integer, MiningWorld> trackedWorlds = new HashMap<>();

	public MiningWorldTracker(MiningRockType.WorldRock trackingRock)
	{
		this.trackingRock = trackingRock;
	}

	/**
	 * Adds a tracked rock to a world.
	 *
	 * @param world			World ID
	 * @param object		The TileObject of the rock to track
	 * @param mined			The MinedRock of the rock, containing the Type and respawn time
	 */
	public void addTracked(int world, TileObject object, MinedRock mined)
	{
		if (!trackedWorlds.containsKey(world))
		{
			trackedWorlds.put(world, new MiningWorld(world));
		}
		// Clear any negative rocks as a new rock is being added
		trackedWorlds.get(world).clearNegatives();
		trackedWorlds.get(world).getRocks().put(object, mined);
	}

}
