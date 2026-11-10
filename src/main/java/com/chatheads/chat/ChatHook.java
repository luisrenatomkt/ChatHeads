package com.chatheads.chat;

import com.chatheads.client.ChatHeadsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Swaps GuiIngame.persistantChatGUI for our subclass (once). We only replace a plain vanilla GuiNewChat: if another
 * mod already installed its own subclass we do NOT fight it - we log a warning and stay inactive.
 */
public final class ChatHook {
    private ChatHook() {}

    /** @return true when finished (installed, or deliberately given up); false when the GUI does not exist yet. */
    public static boolean tryInstall(Minecraft mc) {
        GuiIngame gui = mc.ingameGUI;
        if (gui == null) return false;
        if (!ChatReflection.OK) {
            ChatHeadsClient.setStatus("inactive: could not access chat internals (see log)");
            return true;
        }
        GuiNewChat cur = gui.getChatGUI();
        if (cur instanceof HeadsGuiNewChat) return true;
        if (cur.getClass() != GuiNewChat.class) {
            String msg = "inactive: chat already replaced by " + cur.getClass().getName();
            ChatHeadsClient.LOG.warn("Chat Heads " + msg);
            ChatHeadsClient.setStatus(msg);
            return true;
        }
        try {
            ChatReflection.PERSISTENT.set(gui, new HeadsGuiNewChat(mc, cur));
            ChatHeadsClient.setStatus("active");
            ChatHeadsClient.LOG.info("Chat hook installed.");
        } catch (Throwable t) {
            ChatHeadsClient.reportFailure("install chat hook", t);
            ChatHeadsClient.setStatus("inactive: hook failed (see log)");
        }
        return true;
    }
}
