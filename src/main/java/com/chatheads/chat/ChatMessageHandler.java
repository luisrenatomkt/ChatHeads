package com.chatheads.chat;

import com.chatheads.chat.ChatMessageParser.Header;
import com.chatheads.client.ChatHeadsClient;
import com.chatheads.config.ChatHeadsConfig;
import com.chatheads.player.IdentityScorer;
import com.chatheads.player.PlayerData;
import com.chatheads.player.PlayerIdentityResolver;
import com.chatheads.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.IChatComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Message in -> HeadPlan out (or null = no head). Never touches OpenGL and never does I/O. */
public final class ChatMessageHandler {
    /** Gap between head and text, in chat px. */
    private static final int GAP = 2;

    private final Minecraft mc;
    private final PlayerIdentityResolver resolver;
    private final ChatMessageParser parser = new ChatMessageParser();
    private final Deque<String> debugLog = new ArrayDeque<String>();

    public ChatMessageHandler(Minecraft mc, PlayerIdentityResolver resolver) {
        this.mc = mc;
        this.resolver = resolver;
    }

    public List<String> debugLines() { return new ArrayList<String>(debugLog); }

    public HeadPlan process(IChatComponent msg, FontRenderer fr, int lineWidthPx) {
        if (ChatHeadsClient.isBypass() || !ChatHeadsConfig.shouldRender(mc)) return null;
        String raw = msg.getUnformattedText();
        if (raw == null || raw.length() < 3 || raw.length() > 2048) return null;

        TextUtil.Stripped s = TextUtil.strip(raw);
        Header h = parser.parse(s.text);
        if (h == null) return null;

        IdentityScorer.Match<PlayerData> m = resolver.resolve(h);
        boolean accept = m.player != null && m.confidence + 1e-4f >= ChatHeadsConfig.minConfidence;
        boolean placeholder = !accept && m.player == null && m.nameLike && ChatHeadsConfig.unknownPlayers;
        if (ChatHeadsConfig.debug) record(s.text, m, accept, placeholder);
        if (!accept && !placeholder) return null;   // never attach a head we are not sure about

        int size = ChatHeadsConfig.headSize;
        int spaceW = Math.max(1, fr.getCharWidth(' '));
        int n = (int) Math.ceil((size + GAP) / (double) spaceW);
        int padW = n * spaceW;
        String pad = TextUtil.repeat(' ', n);

        List<ComponentUtil.Run> runs = ComponentUtil.flatten(msg);
        int rawOffset = 0;
        float headX = 0f;
        if (ChatHeadsConfig.headPosition == ChatHeadsConfig.HeadPosition.BEFORE_NAME) {
            int off = s.rawOffset(h.anchorFor(m.token));
            if (off > 0) {
                int before = ComponentUtil.widthBefore(runs, off, fr);
                // keep head + a few chars of the name on the first line, otherwise fall back to "before message"
                if (before + padW + 48 <= lineWidthPx) {
                    rawOffset = off;
                    headX = before;
                }
            }
        }
        IChatComponent out = ComponentUtil.insertPad(runs, rawOffset, pad);
        return new HeadPlan(accept ? m.player : null, !accept, headX, out, accept ? m.confidence : 0f);
    }

    private void record(String text, IdentityScorer.Match<PlayerData> m, boolean accept, boolean placeholder) {
        String line = (accept ? "HEAD " : placeholder ? "PLACEHOLDER " : "SKIP ") + m + " | " + (text.length() > 60 ? text.substring(0, 60) : text);
        debugLog.addLast(line);
        while (debugLog.size() > 12) debugLog.removeFirst();
    }
}
