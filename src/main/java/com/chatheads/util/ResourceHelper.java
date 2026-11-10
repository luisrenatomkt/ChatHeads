package com.chatheads.util;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;

public final class ResourceHelper {
    private ResourceHelper() {}

    public static ResourceLocation defaultSkin(UUID id) {
        return id != null ? DefaultPlayerSkin.getDefaultSkin(id) : DefaultPlayerSkin.getDefaultSkinLegacy();
    }

    public static boolean isDefaultSkin(ResourceLocation loc, UUID id) {
        return loc == null || loc.equals(defaultSkin(id));
    }
}
