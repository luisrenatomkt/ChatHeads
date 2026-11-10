package com.chatheads.cache;

import com.chatheads.client.ChatHeadsClient;
import com.chatheads.player.PlayerData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Skins for players who are NOT online (only needed for manual nickname mappings: online players get their skin
 * from vanilla's NetworkPlayerInfo, which already downloads, caches on disk and uploads textures for us).
 *
 * All network I/O runs on 2 daemon threads, never on the render/client thread. Requests are de-duplicated and
 * failures are remembered for 10 minutes. Texture registration is handed back to vanilla's SkinManager on the
 * client thread; vanilla keeps the texture (and its on-disk cache), so we never create or leak textures.
 */
public final class SkinCache {
    private static final long FAIL_BACKOFF_MS = 10L * 60 * 1000;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final ExecutorService pool = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int n;
        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ChatHeads-SkinFetch-" + (n++));
            t.setDaemon(true);
            return t;
        }
    });
    private final ConcurrentMap<String, Long> inFlight = new ConcurrentHashMap<String, Long>();
    private final ConcurrentMap<String, Long> failedUntil = new ConcurrentHashMap<String, Long>();
    private final ConcurrentMap<String, UUID> uuidByName = new ConcurrentHashMap<String, UUID>();

    /** Cheap to call every message: returns immediately if already fetched / in flight / backing off. */
    public void request(final PlayerData pd) {
        if (pd.hasRealSkin()) return;
        final String key = pd.getName() == null ? String.valueOf(pd.getUuid()) : pd.getName().toLowerCase();
        Long until = failedUntil.get(key);
        if (until != null) {
            if (System.currentTimeMillis() < until) return;
            failedUntil.remove(key);
        }
        if (inFlight.putIfAbsent(key, System.currentTimeMillis()) != null) return;
        try {
            pool.submit(new Runnable() { public void run() { fetch(pd, key); } });
        } catch (RejectedExecutionException e) {
            inFlight.remove(key);
        }
    }

    public void shutdown() { pool.shutdownNow(); }

    private void fetch(final PlayerData pd, String key) {
        try {
            final Minecraft mc = Minecraft.getMinecraft();
            UUID id = pd.getUuid();
            if (id == null) {
                id = uuidByName.get(key);
                if (id == null) {
                    id = lookupUuid(pd.getName());
                    if (id != null) uuidByName.put(key, id);
                }
            }
            if (id == null) { fail(key, "unknown account " + pd.getName()); return; }
            pd.setUuid(id);

            GameProfile profile = new GameProfile(id, pd.getName());
            GameProfile filled = mc.getSessionService().fillProfileProperties(profile, true); // blocking: worker thread
            final GameProfile toLoad = filled != null ? filled : profile;
            mc.addScheduledTask(new Runnable() {
                public void run() {
                    mc.getSkinManager().loadProfileTextures(toLoad, new SkinManager.SkinAvailableCallback() {
                        public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation loc, MinecraftProfileTexture tex) {
                            if (type == MinecraftProfileTexture.Type.SKIN) pd.setRemoteSkin(loc);
                        }
                    }, true);
                }
            });
        } catch (Throwable t) {
            fail(key, t.toString());
            return;
        }
        inFlight.remove(key);
    }

    private void fail(String key, String why) {
        failedUntil.put(key, System.currentTimeMillis() + FAIL_BACKOFF_MS);
        inFlight.remove(key);
        ChatHeadsClient.LOG.debug("Skin lookup failed for {}: {}", key, why);
    }

    /** Mojang profile API, called on a worker thread only. */
    private static UUID lookupUuid(String name) throws IOException {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, "UTF-8"));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("User-Agent", "ChatHeads/1.0 (Minecraft 1.8.9)");
        try {
            int code = c.getResponseCode();
            if (code == 204 || code == 404) return null;
            if (code != 200) throw new IOException("HTTP " + code);
            Reader r = new InputStreamReader(c.getInputStream(), UTF8);
            try {
                JsonObject o = new JsonParser().parse(r).getAsJsonObject();
                String hex = o.get("id").getAsString();
                return UUID.fromString(hex.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
            } finally {
                r.close();
            }
        } finally {
            c.disconnect();
        }
    }
}
