package com.chatheads.chat;

import com.chatheads.util.TextUtil;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds a chat component with blank space reserved for the head.
 *
 * Why spaces instead of shifting the text while drawing: vanilla then does line wrapping, background width and
 * click/hover hit-testing for us, with exactly the same geometry (those all measure the real text). The component is
 * flattened with IChatComponent's own iterator, which yields each piece with its EFFECTIVE style (colour, bold,
 * click and hover events are copied), and re-assembled under an empty root, so links and tooltips keep working.
 */
public final class ComponentUtil {
    private ComponentUtil() {}

    public static final class Run {
        final String text;
        final ChatStyle style;
        Run(String text, ChatStyle style) { this.text = text; this.style = style; }
    }

    public static List<Run> flatten(IChatComponent c) {
        List<Run> out = new ArrayList<Run>();
        for (IChatComponent part : c) {
            String t = part.getUnformattedTextForChat();
            if (t == null || t.isEmpty()) continue;
            out.add(new Run(t, part.getChatStyle().createDeepCopy()));
        }
        return out;
    }

    /** Pixel width of the text that precedes rawOffset (used to place a head "before the name"). */
    public static int widthBefore(List<Run> runs, int rawOffset, FontRenderer fr) {
        ChatComponentText tmp = new ChatComponentText("");
        int pos = 0;
        for (Run r : runs) {
            if (pos >= rawOffset) break;
            int take = Math.min(r.text.length(), rawOffset - pos);
            tmp.appendSibling(text(r.text.substring(0, take), r.style));
            pos += r.text.length();
        }
        return fr.getStringWidth(tmp.getFormattedText());
    }

    /** Inserts `pad` (spaces) at rawOffset of the concatenated raw text; rawOffset <= 0 means "at the very start". */
    public static IChatComponent insertPad(List<Run> runs, int rawOffset, String pad) {
        ChatComponentText root = new ChatComponentText("");
        boolean done = false;
        if (rawOffset <= 0) {
            root.appendSibling(padComponent(pad));
            done = true;
        }
        int pos = 0;
        for (Run r : runs) {
            int len = r.text.length();
            if (!done && rawOffset < pos + len) {
                int k = rawOffset - pos;
                if (k > 0) root.appendSibling(text(r.text.substring(0, k), r.style));
                root.appendSibling(padComponent(pad));
                // the second half must keep the colour/format that was active at the split point
                root.appendSibling(text(TextUtil.activeFormatting(r.text, k) + r.text.substring(k), r.style));
                done = true;
            } else {
                root.appendSibling(text(r.text, r.style));
            }
            pos += len;
        }
        if (!done) root.appendSibling(padComponent(pad));
        return root;
    }

    private static IChatComponent text(String s, ChatStyle style) {
        ChatComponentText t = new ChatComponentText(s);
        t.setChatStyle(style.createDeepCopy());
        return t;
    }

    /** Plain style: no click/hover/underline, so the blank area is inert. */
    private static IChatComponent padComponent(String pad) {
        ChatComponentText t = new ChatComponentText(pad);
        t.setChatStyle(new ChatStyle());
        return t;
    }
}
