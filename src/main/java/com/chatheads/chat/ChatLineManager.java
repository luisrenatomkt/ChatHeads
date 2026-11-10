package com.chatheads.chat;

import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.util.IChatComponent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Remembers which drawn chat line carries a head.
 *
 * Vanilla splits a message into several "drawn" ChatLines (wrapping). We tag the FIRST wrapped line of a message
 * (highest index in vanilla's newest-first list). Maps are weak (ChatLine has identity equality), so lines that
 * vanilla discards are forgotten automatically: no leak, no cleanup code. After vanilla's refreshChat() every drawn
 * line is a new object, so the tags are rebuilt from the source lines (which are reused).
 */
public final class ChatLineManager {
    private final Map<ChatLine, HeadPlan> drawnMeta = new WeakHashMap<ChatLine, HeadPlan>();
    private final Map<ChatLine, HeadPlan> sourceMeta = new WeakHashMap<ChatLine, HeadPlan>();

    /** Lines that existed before we hooked in: known, but without heads. */
    public void adopt(List<ChatLine> sources, List<ChatLine> drawn) {
        for (ChatLine l : sources) sourceMeta.put(l, HeadPlan.NONE);
        for (ChatLine l : drawn) drawnMeta.put(l, HeadPlan.NONE);
    }

    /** Call right after vanilla added a message. New drawn lines are the untagged ones at the head of the list. */
    public void afterPrint(List<ChatLine> sources, List<ChatLine> drawn, IChatComponent printed, HeadPlan plan) {
        HeadPlan p = plan != null ? plan : HeadPlan.NONE;
        if (!sources.isEmpty()) {
            ChatLine s0 = sources.get(0);
            if (s0.getChatComponent() == printed && !sourceMeta.containsKey(s0)) sourceMeta.put(s0, p);
        }
        int n = 0;
        while (n < drawn.size() && n < 64 && !drawnMeta.containsKey(drawn.get(n))) n++;
        for (int i = 0; i < n; i++) drawnMeta.put(drawn.get(i), (i == n - 1) ? p : HeadPlan.NONE);
    }

    /** Call after vanilla's refreshChat(). If the structure does not line up we tag nothing rather than guess. */
    public void rebuild(List<ChatLine> sources, List<ChatLine> drawn, int widthPx, FontRenderer fr) {
        drawnMeta.clear();
        int[] counts = new int[sources.size()];
        int total = 0;
        for (int s = 0; s < sources.size(); s++) {
            counts[s] = GuiUtilRenderComponents.splitText(sources.get(s).getChatComponent(), widthPx, fr, false, false).size();
            total += counts[s];
        }
        boolean aligned = total >= drawn.size();
        int d = 0;
        for (int s = 0; s < sources.size() && d < drawn.size(); s++) {
            HeadPlan p = sourceMeta.get(sources.get(s));
            for (int j = 0; j < counts[s] && d + j < drawn.size(); j++) {
                boolean first = (j == counts[s] - 1);
                drawnMeta.put(drawn.get(d + j), (aligned && first && p != null) ? p : HeadPlan.NONE);
            }
            d += counts[s];
        }
    }

    /** @return plan if this drawn line is the first line of a message with a head, else null. */
    public HeadPlan planFor(ChatLine drawnLine) {
        HeadPlan p = drawnMeta.get(drawnLine);
        return (p == null || p == HeadPlan.NONE) ? null : p;
    }
}
