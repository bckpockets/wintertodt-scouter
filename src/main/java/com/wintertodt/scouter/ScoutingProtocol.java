package com.wintertodt.scouter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

final class ScoutingProtocol
{
    private ScoutingProtocol() {}

    static List<Observation> decode(Gson gson, String json, long now)
    {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        if (root == null || integer(root, "version") != 2 || !root.has("observations")
            || !root.get("observations").isJsonArray())
        {
            throw new IllegalArgumentException("Unsupported scouting response");
        }
        JsonArray rows = root.getAsJsonArray("observations");
        if (rows.size() > 500) { throw new IllegalArgumentException("Too many reports"); }
        List<Observation> result = new ArrayList<>();
        for (JsonElement row : rows)
        {
            try
            {
                JsonObject o = row.getAsJsonObject();
                Observation observation = new Observation(Math.toIntExact(integer(o, "world")),
                    Math.toIntExact(integer(o, "energy")), Math.toIntExact(integer(o, "restartSeconds")),
                    integer(o, "observedAt"));
                if (observation.isFresh(now)) { result.add(observation); }
            }
            catch (RuntimeException ignored)
            {
                // An invalid row must not discard valid reports for other worlds.
            }
        }
        return result;
    }

    private static long integer(JsonObject object, String key)
    {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        { throw new IllegalArgumentException("Missing numeric field"); }
        return value.getAsBigDecimal().longValueExact();
    }

    static String encode(Gson gson, Observation observation)
    {
        return gson.toJson(observation);
    }
}
