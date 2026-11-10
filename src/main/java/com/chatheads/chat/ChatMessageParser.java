package com.chatheads.chat;

import com.chatheads.util.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds the "header" of a chat line (the part before the separator) and extracts the word tokens that could be a
 * player name. It never decides WHO the sender is - that is the job of the resolver, which checks the tokens
 * against the TAB list. Works on text already stripped of formatting codes. Pure Java.
 *
 * Supported shapes (brackets/parentheses/braces groups are treated as rank/tag decoration, never as names):
 *   Steve: hi    [VIP] Steve: hi    &lt;Steve&gt; hi    [MVP+] Steve » hi
 *   Guild Steve: hi    Steve [VIP] &gt;&gt; hi    Steve -&gt; hi    Steve &gt; hi
 */
public final class ChatMessageParser {
    public static final int MAX_NAME = 16;
    private static final int MAX_HEADER = 72;

    public static final class Token {
        public final String text;
        public final int start;
        public final int end;
        /** Depth-0 word tokens before this one (rank words, guild words...). */
        public final int bareBefore;
        /** Depth-0 word tokens after this one but before the separator. */
        public final int tokensAfter;

        Token(String text, int start, int end, int bareBefore, int tokensAfter) {
            this.text = text; this.start = start; this.end = end;
            this.bareBefore = bareBefore; this.tokensAfter = tokensAfter;
        }
    }

    public static final class Header {
        public final List<Token> tokens;
        public final boolean angle;
        /** Index of '<' in angle form, otherwise -1. */
        public final int angleAnchor;
        public final int separatorIndex;

        Header(List<Token> tokens, boolean angle, int angleAnchor, int separatorIndex) {
            this.tokens = Collections.unmodifiableList(tokens);
            this.angle = angle; this.angleAnchor = angleAnchor; this.separatorIndex = separatorIndex;
        }

        /** Stripped-text index where a "head before name" should be inserted for the chosen token. */
        public int anchorFor(Token t) { return angle ? angleAnchor : t.start; }
    }

    /** @return null if the line does not look like a player chat line at all. */
    public Header parse(String text) {
        if (text == null) return null;
        int nl = text.indexOf('\n');
        String line = nl >= 0 ? text.substring(0, nl) : text;
        if (line.trim().isEmpty()) return null;
        Header h = parseAngle(line);
        return h != null ? h : parseSeparator(line);
    }

    private Header parseAngle(String line) {
        int limit = Math.min(line.length(), MAX_HEADER);
        int depth = 0;
        for (int i = 0; i < limit; i++) {
            char c = line.charAt(i);
            if (TextUtil.isOpen(c)) depth++;
            else if (TextUtil.isClose(c)) { if (depth > 0) depth--; }
            else if (depth == 0) {
                if (c != '<' && sepLen(line, i) > 0) return null;   // "Steve: i <3 you" is not angle form
                if (c == '<') {
                    int close = line.indexOf('>', i + 1);
                    if (close < 0 || close - i > 40) return null;
                    List<int[]> inside = spans(line, i + 1, close);
                    if (inside.isEmpty()) return null;
                    int prefix = spans(line, 0, i).size();
                    return new Header(tokens(line, inside, prefix), true, i, close);
                }
            }
        }
        return null;
    }

    private Header parseSeparator(String line) {
        int limit = Math.min(line.length(), MAX_HEADER + 4);
        int depth = 0;
        for (int i = 0; i < limit; i++) {
            char c = line.charAt(i);
            if (TextUtil.isOpen(c)) { depth++; continue; }
            if (TextUtil.isClose(c)) { if (depth > 0) depth--; continue; }
            if (depth != 0) continue;
            int len = sepLen(line, i);
            if (len == 0) continue;
            if (i > MAX_HEADER) return null;
            List<int[]> spans = spans(line, 0, i);
            if (!spans.isEmpty()) return new Header(tokens(line, spans, 0), false, -1, i);
            i += len - 1; // separator with no name before it (e.g. "[Lvl 10] - Steve: hi"): keep looking
        }
        return null;
    }

    /** Word spans minus clock fragments: in "12:30 Steve: hi" the 12 and 30 are a timestamp, not rank words. */
    private static List<int[]> spans(String line, int from, int to) {
        List<int[]> all = TextUtil.wordSpans(line, from, to, MAX_NAME);
        List<int[]> out = new ArrayList<int[]>(all.size());
        for (int[] sp : all) {
            boolean digits = true;
            for (int k = sp[0]; k < sp[1]; k++) if (!Character.isDigit(line.charAt(k))) { digits = false; break; }
            boolean nearColon = (sp[0] > 0 && line.charAt(sp[0] - 1) == ':')
                    || (sp[1] < line.length() && line.charAt(sp[1]) == ':' && sp[1] < to);
            if (digits && nearColon) continue;
            out.add(sp);
        }
        return out;
    }

    private static List<Token> tokens(String line, List<int[]> spans, int prefixCount) {
        List<Token> out = new ArrayList<Token>(spans.size());
        int n = spans.size();
        for (int k = 0; k < n; k++) {
            int[] sp = spans.get(k);
            out.add(new Token(line.substring(sp[0], sp[1]), sp[0], sp[1], prefixCount + k, n - 1 - k));
        }
        return out;
    }

    /** Length of a separator starting at i, or 0. */
    static int sepLen(String s, int i) {
        char c = s.charAt(i);
        int n = s.length();
        switch (c) {
            case ':':
                return (i > 0 && (i + 1 >= n || Character.isWhitespace(s.charAt(i + 1)))) ? 1 : 0;
            case '\u00bb': case '\u203a': case '\u2192': case '\u27a4': case '\u25b6': case '\u279c':
                return i > 0 ? 1 : 0;
            case '>':
                if (i > 0 && i + 1 < n && s.charAt(i + 1) == '>') return 2;
                return spaced(s, i) ? 1 : 0;
            case '-':
                if (i > 0 && i + 1 < n && s.charAt(i + 1) == '>') return 2;
                return spaced(s, i) ? 1 : 0;
            case '|':
                return spaced(s, i) ? 1 : 0;
            default:
                return 0;
        }
    }

    private static boolean spaced(String s, int i) {
        return i > 0 && Character.isWhitespace(s.charAt(i - 1))
                && (i + 1 >= s.length() || Character.isWhitespace(s.charAt(i + 1)));
    }
}
