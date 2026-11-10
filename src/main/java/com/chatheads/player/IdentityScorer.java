package com.chatheads.player;

import com.chatheads.chat.ChatMessageParser.Header;
import com.chatheads.chat.ChatMessageParser.Token;
import com.chatheads.player.ConfidenceModel.Source;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chooses the sender among the header tokens. Generic over the player type so it is testable without Minecraft.
 * Rules: only the HEADER is searched (never the message body); the matching token closest to the separator wins;
 * names shorter than 3 chars only count for manual mappings.
 */
public final class IdentityScorer {
    private IdentityScorer() {}

    public interface Lookup<T> { Hit<T> find(String token); }

    public static final class Hit<T> {
        public final T player;
        public final Source source;
        public Hit(T player, Source source) { this.player = player; this.source = source; }
    }

    public static final class Match<T> {
        public final T player;              // null = nobody identified
        public final float confidence;
        public final Token token;           // chosen token (or last header token when nobody matched)
        public final Source source;
        /** Header ends in something that could plausibly be a name even though nobody matched. */
        public final boolean nameLike;
        public final int candidates;

        Match(T player, float confidence, Token token, Source source, boolean nameLike, int candidates) {
            this.player = player; this.confidence = confidence; this.token = token;
            this.source = source; this.nameLike = nameLike; this.candidates = candidates;
        }

        @Override public String toString() {
            return player == null ? "no-match" : (source + " conf=" + confidence + " token=" + token.text);
        }
    }

    public static <T> Match<T> choose(Header h, Lookup<T> lookup) {
        List<Token> tokens = h.tokens;
        Token last = tokens.get(tokens.size() - 1);
        boolean nameLike = last.text.length() >= 3 && last.tokensAfter == 0 && last.bareBefore <= 2;

        List<Token> foundTokens = new ArrayList<Token>();
        List<Hit<T>> foundHits = new ArrayList<Hit<T>>();
        Map<Object, Boolean> distinct = new IdentityHashMap<Object, Boolean>();
        for (Token t : tokens) {
            Hit<T> hit = lookup.find(t.text);
            if (hit == null) continue;
            if (hit.source != Source.MANUAL && t.text.length() < 3) continue;
            foundTokens.add(t);
            foundHits.add(hit);
            distinct.put(hit.player, Boolean.TRUE);
        }
        if (foundTokens.isEmpty()) return new Match<T>(null, 0f, last, null, nameLike, 0);

        int i = foundTokens.size() - 1;
        Token t = foundTokens.get(i);
        Hit<T> hit = foundHits.get(i);
        float conf = ConfidenceModel.score(hit.source, h.angle, t.bareBefore, t.tokensAfter,
                t.text.length(), distinct.size());
        return new Match<T>(hit.player, conf, t, hit.source, nameLike, foundTokens.size());
    }
}
