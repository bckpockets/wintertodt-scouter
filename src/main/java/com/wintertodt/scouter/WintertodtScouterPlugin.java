/*
 * Copyright (c) 2021, nucleon
 * Copyright (c) 2021, Andrew McAdams, Cyborger1, Psikoi
 * Copyright (c) 2024, Nouser
 * Copyright (c) 2026, bckpockets
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.wintertodt.scouter;

import com.google.inject.Provides;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import okhttp3.HttpUrl;

@PluginDescriptor(name = "Wintertodt Scouter", description = "Scout Wintertodt worlds using fresh observations",
    tags = {"wintertodt", "firemaking", "scouting"})
public class WintertodtScouterPlugin extends Plugin
{
    static final String CONFIG_GROUP = "wintertodtscouter";
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar toolbar;
    @Inject private WorldService worlds;
    @Inject private WintertodtScouterConfig config;
    @Inject private ScoutingNetwork network;
    private final ObservationStore store = new ObservationStore();
    private volatile boolean running;
    private volatile long generation;
    private ScouterPanel panel;
    private NavigationButton navigation;
    private HttpUrl endpoint;
    private String connectionStatus = "Connecting…";
    private String hopStatus;
    private long hopStatusUntil;
    private boolean uploadFailed;
    private int stableWorld = -1;
    private int settlingTicks;
    private net.runelite.api.World hopTarget;
    private int hopAttempts;

    @Provides WintertodtScouterConfig provideConfig(ConfigManager manager)
    { return manager.getConfig(WintertodtScouterConfig.class); }

    @Override protected void startUp()
    {
        running = true;
        generation++;
        // RuneLite calls lifecycle methods on the EDT.
        panel = new ScouterPanel(world -> clientThread.invokeLater(() -> requestHop(world)));
        navigation = NavigationButton.builder().tooltip("Wintertodt Scouter").priority(7)
            .icon(ImageUtil.loadImageResource(getClass(), "/wintertodt-scouter-icon.png")).panel(panel).build();
        toolbar.addNavigation(navigation);
        clientThread.invokeLater(() -> { if (running) { reconfigure(); } });
    }

    @Override protected void shutDown()
    {
        running = false;
        generation++;
        network.cancel();
        toolbar.removeNavigation(navigation);
        long session = generation;
        clientThread.invokeLater(() ->
        {
            if (!running && generation == session) { store.clear(); resetCapture(); hopTarget = null; }
        });
    }

    private void reconfigure()
    {
        generation++;
        network.cancel();
        store.clear();
        resetCapture();
        hopTarget = null;
        uploadFailed = false;
        endpoint = null;
        connectionStatus = "Connecting…";
        try { endpoint = ScoutingNetwork.endpoint(WintertodtScouterConfig.resolveServiceUrl(config.serviceUrl())); }
        catch (IllegalArgumentException error) { connectionStatus = "Invalid HTTPS service URL"; }
        refresh();
    }

    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if (CONFIG_GROUP.equals(event.getGroup()))
        { clientThread.invokeLater(() -> { if (running) { reconfigure(); } }); }
    }

    private void resetCapture()
    { stableWorld = -1; settlingTicks = 0; store.clearLocal(); }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            resetCapture();
            hopTarget = null;
            hopAttempts = 0;
        }
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        if (!running) { return; }
        handleHop();
        capture();
    }

    private void capture()
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
            || client.getLocalPlayer().getWorldLocation().getRegionID() != 6462)
        { resetCapture(); return; }
        if (stableWorld != client.getWorld())
        { resetCapture(); stableWorld = client.getWorld(); }
        // Allow new-world widgets/varbits to arrive; never relabel a previous world's sample.
        if (++settlingTicks <= 2) { return; }
        Widget energy = client.getWidget(InterfaceID.WintStatus.ENERGY_TITLE);
        Widget root = client.getWidget(InterfaceID.WintStatus.UNIVERSE);
        if (root == null || root.isHidden()) { store.clearLocal(); return; }
        store.setLocal(ObservationParser.parse(client.getWorld(), energy == null || energy.isHidden() ? null : energy.getText(),
            client.getVarbitValue(VarbitID.WINT_TRANSMIT_RESPAWNDELAY), Instant.now().getEpochSecond()));
    }

    @Schedule(period = 1, unit = ChronoUnit.SECONDS)
    public void refreshScheduled()
    {
        long session = generation;
        clientThread.invokeLater(() -> { if (running && session == generation) { refresh(); } });
    }

    private void refresh()
    {
        long session = generation;
        long now = Instant.now().getEpochSecond();
        if (endpoint != null)
        {
            if (panel.isViewing()) { network.poll(endpoint, result -> clientThread.invokeLater(() ->
            {
                if (!running || generation != session) { return; }
                store.merge(result.observations, Instant.now().getEpochSecond());
                connectionStatus = result.status;
            })); }
            Observation local = store.getLocal();
            WorldResult result = worlds.getWorlds();
            if (config.shareObservations() && local != null && local.isFresh(now) && result != null
                && WorldRules.isScoutingWorld(result.findWorld(local.world)))
            {
                network.upload(endpoint, local, success -> clientThread.invokeLater(() ->
                { if (running && generation == session) { uploadFailed = !success; } }));
            }
        }
        List<ScouterPanel.Row> rows = new ArrayList<>();
        WorldResult result = worlds.getWorlds();
        if (result != null)
        {
            result.getWorlds().stream().filter(WorldRules::isScoutingWorld).sorted(Comparator.comparingInt(World::getId))
                .forEach(world ->
                {
                    Observation observation = store.get(world.getId(), now);
                    boolean local = observation != null && observation == store.getLocal();
                    rows.add(new ScouterPanel.Row(world.getId(), world.getId() == client.getWorld(),
                        observation == null ? "Unknown" : observation.display(now),
                        observation == null ? "—" : local ? "Local" : Math.max(0, now - observation.observedAt) + "s"));
                });
        }
        String status = hopStatus != null && now < hopStatusUntil ? hopStatus
            : result == null ? "Loading world list…" : connectionStatus + (uploadFailed ? " • upload failed" : "");
        boolean hopping = config.hoppingEnabled();
        SwingUtilities.invokeLater(() ->
        { if (running && generation == session && panel != null) { panel.update(rows, status, hopping); } });
    }

    private void requestHop(int worldId)
    {
        if (!running || !config.hoppingEnabled() || worldId == client.getWorld() || hopTarget != null) { return; }
        WorldResult result = worlds.getWorlds();
        World world = result == null ? null : result.findWorld(worldId);
        if (!WorldRules.isScoutingWorld(world) || world.getPlayers() < 0 || world.getPlayers() >= 2000)
        { showHopStatus("World unavailable"); return; }
        if (client.getGameState() != GameState.LOGGED_IN && client.getGameState() != GameState.LOGIN_SCREEN)
        { showHopStatus("Wait until login or hop completes"); return; }
        if (client.getGameState() == GameState.LOGGED_IN
            && client.getWorldType().stream().anyMatch(type -> !type.name().equals("MEMBERS")))
        { showHopStatus("Use the game hopper for this world type"); return; }
        net.runelite.api.World target = client.createWorld();
        target.setId(world.getId());
        target.setAddress(world.getAddress());
        target.setActivity(world.getActivity());
        if (world.getRegion() != null) { target.setLocation(world.getRegion().getId()); }
        target.setPlayerCount(world.getPlayers());
        target.setTypes(WorldUtil.toWorldTypes(world.getTypes()));
        if (client.getGameState() == GameState.LOGIN_SCREEN) { client.changeWorld(target); return; }
        hopTarget = target;
        hopAttempts = 0;
        showHopStatus("Opening hopper for " + worldId);
        handleHop();
    }

    private void handleHop()
    {
        if (hopTarget == null) { return; }
        if (!config.hoppingEnabled() || client.getGameState() != GameState.LOGGED_IN)
        { hopTarget = null; return; }
        Widget list = client.getWidget(InterfaceID.Worldswitcher.LIST);
        if (list != null && !list.isHidden())
        {
            client.hopToWorld(hopTarget);
            hopTarget = null;
            showHopStatus("Hop requested — follow game prompts");
            resetCapture();
        }
        else if (++hopAttempts > 4)
        { hopTarget = null; showHopStatus("Finish your action, then retry Hop"); }
        else { client.openWorldHopper(); }
    }

    private void showHopStatus(String message)
    { hopStatus = message; hopStatusUntil = Instant.now().getEpochSecond() + 8; }
}
