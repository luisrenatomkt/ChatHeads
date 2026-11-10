package com.chatheads.util;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Small GL helpers. Everything here goes through GlStateManager where the game tracks state. */
public final class OpenGLHelper {
    private static boolean lightingWasOn;

    private OpenGLHelper() {}

    /** Puts GL in the state the HUD expects for textured, blended quads. Pair with {@link #endHud()}. */
    public static void beginHud() {
        lightingWasOn = GL11.glIsEnabled(GL11.GL_LIGHTING);
        if (lightingWasOn) GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        blend();
    }

    /** Restores what beginHud changed. Vanilla leaves blend off after drawing chat; so do we. */
    public static void endHud() {
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        if (lightingWasOn) GlStateManager.enableLighting();
    }

    public static void blend() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    /** Switches the bound texture to GL_LINEAR; returns the previous {min, mag} filters for {@link #restoreFilter}. */
    public static int[] setLinearFilter() {
        int[] prev = {
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER),
                GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) };
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        return prev;
    }

    public static void restoreFilter(int[] prev) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, prev[0]);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, prev[1]);
    }

    /** Level-0 size of the currently bound texture, or null if it is not uploaded yet. */
    public static int[] boundTextureSize() {
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        return (w > 0 && h > 0) ? new int[] { w, h } : null;
    }

    public static void quad(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0.0).tex(u0, v1).endVertex();
        wr.pos(x + w, y + h, 0.0).tex(u1, v1).endVertex();
        wr.pos(x + w, y, 0.0).tex(u1, v0).endVertex();
        wr.pos(x, y, 0.0).tex(u0, v0).endVertex();
        t.draw();
    }
}
