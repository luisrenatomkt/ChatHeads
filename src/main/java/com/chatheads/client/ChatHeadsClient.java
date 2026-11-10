package com.chatheads.client;

import com.chatheads.cache.PlayerCache;
import com.chatheads.cache.SkinCache;
import com.chatheads.chat.ChatHook;
import com.chatheads.chat.ChatMessageHandler;
import com.chatheads.command.ChatHeadsCommand;
import com.chatheads.config.ChatHeadsGui;
import com.chatheads.player.NicknameMapper;
import com.chatheads.player.PlayerIdentityResolver;
import com.chatheads.render.HeadRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;

/** Wiring + client-thread lifecycle. Holds the singletons; everything here runs on the client thread. */
public final class ChatHeadsClient {
    public static final Logger LOG = LogManager.getLogger("ChatHeads");
    private static final int MAX_FAILURES = 5;

    private static File configDir;
    private static PlayerCache cache;
    private static SkinCache skins;
    private static NicknameMapper nicknames;
    private static ChatMessageHandler handler;
    private static KeyBinding settingsKey;

    private static int tick;
    private static boolean hookDone;
    private static boolean bypass;
    private static boolean disabled;
    private static boolean openGuiRequested;
    private static int failures;
    private static String status = "waiting for the in-game GUI";

    private ChatHeadsClient() {}

    public static void preInit(File dir) { configDir = dir; }

    public static void init() {
        Minecraft mc = Minecraft.getMinecraft();
        cache = new PlayerCache();
        skins = new SkinCache();
        nicknames = new NicknameMapper(new File(configDir, "chatheads_nicknames.properties"));
        handler = new ChatMessageHandler(mc, new PlayerIdentityResolver(mc, cache, nicknames, skins));

        settingsKey = new KeyBinding("key.chatheads.settings", Keyboard.KEY_NONE, "key.categories.misc");
        ClientRegistry.registerKeyBinding(settingsKey);
        ClientCommandHandler.instance.registerCommand(new ChatHeadsCommand());
        FMLCommonHandler.instance().bus().register(new Events());
    }

    // ---- accessors
    public static ChatMessageHandler handler() { return handler; }
    public static PlayerCache cache() { return cache; }
    public static SkinCache skins() { return skins; }
    public static NicknameMapper nicknames() { return nicknames; }
    public static int tick() { return tick; }
    public static String status() { return disabled ? "disabled after repeated errors (see log)" : status; }
    public static void setStatus(String s) { status = s; }
    public static boolean isDisabled() { return disabled; }
    public static boolean isBypass() { return bypass; }
    public static void requestOpenGui() { openGuiRequested = true; }   // opened next tick: GuiChat closes itself after a command

    /** Our own feedback lines must never be parsed as player chat. */
    public static void withBypass(Runnable r) {
        bypass = true;
        try { r.run(); } finally { bypass = false; }
    }

    /** Isolates failures: a bug in Chat Heads must never take the game down; after 5 errors it switches itself off. */
    public static void reportFailure(String what, Throwable t) {
        LOG.error("Chat Heads error while trying to " + what, t);
        if (++failures >= MAX_FAILURES && !disabled) {
            disabled = true;
            LOG.error("Chat Heads disabled for this session after {} errors.", MAX_FAILURES);
        }
    }

    public static void resetFailures() { failures = 0; disabled = false; }

    /** Event handlers (FML bus). */
    public static final class Events {
        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            tick++;
            if (!hookDone) hookDone = ChatHook.tryInstall(mc);
            if (openGuiRequested && mc.currentScreen == null) {
                openGuiRequested = false;
                mc.displayGuiScreen(new ChatHeadsGui(null));
            }
            if (mc.theWorld != null && tick % 20 == 0 && !disabled) {
                try { cache.refresh(mc, tick); } catch (Throwable t) { reportFailure("refresh player list", t); }
            }
        }

        @SubscribeEvent
        public void onKey(InputEvent.KeyInputEvent e) {
            if (settingsKey.isPressed()) requestOpenGui();
        }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
            // new server = new identities. Skin textures stay owned by vanilla's SkinManager.
            cache.clear();
            HeadRenderer.clearCache();
        }
    }
}
