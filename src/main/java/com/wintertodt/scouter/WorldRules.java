package com.wintertodt.scouter;

import java.util.Locale;
import net.runelite.http.api.worlds.World;

final class WorldRules
{
    private WorldRules() {}

    static boolean isScoutingWorld(World world)
    {
        if (world == null || world.getActivity() == null
            || !world.getActivity().toLowerCase(Locale.ROOT).contains("wintertodt")) { return false; }
        // Fail closed for PvP, high risk, tournament, beta and seasonal worlds.
        // Seasonal hopping needs its own explicit account-mode compatibility checks.
        return world.getTypes() != null && world.getTypes().stream()
            .allMatch(type -> type.name().equals("MEMBERS"));
    }
}
