package com.wintertodt.scouter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(WintertodtScouterPlugin.CONFIG_GROUP)
public interface WintertodtScouterConfig extends Config
{
    String DEFAULT_SERVICE_URL = "https://wintertodt-scouter.wintertodt-scouter-service.workers.dev";

    @ConfigItem(keyName = "serviceUrlV2", name = "Scouting service URL", position = 0,
        description = "Optional HTTPS service override. Leave blank to use the default Cloudflare service. The server receives your IP address.")
    default String serviceUrl() { return DEFAULT_SERVICE_URL; }

    static String resolveServiceUrl(String configured)
    {
        return configured == null || configured.trim().isEmpty() ? DEFAULT_SERVICE_URL : configured.trim();
    }

    @ConfigItem(keyName = "shareObservationsV2", name = "Share observations", position = 1,
        description = "Send world, boss energy or restart timer, and observation time to the configured service. No account name is sent.")
    default boolean shareObservations() { return false; }

    @ConfigItem(keyName = "worldHopperEnabled", name = "Enable manual hopping", position = 2,
        description = "Enable Hop button and double-click hopping. Hopping always requires your click.")
    default boolean hoppingEnabled() { return true; }
}
