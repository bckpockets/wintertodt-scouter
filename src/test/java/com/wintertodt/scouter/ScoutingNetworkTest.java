package com.wintertodt.scouter;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoutingNetworkTest
{
    @Test public void retriesAreBoundedAndCancellationResetsBackoff() throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v2/worlds", exchange ->
        {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        ScoutingNetwork network = new ScoutingNetwork(new OkHttpClient());
        try
        {
            HttpUrl url = HttpUrl.parse("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/worlds");
            CountDownLatch first = new CountDownLatch(1);
            AtomicReference<ScoutingNetwork.Result> result = new AtomicReference<>();
            network.poll(url, value -> { result.set(value); first.countDown(); });
            assertTrue(first.await(5, TimeUnit.SECONDS));
            assertFalse(result.get().success);
            network.poll(url, value -> fail("Backoff should suppress the request"));
            assertEquals(1, calls.get());
            network.cancel();
            CountDownLatch second = new CountDownLatch(1);
            network.poll(url, value -> second.countDown());
            assertTrue(second.await(5, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        }
        finally { network.cancel(); server.stop(0); }
    }

    @Test public void rejectsOversizedResponseAndClosesIt() throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/worlds", exchange ->
        {
            byte[] body = new byte[129 * 1024];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ScoutingNetwork network = new ScoutingNetwork(new OkHttpClient());
        try
        {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<ScoutingNetwork.Result> result = new AtomicReference<>();
            network.poll(HttpUrl.parse("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/worlds"),
                value -> { result.set(value); done.countDown(); });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals("Service response too large", result.get().status);
        }
        finally { network.cancel(); server.stop(0); }
    }
}
