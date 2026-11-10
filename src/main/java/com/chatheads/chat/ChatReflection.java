package com.chatheads.chat;

import com.chatheads.client.ChatHeadsClient;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Access to GuiNewChat/GuiIngame private fields.
 *
 * Each field is located in two steps, so a wrong SRG name can no longer disable the mod:
 *   1. by name (MCP name for the dev environment, then SRG candidates for a normal install);
 *   2. fallback by declared TYPE and POSITION (vanilla declares sentMessages, chatLines, drawnChatLines - in that
 *      order - as the only List fields, then one int scrollPos and one boolean isScrolled; GuiIngame has exactly
 *      one GuiNewChat field). The fallback is logged so we know which path was used.
 * If something still cannot be found the mod disables itself cleanly (OK == false) instead of crashing.
 */
final class ChatReflection {
    static final Field DRAWN, SOURCES, SENT, SCROLL, IS_SCROLLED, PERSISTENT;
    static final boolean OK;

    static {
        Field drawn = null, sources = null, sent = null, scroll = null, isScrolled = null, persistent = null;
        boolean ok = false;
        try {
            sent = locate(GuiNewChat.class, List.class, 0, "sentMessages", "field_146248_g", "field_146248_b");
            sources = locate(GuiNewChat.class, List.class, 1, "chatLines", "field_146252_h");
            drawn = locate(GuiNewChat.class, List.class, 2, "drawnChatLines", "field_146253_i");
            scroll = locate(GuiNewChat.class, int.class, 0, "scrollPos", "field_146250_j");
            isScrolled = locate(GuiNewChat.class, boolean.class, 0, "isScrolled", "field_146251_k");
            persistent = locate(GuiIngame.class, GuiNewChat.class, 0, "persistantChatGUI", "field_73840_e");
            ok = true;
        } catch (Throwable t) {
            ChatHeadsClient.LOG.error("Could not access chat internals; Chat Heads will stay disabled.", t);
        }
        DRAWN = drawn; SOURCES = sources; SENT = sent; SCROLL = scroll; IS_SCROLLED = isScrolled; PERSISTENT = persistent;
        OK = ok;
    }

    private ChatReflection() {}

    private static Field locate(Class<?> owner, Class<?> type, int index, String... names) {
        try {
            return ReflectionHelper.findField(owner, names);
        } catch (Throwable byName) {
            int seen = 0;
            for (Field f : owner.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != type) continue;
                if (seen++ == index) {
                    f.setAccessible(true);
                    ChatHeadsClient.LOG.warn("Field '{}' not found by name in {}; using field #{} of type {} instead ({}).",
                            names[0], owner.getSimpleName(), index, type.getSimpleName(), f.getName());
                    return f;
                }
            }
            throw new IllegalStateException("Cannot locate field '" + names[0] + "' in " + owner.getName(), byName);
        }
    }

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
