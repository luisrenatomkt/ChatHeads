package com.chatheads.chat;

import com.chatheads.client.ChatHeadsClient;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Access to GuiNewChat/GuiIngame private fields. Each lookup passes the MCP name (dev environment) AND the SRG name
 * (production), so it works both in the IDE and in a normal install. If any field cannot be found the mod
 * disables itself cleanly instead of crashing (OK == false).
 */
final class ChatReflection {
    static final Field DRAWN, SOURCES, SENT, SCROLL, IS_SCROLLED, PERSISTENT;
    static final boolean OK;

    static {
        Field drawn = null, sources = null, sent = null, scroll = null, isScrolled = null, persistent = null;
        boolean ok = false;
        try {
            drawn = ReflectionHelper.findField(GuiNewChat.class, "drawnChatLines", "field_146253_i");
            sources = ReflectionHelper.findField(GuiNewChat.class, "chatLines", "field_146252_h");
            sent = ReflectionHelper.findField(GuiNewChat.class, "sentMessages", "field_146248_b");
            scroll = ReflectionHelper.findField(GuiNewChat.class, "scrollPos", "field_146250_j");
            isScrolled = ReflectionHelper.findField(GuiNewChat.class, "isScrolled", "field_146251_k");
            persistent = ReflectionHelper.findField(GuiIngame.class, "persistantChatGUI", "field_73840_e");
            ok = true;
        } catch (Throwable t) {
            ChatHeadsClient.LOG.error("Could not access chat internals; Chat Heads will stay disabled.", t);
        }
        DRAWN = drawn; SOURCES = sources; SENT = sent; SCROLL = scroll; IS_SCROLLED = isScrolled; PERSISTENT = persistent;
        OK = ok;
    }

    private ChatReflection() {}

    @SuppressWarnings("unchecked")
    static List<ChatLine> lines(Field f, Object owner) {
        try { return (List<ChatLine>) f.get(owner); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    @SuppressWarnings("unchecked")
    static List<String> strings(Field f, Object owner) {
        try { return (List<String>) f.get(owner); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    static int getInt(Field f, Object owner) {
        try { return f.getInt(owner); } catch (IllegalAccessException e) { return 0; }
    }
}
