package com.chatheads.player;

import net.minecraft.util.ResourceLocation;

import java.util.UUID;

/**
 * Everything we know about one player. Fields are volatile because the skin fetcher thread writes some of them;
 * all other access happens on the client thread.
 */
public final class PlayerData {
    private volatile UUID uuid;                 // null while we only know a name (manual mapping, not yet looked up)
    private volatile String name;
    private volatile boolean online;
    private volatile long lastSeen;
    /** Last NON-default skin seen while the player was online: survives the player leaving. */
    private volatile ResourceLocation lastRealSkin;
    /** Skin fetched by SkinCache for players who are not online (manual mappings). */
    private volatile ResourceLocation remoteSkin;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID u) { this.uuid = u; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean o) { this.online = o; }
    public long getLastSeen() { return lastSeen; }
    public void markOnline(long now) { this.online = true; this.lastSeen = now; }
    public ResourceLocation getLastRealSkin() { return lastRealSkin; }
    public void setLastRealSkin(ResourceLocation r) { this.lastRealSkin = r; }
    public ResourceLocation getRemoteSkin() { return remoteSkin; }
    public void setRemoteSkin(ResourceLocation r) { this.remoteSkin = r; }
    public boolean hasRealSkin() { return lastRealSkin != null || remoteSkin != null; }
}
