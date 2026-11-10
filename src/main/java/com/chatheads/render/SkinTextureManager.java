package com.chatheads.render;

import com.chatheads.player.PlayerData;
import com.chatheads.util.ResourceHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;

/**
 * Chooses which texture to draw for a player, every frame, without any I/O.
 * Priority: live NetworkPlayerInfo skin (vanilla loads it asynchronously and returns the UUID's default skin until
 * ready, so the head upgrades by itself) > last real skin seen > skin fetched for manual mappings > default skin.
 */
public final class SkinTextureManager {
    private SkinTextureManager() {}

    public static ResourceLocation getSkin(Minecraft mc, PlayerData pd) {
        UUID id = pd.getUuid();
        NetHandlerPlayClient nh = mc.getNetHandler();
        if (nh != null && id != null) {
            NetworkPlayerInfo info = nh.getPlayerInfo(id);
            if (info != null) {
                ResourceLocation r = info.getLocationSkin();
                if (!ResourceHelper.isDefaultSkin(r, id)) pd.setLastRealSkin(r);
                return r;
            }
        }
        ResourceLocation r = pd.getLastRealSkin();
        if (r != null) return r;
        r = pd.getRemoteSkin();
        if (r != null) return r;
        return ResourceHelper.defaultSkin(id);
    }
}
