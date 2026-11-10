package com.chatheads.config;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/** All user options. Persisted with Forge's Configuration (config/chatheads.cfg). Main-thread only. */
public final class ChatHeadsConfig {
    public enum HeadPosition {
        BEFORE_MESSAGE("Before message"), BEFORE_NAME("Before name");
        public final String label;
        HeadPosition(String label) { this.label = label; }
        public HeadPosition next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static final int MIN_SIZE = 6, MAX_SIZE = 9; // chat lines are 9px tall

    public static boolean enabled = true;
    public static HeadPosition headPosition = HeadPosition.BEFORE_MESSAGE;
    public static int headSize = 8;
    public static boolean showHatLayer = true;
    public static boolean headShadow = true;
    public static boolean smoothRendering = false;
    public static boolean customNicknameMapping = true;
    public static boolean unknownPlayers = false;
    public static boolean inMultiplayer = true;
    public static boolean inSingleplayer = true;
    /** Minimum confidence (0.5..1) for attaching a head. Config-file only. */
    public static float minConfidence = 0.85f;
    public static boolean debug = false;

    private static final String C = Configuration.CATEGORY_GENERAL;
    private static Configuration cfg;

    private ChatHeadsConfig() {}

    public static void init(File file) {
        cfg = new Configuration(file);
        load();
    }

    public static void load() {
        enabled = cfg.getBoolean("enabled", C, true, "Master switch.");
        String pos = cfg.getString("headPosition", C, "BEFORE_MESSAGE",
                "Where the head goes: BEFORE_MESSAGE or BEFORE_NAME.", new String[] { "BEFORE_MESSAGE", "BEFORE_NAME" });
        try { headPosition = HeadPosition.valueOf(pos); } catch (IllegalArgumentException ex) { headPosition = HeadPosition.BEFORE_MESSAGE; }
        headSize = cfg.getInt("headSize", C, 8, MIN_SIZE, MAX_SIZE, "Head size in chat pixels (6-9; a chat line is 9px tall).");
        showHatLayer = cfg.getBoolean("showHatLayer", C, true, "Draw the skin's outer head layer.");
        headShadow = cfg.getBoolean("headShadow", C, true, "Draw a soft shadow behind the head.");
        smoothRendering = cfg.getBoolean("smoothRendering", C, false, "Bilinear filtering instead of crisp pixels.");
        customNicknameMapping = cfg.getBoolean("customNicknameMapping", C, true, "Use /chatheads nick mappings.");
        unknownPlayers = cfg.getBoolean("unknownPlayers", C, false,
                "Show a grey placeholder for chat-like lines whose sender could not be identified (never a wrong skin).");
        inMultiplayer = cfg.getBoolean("inMultiplayer", C, true, "Show heads on servers.");
        inSingleplayer = cfg.getBoolean("inSingleplayer", C, true, "Show heads in singleplayer.");
        minConfidence = cfg.getFloat("minConfidence", C, 0.85f, 0.5f, 1.0f,
                "Minimum identification confidence. Lower = more heads but more risk of a wrong one.");
        debug = cfg.getBoolean("debug", C, false, "Record identification details for /chatheads why.");
        if (cfg.hasChanged()) cfg.save();
    }

    public static void save() {
        if (cfg == null) return;
        cfg.get(C, "enabled", true).set(enabled);
        cfg.get(C, "headPosition", "BEFORE_MESSAGE").set(headPosition.name());
        cfg.get(C, "headSize", 8).set(headSize);
        cfg.get(C, "showHatLayer", true).set(showHatLayer);
        cfg.get(C, "headShadow", true).set(headShadow);
        cfg.get(C, "smoothRendering", false).set(smoothRendering);
        cfg.get(C, "customNicknameMapping", true).set(customNicknameMapping);
        cfg.get(C, "unknownPlayers", false).set(unknownPlayers);
        cfg.get(C, "inMultiplayer", true).set(inMultiplayer);
        cfg.get(C, "inSingleplayer", true).set(inSingleplayer);
        cfg.get(C, "minConfidence", "0.85").set(Float.toString(minConfidence));
        cfg.get(C, "debug", false).set(debug);
        cfg.save();
    }

    public static boolean shouldRender(Minecraft mc) {
        return enabled && (mc.isSingleplayer() ? inSingleplayer : inMultiplayer);
    }
}
