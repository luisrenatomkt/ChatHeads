package com.chatheads;

import com.chatheads.chat.ChatMessageParser;
import com.chatheads.chat.ChatMessageParser.Header;
import com.chatheads.player.ConfidenceModel.Source;
import com.chatheads.player.IdentityScorer;
import com.chatheads.player.IdentityScorer.Hit;
import com.chatheads.player.IdentityScorer.Match;
import com.chatheads.util.TextUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Plain main()-based test (no JUnit needed). Exit code 1 on failure. Pure logic only. */
public class LogicSmokeTest {
    static int fails = 0, total = 0;
    static final float MIN = 0.85f;

    static final Map<String, String> REAL = new HashMap<String, String>();   // lower -> real
    static final Map<String, String> ALIAS = new HashMap<String, String>();  // lower -> real
    static final Map<String, String> MANUAL = new HashMap<String, String>(); // lower -> real

    static IdentityScorer.Lookup<String> lookup = new IdentityScorer.Lookup<String>() {
        public Hit<String> find(String token) {
            String l = token.toLowerCase(Locale.ROOT);
            if (MANUAL.containsKey(l)) return new Hit<String>(MANUAL.get(l), Source.MANUAL);
            String r = REAL.get(l);
            if (r != null) return new Hit<String>(r, r.equals(token) ? Source.REAL_EXACT : Source.REAL_IGNORE_CASE);
            String a = ALIAS.get(l);
            if (a != null) return new Hit<String>(a, Source.ALIAS);
            return null;
        }
    };

    /** expected = real name that must get the head, or null = must NOT get a head. */
    static void check(String raw, String expected) {
        total++;
        ChatMessageParser p = new ChatMessageParser();
        Header h = p.parse(TextUtil.strip(raw).text);
        String got = null;
        String detail = "unparsed";
        if (h != null) {
            Match<String> m = IdentityScorer.choose(h, lookup);
            detail = m.toString();
            if (m.player != null && m.confidence + 1e-4f >= MIN) got = m.player;
        }
        boolean ok = (expected == null) ? got == null : expected.equals(got);
        if (!ok) fails++;
        System.out.println((ok ? "PASS " : "FAIL ") + String.format("%-44s -> %-8s (%s)", raw.replace('\u00a7', '&'), got, detail));
    }

    public static void main(String[] a) {
        for (String n : new String[] { "Steve", "Alex", "Notch", "Bob" }) REAL.put(n.toLowerCase(Locale.ROOT), n);
        ALIAS.put("cool_nick", "Alex");
        MANUAL.put("player123", "Notch");

        check("Steve: Ol\u00e1!", "Steve");
        check("[VIP] Steve: Ol\u00e1!", "Steve");
        check("<Steve> Ol\u00e1!", "Steve");
        check("[MVP+] Steve \u00bb Ol\u00e1!", "Steve");
        check("Guild Steve: Ol\u00e1!", "Steve");
        check("Steve [VIP] >> gg", "Steve");
        check("Steve -> hi", "Steve");
        check("Steve > hi", "Steve");
        check("[12:30] Steve: hi", "Steve");
        check("12:30 Steve: hi", "Steve");
        check("<[Red] Steve> hi", "Steve");
        check("[Lvl 10] - Steve: hi", "Steve");
        check("\u00a7a[VIP] \u00a7bSteve\u00a77: hi", "Steve");
        check("steve: lowercase", "Steve");
        check("Bob: short name", "Bob");
        check("Alex: tell Steve hi", "Alex");          // body must never be searched
        check("[Steve] Alex: hi", "Alex");              // bracketed tag is not a name
        check("Cool_Nick: hi", "Alex");                 // alias
        check("[VIP] Player123: hi", "Notch");          // manual mapping
        // must NOT get a head
        check("Steve joined the game", null);
        check("You killed Steve: +5 coins", null);      // low confidence
        check("Steve Alex: ambiguous", null);           // two players in header
        check("Server: restarting soon", null);         // unknown name
        check("Unknown123: hi", null);
        check("Steve: i <3 you", "Steve");
        check("hello world", null);

        // raw offset mapping + format carry (used when splitting text for "head before name")
        total++;
        String raw = "\u00a7a[VIP] \u00a7bSteve\u00a77: hi";
        TextUtil.Stripped s = TextUtil.strip(raw);
        int idx = s.text.indexOf("Steve");
        int rawOff = s.rawOffset(idx);
        boolean ok = raw.startsWith("Steve", rawOff) && TextUtil.activeFormatting(raw, rawOff).equals("\u00a7b");
        if (!ok) fails++;
        System.out.println((ok ? "PASS " : "FAIL ") + "rawOffset/activeFormatting");

        System.out.println(total - fails + "/" + total + " passed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
