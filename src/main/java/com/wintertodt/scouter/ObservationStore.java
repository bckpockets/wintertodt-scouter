package com.wintertodt.scouter;

import java.util.HashMap;
import java.util.Map;

/** All access is on the RuneLite client thread. */
final class ObservationStore
{
    private final Map<Integer, Observation> remote = new HashMap<>();
    private Observation local;

    void setLocal(Observation observation) { local = observation; }
    Observation getLocal() { return local; }
    void clearLocal() { local = null; }
    void clear() { local = null; remote.clear(); }

    void merge(Iterable<Observation> observations, long now)
    {
        remote.values().removeIf(value -> !value.isFresh(now));
        for (Observation next : observations)
        {
            Observation previous = remote.get(next.world);
            if (next.isFresh(now) && (previous == null || next.observedAt > previous.observedAt))
            {
                remote.put(next.world, next);
            }
        }
    }

    Observation get(int world, long now)
    {
        // Direct observation of the current world takes precedence over crowdsourced reports.
        if (local != null && local.world == world && local.isFresh(now))
        {
            return local;
        }
        return remote.get(world);
    }
}
