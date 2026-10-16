package com.wintertodt.scouter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ObservationParser
{
    private static final Pattern ENERGY = Pattern.compile("^(?:Wintertodt(?:['’]s)?\\s+)?Energy:\\s*(\\d{1,3})%$", Pattern.CASE_INSENSITIVE);

    private ObservationParser() {}

    static Observation parse(int world, String energyText, int respawnTicks, long now)
    {
        if (respawnTicks > 0 && respawnTicks <= 500)
        {
            // The game transmits ticks of 0.6 seconds. Round up rather than show zero early.
            return new Observation(world, -1, (respawnTicks * 3 + 4) / 5, now);
        }
        String plain = energyText == null ? "" : energyText.replaceAll("<[^>]*>", "").trim();
        Matcher match = ENERGY.matcher(plain);
        if (!match.matches())
        {
            return null;
        }
        int energy = Integer.parseInt(match.group(1));
        return energy <= 100 ? new Observation(world, energy, -1, now) : null;
    }
}
