package com.chatheads.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Java text helpers (no Minecraft imports, so they are unit-testable outside the game).
 * Everything that deals with the legacy "section sign" formatting codes lives here.
 */
public final class TextUtil {
    public static final char SECTION = '\u00A7';

    private TextUtil() {}

    /** Text without formatting codes + a map from stripped index back to the raw index. */
    public static final class Stripped {
        public final String text;
        private final int[] rawIndex;

        private Stripped(String text, int[] rawIndex) {
            this.text = text;
            this.rawIndex = rawIndex;
        }

        /** Raw (with codes) offset of the character at the given stripped index. */
        public int rawOffset(int strippedIndex) {
            if (strippedIndex < 0) return 0;
            if (strippedIndex >= rawIndex.length) return rawIndex[rawIndex.length - 1];
            return rawIndex[strippedIndex];
        }
    }

    /** Mirrors FontRenderer: a section sign swallows the next character, valid code or not. */
    public static Stripped strip(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        int[] map = new int[raw.length() + 1];
        int n = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == SECTION && i + 1 < raw.length()) {
                i++;
                continue;
            }
            map[n++] = i;
            sb.append(c);
        }
        map[n] = raw.length();
        return new Stripped(sb.toString(), Arrays.copyOf(map, n + 1));
    }

    public static boolean isOpen(char c) { return c == '[' || c == '(' || c == '{'; }
    public static boolean isClose(char c) { return c == ']' || c == ')' || c == '}'; }

    public static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    /**
     * [start,end) spans of word tokens in s[from,to) that are NOT inside brackets.
     * Words longer than maxLen are discarded (Minecraft names are at most 16 chars).
     */
    public static List<int[]> wordSpans(String s, int from, int to, int maxLen) {
        List<int[]> out = new ArrayList<int[]>();
        int depth = 0;
        int i = from;
        while (i < to) {
            char c = s.charAt(i);
            if (isOpen(c)) { depth++; i++; }
            else if (isClose(c)) { if (depth > 0) depth--; i++; }
            else if (depth == 0 && isWordChar(c)) {
                int j = i;
                while (j < to && isWordChar(s.charAt(j))) j++;
                if (j - i <= maxLen) out.add(new int[] { i, j });
                i = j;
            } else {
                i++;
            }
        }
        return out;
    }

    public static List<String> depthZeroWords(String s, int maxLen) {
        List<String> words = new ArrayList<String>();
        for (int[] sp : wordSpans(s, 0, s.length(), maxLen)) words.add(s.substring(sp[0], sp[1]));
        return words;
    }

    /**
     * Formatting that is "active" at raw[0,end), as a string of section codes. Used when a text run is split
     * in two, so the second half keeps the colour/style it would have had.
     */
    public static String activeFormatting(String raw, int end) {
        char color = 0;
        boolean[] fmt = new boolean[5]; // k l m n o
        for (int i = 0; i + 1 < end; i++) {
            if (raw.charAt(i) != SECTION) continue;
            char c = Character.toLowerCase(raw.charAt(i + 1));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                color = c;
                Arrays.fill(fmt, false);
            } else if (c == 'r') {
                color = 0;
                Arrays.fill(fmt, false);
            } else if (c >= 'k' && c <= 'o') {
                fmt[c - 'k'] = true;
            }
            i++;
        }
        StringBuilder sb = new StringBuilder();
        if (color != 0) sb.append(SECTION).append(color);
        for (int k = 0; k < fmt.length; k++) if (fmt[k]) sb.append(SECTION).append((char) ('k' + k));
        return sb.toString();
    }

    public static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
