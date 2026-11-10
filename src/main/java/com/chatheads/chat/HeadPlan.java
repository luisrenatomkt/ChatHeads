package com.chatheads.chat;

import com.chatheads.player.PlayerData;
import net.minecraft.util.IChatComponent;

/** Decision taken for one incoming chat message. Immutable. */
public final class HeadPlan {
    /** Marker stored for lines that never get a head. */
    public static final HeadPlan NONE = new HeadPlan(null, false, 0f, null, 0f);

    public final PlayerData player;       // null for placeholder
    public final boolean placeholder;
    /** X offset (chat px) of the head inside the first line. 0 for "before message". */
    public final float headX;
    /** Component to print (original content + blank space reserved for the head). */
    public final IChatComponent component;
    public final float confidence;

    public HeadPlan(PlayerData player, boolean placeholder, float headX, IChatComponent component, float confidence) {
        this.player = player; this.placeholder = placeholder; this.headX = headX;
        this.component = component; this.confidence = confidence;
    }
}
