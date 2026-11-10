package com.chatheads;

import com.chatheads.client.ChatHeadsClient;
import com.chatheads.config.ChatHeadsConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

/**
 * Chat Heads for Minecraft 1.8.9 (Forge 11.15.1.2318). Client-side only: nothing is installed or sent to the server.
 * No coremod / ASM / Mixin: the single integration point is a GuiNewChat subclass (see HeadsGuiNewChat).
 */
@Mod(modid = ChatHeads.MODID, name = ChatHeads.NAME, version = ChatHeads.VERSION,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]", acceptableRemoteVersions = "*",
        guiFactory = "com.chatheads.config.ChatHeadsGuiFactory")
public class ChatHeads {
    public static final String MODID = "chatheads";
    public static final String NAME = "Chat Heads";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        File dir = e.getModConfigurationDirectory();
        ChatHeadsConfig.init(new File(dir, "chatheads.cfg"));
        ChatHeadsClient.preInit(dir);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        ChatHeadsClient.init();
    }
}
