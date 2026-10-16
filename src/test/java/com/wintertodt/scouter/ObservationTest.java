package com.wintertodt.scouter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ObservationTest
{
    @Test public void readsEnergyWithoutMistakingWarmthOrTimerForEnergy()
    {
        assertEquals(57, ObservationParser.parse(307, "<col=ffffff>Wintertodt Energy: 57%</col>", 0, 100).energy);
        assertNull(ObservationParser.parse(307, "Warmth: 57%", 0, 100));
        assertNull(ObservationParser.parse(307, "Next game: 1:32", 0, 100));
        assertNull(ObservationParser.parse(307, "Energy: 101%", 0, 100));
        assertNull(ObservationParser.parse(307, null, 0, 100));
    }

    @Test public void respawnVarbitWinsOverOldEnergyText()
    {
        Observation o = ObservationParser.parse(307, "Energy: 100%", 150, 100);
        assertEquals(-1, o.energy);
        assertEquals(90, o.restartSeconds);
        assertEquals("1:25", o.display(105));
        assertEquals(1, ObservationParser.parse(307, null, 1, 100).restartSeconds);
    }

    @Test public void neverInventsRoundStartAndExpiresAtThirtySeconds()
    {
        Observation o = new Observation(307, -1, 2, 100);
        assertEquals("Awaiting start", o.display(103));
        assertEquals("Stale", o.display(130));
        assertFalse(o.isFresh(94));
        assertTrue(o.isFresh(129));
    }

    @Test public void localWinsAndOutOfOrderReportsCannotRollback()
    {
        ObservationStore store = new ObservationStore();
        Observation newest = new Observation(307, 80, -1, 110);
        store.merge(Arrays.asList(newest, new Observation(307, 90, -1, 100)), 110);
        assertSame(newest, store.get(307, 110));
        Observation local = new Observation(307, 75, -1, 109);
        store.setLocal(local);
        assertSame(local, store.get(307, 110));
        store.clearLocal();
        assertSame(newest, store.get(307, 110));
        store.clear();
        assertNull(store.get(307, 110));
    }

    @Test public void validatesEachRemoteRowAndKeepsGoodRows()
    {
        String json = "{\"version\":2,\"observations\":["
            + "{\"world\":307,\"energy\":77,\"restartSeconds\":-1,\"observedAt\":100},"
            + "{\"world\":309,\"energy\":-1,\"restartSeconds\":50,\"observedAt\":1},"
            + "{\"world\":311,\"energy\":101,\"restartSeconds\":-1,\"observedAt\":100},"
            + "{\"world\":312,\"energy\":1.5,\"restartSeconds\":-1,\"observedAt\":100},null,{}]}";
        assertEquals(1, ScoutingProtocol.decode(json, 110).size());
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsUnsupportedProtocol()
    { ScoutingProtocol.decode("{\"version\":1,\"observations\":[]}", 100); }

    @Test public void normalizesServicePaths()
    {
        assertEquals("https://example.org/v2/worlds", ScoutingNetwork.endpoint("https://example.org").toString());
        assertEquals("https://example.org/scout/v2/worlds", ScoutingNetwork.endpoint("https://example.org/scout/").toString());
        assertEquals("https://example.org/scout/v2/worlds", ScoutingNetwork.endpoint("https://example.org/scout").toString());
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsPlainHttp()
    { ScoutingNetwork.endpoint("http://example.org"); }

    @Test(expected = IllegalArgumentException.class) public void rejectsEmbeddedCredentials()
    { ScoutingNetwork.endpoint("https://user:password@example.org"); }
}
