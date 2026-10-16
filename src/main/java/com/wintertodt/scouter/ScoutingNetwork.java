package com.wintertodt.scouter;

import com.google.gson.Gson;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class ScoutingNetwork
{
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private final OkHttpClient http;
    private final Gson gson;
    private Call download;
    private Call upload;
    private long nextDownload;
    private long nextUpload;
    private int downloadFailures;
    private int uploadFailures;

    @Inject
    ScoutingNetwork(OkHttpClient http, Gson gson)
    {
        this.gson = gson;
        this.http = http.newBuilder().callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build();
    }

    static HttpUrl endpoint(String base)
    {
        HttpUrl url = HttpUrl.parse(base.trim());
        if (url == null || !url.isHttps() || !url.username().isEmpty() || !url.password().isEmpty()
            || url.query() != null || url.fragment() != null)
        { throw new IllegalArgumentException("Use an HTTPS service URL without credentials or query parameters"); }
        String path = url.encodedPath();
        return url.newBuilder().encodedPath(path + (path.endsWith("/") ? "" : "/") + "v2/worlds").build();
    }

    synchronized void cancel()
    {
        if (download != null) { download.cancel(); }
        if (upload != null) { upload.cancel(); }
        download = null;
        upload = null;
        nextDownload = nextUpload = 0;
        downloadFailures = uploadFailures = 0;
    }

    synchronized void poll(HttpUrl url, Consumer<Result> callback)
    {
        if (download != null || System.nanoTime() < nextDownload) { return; }
        Call call = http.newCall(new Request.Builder().url(url)
            .header("Accept", "application/json").header("User-Agent", "WintertodtScouter/2.0").build());
        download = call;
        call.enqueue(new Callback()
        {
            @Override public void onFailure(Call call, IOException error) { finish(call, Result.failure("Service unavailable")); }
            @Override public void onResponse(Call call, Response response)
            {
                Result result;
                try (Response closed = response)
                {
                    if (!closed.isSuccessful() || closed.body() == null)
                    { result = Result.failure("Service HTTP " + closed.code()); }
                    else if (closed.body().source().request(MAX_RESPONSE_BYTES + 1L))
                    { result = Result.failure("Service response too large"); }
                    else
                    {
                        List<Observation> observations = ScoutingProtocol.decode(gson, closed.body().string(), Instant.now().getEpochSecond());
                        result = new Result(observations, "Connected", true);
                    }
                }
                catch (IOException | RuntimeException error) { result = Result.failure("Invalid service response"); }
                finish(call, result);
            }
            private void finish(Call call, Result result)
            {
                synchronized (ScoutingNetwork.this)
                {
                    if (download != call) { return; }
                    download = null;
                    downloadFailures = result.success ? 0 : Math.min(5, downloadFailures + 1);
                    nextDownload = System.nanoTime() + TimeUnit.SECONDS.toNanos(result.success ? 5 : 5L << downloadFailures);
                }
                callback.accept(result);
            }
        });
    }

    synchronized void upload(HttpUrl url, Observation observation, Consumer<Boolean> callback)
    {
        if (upload != null || System.nanoTime() < nextUpload || !observation.isFresh(Instant.now().getEpochSecond())) { return; }
        Call call = http.newCall(new Request.Builder().url(url).header("User-Agent", "WintertodtScouter/2.0")
            .post(RequestBody.create(MediaType.parse("application/json"), ScoutingProtocol.encode(gson, observation))).build());
        upload = call;
        call.enqueue(new Callback()
        {
            @Override public void onFailure(Call call, IOException error) { finish(call, false); }
            @Override public void onResponse(Call call, Response response)
            {
                try (Response closed = response) { finish(call, closed.isSuccessful()); }
            }
            private void finish(Call call, boolean success)
            {
                synchronized (ScoutingNetwork.this)
                {
                    if (upload != call) { return; }
                    upload = null;
                    uploadFailures = success ? 0 : Math.min(5, uploadFailures + 1);
                    nextUpload = System.nanoTime() + TimeUnit.SECONDS.toNanos(success ? 5 : 5L << uploadFailures);
                }
                callback.accept(success);
            }
        });
    }

    static final class Result
    {
        final List<Observation> observations;
        final String status;
        final boolean success;
        Result(List<Observation> observations, String status, boolean success)
        { this.observations = observations; this.status = status; this.success = success; }
        static Result failure(String message) { return new Result(Collections.emptyList(), message, false); }
    }
}
