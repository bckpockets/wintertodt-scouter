package com.wintertodt.scouter;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServiceUrlConfigTest
{
    @Test public void oldEmptySettingsUseTheProductionService()
    {
        for (String saved : new String[]{null, "", "  \t"})
        {
            assertEquals(WintertodtScouterConfig.DEFAULT_SERVICE_URL,
                WintertodtScouterConfig.resolveServiceUrl(saved));
        }
    }

    @Test public void explicitCustomServiceOverridesTheDefault()
    {
        assertEquals("https://example.org/scout", WintertodtScouterConfig.resolveServiceUrl(" https://example.org/scout "));
    }
}
