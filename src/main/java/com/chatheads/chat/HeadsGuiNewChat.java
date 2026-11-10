package com.chatheads.chat;

import com.chatheads.client.ChatHeadsClient;
import com.chatheads.config.ChatHeadsConfig;
import com.chatheads.render.HeadRenderer;
import com.chatheads.render.SkinTextureManager;
import com.chatheads.util.OpenGLHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/**
 * The single integration point with vanilla chat.
 *
 *  - printChatMessageWithOptionalDeletion: decides whether the message gets a head; if so, prints a copy with blank
 *    space reserved and tags the resulting lines.
 *  - drawChat: lets vanilla draw EVERYTHING exactly as before (super call), then draws only the heads on top using
 *    the same transform and the same fade/opacity maths. Vanilla drawing code is not copied or altered, so scroll,
 *    scrollbar, opacity, scale, width, colours, hover and click behave exactly like vanilla.
 *  - refreshChat: re-tags lines after vanilla rebuilt them (resize, width/scale change).
 */
public class HeadsGuiNewChat extends GuiNewChat {
    /** Vanilla draws the chat after translate(2, 20, 0) + scale(chatScale). */
    private static final float ORIGIN_X = 2.0f, ORIGIN_Y = 20.0f;

    private final Minecraft mc;
    private final List<ChatLine> drawn;
    private final List<ChatLine> sources;
    private final ChatLineManager lines = new ChatLineManager();
    private int drawFailures;

    public HeadsGuiNewChat(Minecraft mc, GuiNewChat old) throws IllegalAccessException {
        super(mc);
        this.mc = mc;
        this.drawn = ChatReflection.lines(ChatReflection.DRAWN, this);
        this.sources = ChatReflection.lines(ChatReflection.SOURCES, this);
        if (old != null) { // keep history, sent-message recall and scroll position across the swap
            drawn.addAll(ChatReflection.lines(ChatReflection.DRAWN, old));
            sources.addAll(ChatReflection.lines(ChatReflection.SOURCES, old));
            ChatReflection.strings(ChatReflection.SENT, this).addAll(ChatReflection.strings(ChatReflection.SENT, old));
            ChatReflection.SCROLL.setInt(this, ChatReflection.SCROLL.getInt(old));
            ChatReflection.IS_SCROLLED.setBoolean(this, ChatReflection.IS_SCROLLED.getBoolean(old));
        }
        lines.adopt(sources, drawn);
    }

    // ---------------------------------------------------------------- incoming messages

    @Override
    public void printChatMessageWithOptionalDeletion(IChatComponent component, int id) {
        HeadPlan plan = null;
        IChatComponent toPrint = component;
        if (component != null && !ChatHeadsClient.isDisabled()) {
            try {
                int width = MathHelper.floor_float((float) getChatWidth() / getChatScale());
                plan = ChatHeadsClient.handler().process(component, mc.fontRendererObj, width);
                if (plan != null) toPrint = plan.component;
            } catch (Throwable t) {
                ChatHeadsClient.reportFailure("process message", t);
                plan = null;
                toPrint = component;
            }
        }
        super.printChatMessageWithOptionalDeletion(toPrint, id);
        try {
            lines.afterPrint(sources, drawn, toPrint, plan);
        } catch (Throwable t) {
            ChatHeadsClient.reportFailure("tag lines", t);
        }
    }

    @Override
    public void refreshChat() {
        super.refreshChat();
        try {
            lines.rebuild(sources, drawn, MathHelper.floor_float((float) getChatWidth() / getChatScale()), mc.fontRendererObj);
        } catch (Throwable t) {
            ChatHeadsClient.reportFailure("rebuild lines", t);
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void drawChat(int updateCounter) {
        super.drawChat(updateCounter);
        if (drawFailures >= 5 || ChatHeadsClient.isDisabled()) return;
        try {
            drawHeads(updateCounter);
        } catch (Throwable t) {
            drawFailures++;
            ChatHeadsClient.reportFailure("draw heads", t);
        }
    }

    private void drawHeads(int updateCounter) {
        if (mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) return;
        if (!ChatHeadsConfig.shouldRender(mc)) return;
        int total = drawn.size();
        if (total == 0) return;

        boolean open = getChatOpen();
        int maxLines = getLineCount();
        int scroll = ChatReflection.getInt(ChatReflection.SCROLL, this);
        float opacity = mc.gameSettings.chatOpacity * 0.9F + 0.1F;
        float scale = getChatScale();
        int size = ChatHeadsConfig.headSize;

        boolean begun = false;
        try {
            for (int i = 0; i + scroll < total && i < maxLines; i++) {
                ChatLine line = drawn.get(i + scroll);
                if (line == null) continue;
                HeadPlan plan = lines.planFor(line);
                if (plan == null) continue;

                // same fade maths as vanilla's drawChat
                int age = updateCounter - line.getUpdatedCounter();
                if (age >= 200 && !open) continue;
                double d0 = 1.0D - (double) age / 200.0D;
                d0 = MathHelper.clamp_double(d0 * 10.0D, 0.0D, 1.0D);
                d0 *= d0;
                int a = open ? 255 : (int) (255.0D * d0);
                a = (int) ((float) a * opacity);
                if (a <= 3) continue;

                if (!begun) {
                    begun = true;
                    OpenGLHelper.beginHud();
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(ORIGIN_X, ORIGIN_Y, 0.0F);
                    GlStateManager.scale(scale, scale, 1.0F);
                }
                float x = plan.headX;
                float y = -i * 9 - 9 + (9 - size);   // bottom-aligned inside the 9px line
                float alpha = a / 255.0f;
                if (plan.placeholder) {
                    HeadRenderer.drawPlaceholder(x, y, size, alpha);
                } else {
                    ResourceLocation skin = SkinTextureManager.getSkin(mc, plan.player);
                    HeadRenderer.drawHead(mc, skin, x, y, size, alpha);
                }
            }
        } finally {
            if (begun) {
                GlStateManager.popMatrix();
                OpenGLHelper.endHud();
            }
        }
    }
}
