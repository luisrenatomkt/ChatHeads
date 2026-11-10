package com.chatheads.player;

import com.chatheads.cache.PlayerCache;
import com.chatheads.cache.SkinCache;
import com.chatheads.chat.ChatMessageParser.Header;
import com.chatheads.client.ChatHeadsClient;
import com.chatheads.config.ChatHeadsConfig;
import com.chatheads.player.ConfidenceModel.Source;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Glue between the parsed header and the real world: manual mappings first, then real account names in TAB,
 * then single-word TAB display names (nicknames). The decision rules live in IdentityScorer (pure, tested).
 */
public final class PlayerIdentityResolver implements IdentityScorer.Lookup<PlayerData> {
    private final Minecraft mc;
    private final PlayerCache cache;
    private final NicknameMapper nicks;
    private final SkinCache skins;
    private PlayerCache.Snapshot snap = null;

    public PlayerIdentityResolver(Minecraft mc, PlayerCache cache, NicknameMapper nicks, SkinCache skins) {
        this.mc = mc; this.cache = cache; this.nicks = nicks; this.skins = skins;
    }

    public IdentityScorer.Match<PlayerData> resolve(Header h) {
        int tick = ChatHeadsClient.tick();
        cache.refreshIfOlderThan(mc, tick, 20);
        snap = cache.snapshot();
        IdentityScorer.Match<PlayerData> m = IdentityScorer.choose(h, this);
        // Unknown name: the player may have joined <1s ago. Rebuild once (rate-limited) and retry.
        if (m.player == null && m.nameLike && tick - cache.lastRefreshTick() >= 5) {
            cache.refresh(mc, tick);
            snap = cache.snapshot();
            m = IdentityScorer.choose(h, this);
        }
        return m;
    }

    @Override
    public IdentityScorer.Hit<PlayerData> find(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (ChatHeadsConfig.customNicknameMapping) {
            NicknameMapper.Target t = nicks.get(lower);
            if (t != null) {
                PlayerData pd = cache.offlineTarget(t.name, t.uuid);
                if (!pd.isOnline() && !pd.hasRealSkin()) skins.request(pd);
                return new IdentityScorer.Hit<PlayerData>(pd, Source.MANUAL);
            }
        }
        PlayerData pd = snap.byName(lower);
        if (pd != null) return new IdentityScorer.Hit<PlayerData>(pd, pd.getName().equals(token) ? Source.REAL_EXACT : Source.REAL_IGNORE_CASE);
        pd = snap.byAlias(lower);
        if (pd != null) return new IdentityScorer.Hit<PlayerData>(pd, Source.ALIAS);
        return null;
    }
}
