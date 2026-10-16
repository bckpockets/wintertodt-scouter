package com.wintertodt.scouter;

/** An immutable observation. Its timestamp is never refreshed without a new reading. */
final class Observation
{
    static final int MAX_AGE_SECONDS = 30;
    final int world;
    final int energy;
    final int restartSeconds;
    final long observedAt;

    Observation(int world, int energy, int restartSeconds, long observedAt)
    {
        if (world < 301 || world > 2000 || observedAt <= 0
            || !((energy >= 0 && energy <= 100 && restartSeconds == -1)
            || (energy == -1 && restartSeconds >= 0 && restartSeconds <= 300)))
        {
            throw new IllegalArgumentException("Invalid Wintertodt observation");
        }
        this.world = world;
        this.energy = energy;
        this.restartSeconds = restartSeconds;
        this.observedAt = observedAt;
    }

    boolean isFresh(long now)
    {
        return observedAt <= now + 5 && now - observedAt < MAX_AGE_SECONDS;
    }

    String display(long now)
    {
        if (!isFresh(now))
        {
            return "Stale";
        }
        if (restartSeconds < 0)
        {
            return energy + "%";
        }
        long remaining = restartSeconds - Math.max(0, now - observedAt);
        return remaining <= 0 ? "Awaiting start" : String.format("%d:%02d", remaining / 60, remaining % 60);
    }
}
