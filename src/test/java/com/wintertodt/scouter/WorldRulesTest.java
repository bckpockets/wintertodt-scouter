package com.wintertodt.scouter;

import java.util.EnumSet;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldRulesTest
{
    @Test public void discoversNewThemedWorldsWithoutHardcodedIds()
    {
        assertTrue(WorldRules.isScoutingWorld(World.builder().id(402).activity("Wintertodt")
            .types(EnumSet.of(WorldType.MEMBERS)).build()));
        assertTrue(WorldRules.isScoutingWorld(World.builder().id(999).activity("Wintertodt")
            .types(EnumSet.of(WorldType.MEMBERS)).build()));
    }
    @Test public void excludesDangerousAndSeasonalWorldsEvenWithMatchingActivity()
    {
        for (WorldType type : new WorldType[]{WorldType.PVP, WorldType.HIGH_RISK, WorldType.BETA_WORLD, WorldType.SEASONAL})
        {
            assertFalse(WorldRules.isScoutingWorld(World.builder().id(402).activity("Wintertodt")
                .types(EnumSet.of(WorldType.MEMBERS, type)).build()));
        }
        assertFalse(WorldRules.isScoutingWorld(null));
        assertFalse(WorldRules.isScoutingWorld(World.builder().id(402).activity("Trade")
            .types(EnumSet.of(WorldType.MEMBERS)).build()));
    }
}
