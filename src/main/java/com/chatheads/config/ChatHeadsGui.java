package com.chatheads.config;

import com.chatheads.render.HeadRenderer;
import com.chatheads.util.OpenGLHelper;
import com.chatheads.util.ResourceHelper;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/** Settings screen: opened from Mods > Chat Heads > Config, /chatheads, or the (unbound by default) key. */
public class ChatHeadsGui extends GuiScreen {
    private static final int B_ENABLED = 0, B_POS = 1, B_SIZE = 2, B_HAT = 3, B_SHADOW = 4, B_SMOOTH = 5,
            B_NICKS = 6, B_UNKNOWN = 7, B_MULTI = 8, B_SINGLE = 9, B_DONE = 100;

    private final GuiScreen parent;

    /** Forge's config-GUI factory requires this exact constructor. */
    public ChatHeadsGui(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - 155, right = width / 2 + 5, y0 = height / 6 + 10, row = 24;
        buttonList.add(new GuiButton(B_ENABLED, left, y0, 150, 20, ""));
        buttonList.add(new GuiButton(B_POS, right, y0, 150, 20, ""));
        buttonList.add(new GuiButton(B_SIZE, left, y0 + row, 150, 20, ""));
        buttonList.add(new GuiButton(B_HAT, right, y0 + row, 150, 20, ""));
        buttonList.add(new GuiButton(B_SHADOW, left, y0 + row * 2, 150, 20, ""));
        buttonList.add(new GuiButton(B_SMOOTH, right, y0 + row * 2, 150, 20, ""));
        buttonList.add(new GuiButton(B_NICKS, left, y0 + row * 3, 150, 20, ""));
        buttonList.add(new GuiButton(B_UNKNOWN, right, y0 + row * 3, 150, 20, ""));
        buttonList.add(new GuiButton(B_MULTI, left, y0 + row * 4, 150, 20, ""));
        buttonList.add(new GuiButton(B_SINGLE, right, y0 + row * 4, 150, 20, ""));
        buttonList.add(new GuiButton(B_DONE, width / 2 - 100, y0 + row * 5 + 8, 200, 20, "Done"));
        updateLabels();
    }

    private void updateLabels() {
        for (GuiButton b : buttonList) {
            switch (b.id) {
                case B_ENABLED: b.displayString = "Enable Chat Heads: " + onOff(ChatHeadsConfig.enabled); break;
                case B_POS: b.displayString = "Head Position: " + ChatHeadsConfig.headPosition.label; break;
                case B_SIZE: b.displayString = "Head Size: " + ChatHeadsConfig.headSize; break;
                case B_HAT: b.displayString = "Show Hat Layer: " + onOff(ChatHeadsConfig.showHatLayer); break;
                case B_SHADOW: b.displayString = "Head Shadow: " + onOff(ChatHeadsConfig.headShadow); break;
                case B_SMOOTH: b.displayString = "Smooth Rendering: " + onOff(ChatHeadsConfig.smoothRendering); break;
                case B_NICKS: b.displayString = "Custom Nickname Mapping: " + onOff(ChatHeadsConfig.customNicknameMapping); break;
                case B_UNKNOWN: b.displayString = "Unknown Players: " + onOff(ChatHeadsConfig.unknownPlayers); break;
                case B_MULTI: b.displayString = "Chat Heads in Multiplayer: " + onOff(ChatHeadsConfig.inMultiplayer); break;
                case B_SINGLE: b.displayString = "Chat Heads in Singleplayer: " + onOff(ChatHeadsConfig.inSingleplayer); break;
                default: break;
            }
        }
    }

    private static String onOff(boolean v) { return v ? "ON" : "OFF"; }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        switch (b.id) {
            case B_ENABLED: ChatHeadsConfig.enabled = !ChatHeadsConfig.enabled; break;
            case B_POS: ChatHeadsConfig.headPosition = ChatHeadsConfig.headPosition.next(); break;
            case B_SIZE:
                ChatHeadsConfig.headSize = ChatHeadsConfig.headSize >= ChatHeadsConfig.MAX_SIZE
                        ? ChatHeadsConfig.MIN_SIZE : ChatHeadsConfig.headSize + 1;
                break;
            case B_HAT: ChatHeadsConfig.showHatLayer = !ChatHeadsConfig.showHatLayer; break;
            case B_SHADOW: ChatHeadsConfig.headShadow = !ChatHeadsConfig.headShadow; break;
            case B_SMOOTH: ChatHeadsConfig.smoothRendering = !ChatHeadsConfig.smoothRendering; break;
            case B_NICKS: ChatHeadsConfig.customNicknameMapping = !ChatHeadsConfig.customNicknameMapping; break;
            case B_UNKNOWN: ChatHeadsConfig.unknownPlayers = !ChatHeadsConfig.unknownPlayers; break;
            case B_MULTI: ChatHeadsConfig.inMultiplayer = !ChatHeadsConfig.inMultiplayer; break;
            case B_SINGLE: ChatHeadsConfig.inSingleplayer = !ChatHeadsConfig.inSingleplayer; break;
            case B_DONE: mc.displayGuiScreen(parent); return;
            default: break;
        }
        updateLabels();
    }

    @Override
    public void onGuiClosed() { ChatHeadsConfig.save(); }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Chat Heads", width / 2, height / 6 - 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawPreview();
        drawCenteredString(fontRendererObj, "Changes apply to new messages; lines already in chat keep their spacing.",
                width / 2, height - 14, 0x999999);
    }

    /** Live preview using the same renderer as the chat. */
    private void drawPreview() {
        int x = width / 2 - 100;
        int y = height / 6 + 10 + 24 * 5 + 40;
        boolean byName = ChatHeadsConfig.headPosition == ChatHeadsConfig.HeadPosition.BEFORE_NAME;
        int size = ChatHeadsConfig.headSize;
        int cx = x;
        if (byName) {
            fontRendererObj.drawStringWithShadow("\u00a7a[VIP] ", cx, y, 0xFFFFFF);
            cx += fontRendererObj.getStringWidth("[VIP] ");
        }
        ResourceLocation skin = mc.thePlayer != null ? mc.thePlayer.getLocationSkin() : ResourceHelper.defaultSkin(null);
        OpenGLHelper.beginHud();
        HeadRenderer.drawHead(mc, skin, cx, y + 8 - size, size, 1f);
        OpenGLHelper.endHud();
        int tx = cx + size + 2;
        fontRendererObj.drawStringWithShadow(byName ? "Steve\u00a77: Hello!" : (mc.thePlayer != null ? mc.thePlayer.getName() : "Steve") + "\u00a77: Hello!", tx, y, 0xFFFFFF);
    }
}
