package com.chatheads.render;

import com.chatheads.config.ChatHeadsConfig;
import com.chatheads.util.OpenGLHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a player's face (+ optional hat layer) from a skin texture. Call between OpenGLHelper.beginHud()/endHud().
 *
 * Skin UV layout (64 wide): face = (8,8)-(16,16), hat = (40,8)-(48,16). Vanilla's own steve.png is a LEGACY 64x32
 * texture while network skins are converted to 64x64, so the V coordinates are normalised by the real texture
 * height (queried once per texture and cached) instead of assuming 64x64.
 */
public final class HeadRenderer {
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<ResourceLocation, int[]>();

    private HeadRenderer() {}

    public static void drawHead(Minecraft mc, ResourceLocation skin, float x, float y, float size, float alpha) {
        if (skin == null) return;
        mc.getTextureManager().bindTexture(skin);
        int[] d = SIZES.get(skin);
        if (d == null) {
            d = OpenGLHelper.boundTextureSize();
            if (d == null) return;                       // not uploaded yet: skip this frame, retry next one
            if (SIZES.size() > 512) SIZES.clear();
            SIZES.put(skin, d);
        }
        boolean legacy = d[1] * 2 <= d[0];               // 64x32 style layout
        float vDiv = legacy ? 32f : 64f;
        float fu0 = 8f / 64f, fu1 = 16f / 64f, hu0 = 40f / 64f, hu1 = 48f / 64f;
        float v0 = 8f / vDiv, v1 = 16f / vDiv;

        OpenGLHelper.blend();
        GlStateManager.enableTexture2D();
        int[] prevFilter = ChatHeadsConfig.smoothRendering ? OpenGLHelper.setLinearFilter() : null;
        try {
            if (ChatHeadsConfig.headShadow) {
                GlStateManager.color(0f, 0f, 0f, alpha * 0.5f);
                OpenGLHelper.quad(x + 1f, y + 1f, size, size, fu0, v0, fu1, v1);
            }
            GlStateManager.color(1f, 1f, 1f, alpha);
            OpenGLHelper.quad(x, y, size, size, fu0, v0, fu1, v1);
            if (ChatHeadsConfig.showHatLayer) OpenGLHelper.quad(x, y, size, size, hu0, v0, hu1, v1);
        } finally {
            if (prevFilter != null) OpenGLHelper.restoreFilter(prevFilter);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    /** Neutral grey square for "looks like a player but could not be identified". Never a skin. */
    public static void drawPlaceholder(float x, float y, float size, float alpha) {
        int a = (int) (alpha * 150f) & 0xFF;
        Gui.drawRect((int) x, (int) y, (int) (x + size), (int) (y + size), (a << 24) | 0x707070);
        OpenGLHelper.blend(); // Gui.drawRect turns blending off when it finishes
    }

    public static void clearCache() { SIZES.clear(); }
}
