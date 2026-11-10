package com.chatheads.player;

/** Pure scoring rules. Kept separate so they can be tuned (and tested) without touching the game. */
public final class ConfidenceModel {
    private ConfidenceModel() {}

    public enum Source {
        /** User-defined nickname mapping. */
        MANUAL(1.00f),
        /** Token equals a real account name in TAB (case-sensitive). */
        REAL_EXACT(0.95f),
        REAL_IGNORE_CASE(0.90f),
        /** Token equals the single-word display name shown in TAB (nickname / team formatting). */
        ALIAS(0.85f);

        public final float base;
        Source(float base) { this.base = base; }
    }

    public static float score(Source src, boolean angle, int bareBefore, int tokensAfter,
                              int nameLength, int distinctMatches) {
        if (src == Source.MANUAL) return 1.0f;
        float c = src.base;
        if (bareBefore > 0) c -= 0.03f + 0.10f * (bareBefore - 1); // "Guild Steve:" ok, "You killed Steve:" not
        c -= 0.05f * tokensAfter;
        if (nameLength <= 3) c -= 0.05f;
        if (distinctMatches > 1) c -= 0.10f;                      // two different players named in the header
        if (angle) c += 0.03f;
        return Math.max(0f, Math.min(1f, c));
    }
}
