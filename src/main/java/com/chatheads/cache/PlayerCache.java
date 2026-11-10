package com.chatheads.cache;

import com.chatheads.player.PlayerData;
import com.chatheads.util.ResourceHelper;
import com.chatheads.util.TextUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ResourceLocation;

import java.nio.charset.Charset;
import java.util.*;

/**
 * Index of the players we know about: the TAB list (NetworkPlayerInfo) plus nearby player entities as a second
 * source for servers that hide people from TAB. Rebuilt every ~1s, or on demand when a name does not resolve.
 * Entries of players who left are kept (so old chat lines keep their head) until disconnect or LRU eviction.
 * Client-thread only.
 */
public final class PlayerCache {
    private static final int MAX_ENTRIES = 512;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Immutable lookup tables for the players currently online. */
    public static final class Snapshot {
        private final Map<String, PlayerData> names;
        private final Map<String, PlayerData> aliases;
        Snapshot(Map<String, PlayerData> names, Map<String, PlayerData> aliases) { this.names = names; this.aliases = aliases; }
        public PlayerData byName(String lower) { return names.get(lower); }
        public PlayerData byAlias(String lower) { return aliases.get(lower); }
        public int size() { return names.size(); }
    }

    private static final Snapshot EMPTY = new Snapshot(Collections.<String, PlayerData>emptyMap(), Collections.<String, PlayerData>emptyMap());

    private final Map<UUID, PlayerData> byUuid = new HashMap<UUID, PlayerData>();
    private final Map<String, PlayerData> pendingByName = new HashMap<String, PlayerData>();
    private Snapshot snapshot = EMPTY;
    private int lastRefreshTick = -1000;
    private Object lastHandler;

    public Snapshot snapshot() { return snapshot; }
    public int lastRefreshTick() { return lastRefreshTick; }
    public int knownPlayers() { return byUuid.size(); }

    public void refreshIfOlderThan(Minecraft mc, int tickNow, int maxAgeTicks) {
        if (tickNow - lastRefreshTick >= maxAgeTicks) refresh(mc, tickNow);
    }

    public void clear() {
        byUuid.clear();
        pendingByName.clear();
        snapshot = EMPTY;
    }

    public void refresh(Minecraft mc, int tickNow) {
        lastRefreshTick = tickNow;
        NetHandlerPlayClient nh = mc.getNetHandler();
        if (nh != lastHandler) { clear(); lastHandler = nh; }   // new connection: names may map to other people
        if (nh == null || mc.theWorld == null) { snapshot = EMPTY; return; }

        long now = System.currentTimeMillis();
        Set<UUID> online = new HashSet<UUID>();
        Map<String, PlayerData> names = new HashMap<String, PlayerData>();
        Map<String, PlayerData> aliasMap = new HashMap<String, PlayerData>();
        Set<String> ambiguous = new HashSet<String>();

        for (NetworkPlayerInfo info : nh.getPlayerInfoMap()) {
            GameProfile gp = info.getGameProfile();
            if (gp == null || gp.getName() == null || gp.getName().isEmpty()) continue;
            String name = gp.getName();
            UUID id = gp.getId() != null ? gp.getId() : offlineUuid(name);
            PlayerData pd = obtain(id, name);
            pd.markOnline(now);
            online.add(id);
            names.put(name.toLowerCase(Locale.ROOT), pd);
            String alias = tabAlias(info, name);
            if (alias != null) {
                String key = alias.toLowerCase(Locale.ROOT);
                PlayerData prev = aliasMap.put(key, pd);
                if (prev != null && prev != pd) ambiguous.add(key);
            }
        }

        // Second source: player entities in render distance (covers servers that hide players from TAB).
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            UUID id = p.getUniqueID();
            String name = p.getName();
            if (id == null || name == null || online.contains(id)) continue;
            PlayerData pd = obtain(id, name);
            pd.markOnline(now);
            online.add(id);
            String lower = name.toLowerCase(Locale.ROOT);
            if (!names.containsKey(lower)) names.put(lower, pd);
            if (p instanceof AbstractClientPlayer) {
                ResourceLocation skin = ((AbstractClientPlayer) p).getLocationSkin();
                if (!ResourceHelper.isDefaultSkin(skin, id)) pd.setLastRealSkin(skin);
            }
        }

        for (String k : ambiguous) aliasMap.remove(k);          // an alias shared by 2 players identifies nobody
        for (PlayerData pd : byUuid.values()) if (pd.getUuid() == null || !online.contains(pd.getUuid())) pd.setOnline(false);
        evictIfNeeded();
        snapshot = new Snapshot(names, aliasMap);
    }

    /** Player a manual nickname points to (uuid and/or name). May create an offline placeholder entry. */
    public PlayerData offlineTarget(String name, UUID uuid) {
        if (uuid != null) {
            PlayerData pd = byUuid.get(uuid);
            if (pd == null) {
                pd = new PlayerData(uuid, name);
                byUuid.put(uuid, pd);
            }
            return pd;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        PlayerData pd = snapshot.byName(lower);
        if (pd != null) return pd;
        for (PlayerData p : byUuid.values()) if (name.equalsIgnoreCase(p.getName())) return p;
        pd = pendingByName.get(lower);
        if (pd == null) {
            pd = new PlayerData(null, name);
            pendingByName.put(lower, pd);
        }
        return pd;
    }

    private PlayerData obtain(UUID id, String name) {
        PlayerData pd = byUuid.get(id);
        if (pd == null) {
            pd = pendingByName.remove(name.toLowerCase(Locale.ROOT));
            if (pd != null) pd.setUuid(id); else pd = new PlayerData(id, name);
            byUuid.put(id, pd);
        }
        pd.setName(name);
        return pd;
    }

    /** Single-word TAB display name different from the account name = nickname (e.g. Essentials). */
    private static String tabAlias(NetworkPlayerInfo info, String realName) {
        String formatted = info.getDisplayName() != null
                ? info.getDisplayName().getFormattedText()
                : ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(), realName);
        if (formatted == null) return null;
        List<String> words = TextUtil.depthZeroWords(TextUtil.strip(formatted).text, 16);
        if (words.size() != 1) return null;
        String w = words.get(0);
        return (w.length() < 3 || w.equalsIgnoreCase(realName)) ? null : w;
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF8));
    }

    private void evictIfNeeded() {
        if (byUuid.size() <= MAX_ENTRIES) return;
        List<PlayerData> offline = new ArrayList<PlayerData>();
        for (PlayerData pd : byUuid.values()) if (!pd.isOnline()) offline.add(pd);
        Collections.sort(offline, new Comparator<PlayerData>() {
            public int compare(PlayerData a, PlayerData b) { return Long.compare(a.getLastSeen(), b.getLastSeen()); }
        });
        int target = MAX_ENTRIES * 3 / 4;
        for (PlayerData pd : offline) {
            if (byUuid.size() <= target) break;
            byUuid.remove(pd.getUuid());
        }
    }
}
