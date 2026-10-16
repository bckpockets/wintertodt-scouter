package com.wintertodt.scouter;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureLifecycleTest
{
    @Test public void hoppingAndMissingWidgetsInvalidateLocalData() throws Exception
    {
        WintertodtScouterPlugin plugin = new WintertodtScouterPlugin();
        int[] world = {307};
        GameState[] state = {GameState.LOGGED_IN};
        boolean[] visible = {true};
        String[] text = {"Wintertodt's Energy: 82%"};
        Player player = proxy(Player.class, (method, args) -> method.equals("getWorldLocation") ? new WorldPoint(1630, 3980, 0) : null);
        Widget widget = proxy(Widget.class, (method, args) -> method.equals("isHidden") ? !visible[0] : method.equals("getText") ? text[0] : null);
        Client client = proxy(Client.class, (method, args) ->
        {
            switch (method)
            {
                case "getGameState": return state[0];
                case "getLocalPlayer": return player;
                case "getWorld": return world[0];
                case "getWidget": return widget;
                case "getVarbitValue": return 0;
                default: return null;
            }
        });
        set(plugin, "client", client);
        set(plugin, "running", true);
        Field field = WintertodtScouterPlugin.class.getDeclaredField("store");
        field.setAccessible(true);
        ObservationStore store = (ObservationStore) field.get(plugin);
        plugin.onGameTick(new GameTick()); plugin.onGameTick(new GameTick());
        assertNull(store.getLocal());
        plugin.onGameTick(new GameTick());
        assertEquals(307, store.getLocal().world);
        state[0] = GameState.HOPPING;
        GameStateChanged hop = new GameStateChanged();
        hop.setGameState(GameState.HOPPING);
        plugin.onGameStateChanged(hop);
        assertNull(store.getLocal());
        world[0] = 309;
        state[0] = GameState.LOGGED_IN;
        plugin.onGameTick(new GameTick()); plugin.onGameTick(new GameTick());
        assertNull(store.getLocal());
        text[0] = "Wintertodt's Energy: 45%";
        plugin.onGameTick(new GameTick());
        assertEquals(309, store.getLocal().world);
        assertEquals(45, store.getLocal().energy);
        visible[0] = false;
        plugin.onGameTick(new GameTick());
        assertNull(store.getLocal());
        state[0] = GameState.LOGIN_SCREEN;
        plugin.onGameTick(new GameTick());
        assertNull(store.getLocal());
    }

    private interface Call { Object invoke(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (instance, method, args) -> call.invoke(method.getName(), args));
    }
    private static void set(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
