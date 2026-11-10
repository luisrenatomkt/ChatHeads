package com.chatheads.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

/** Makes the "Config" button of Forge's Mods screen open our settings screen. */
public class ChatHeadsGuiFactory implements IModGuiFactory {
    @Override public void initialize(Minecraft mc) {}
    @Override public Class<? extends GuiScreen> mainConfigGuiClass() { return ChatHeadsGui.class; }
    @Override public Set<IModGuiFactory.RuntimeOptionCategoryElement> runtimeGuiCategories() { return null; }
    @Override public IModGuiFactory.RuntimeOptionGuiHandler getHandlerFor(IModGuiFactory.RuntimeOptionCategoryElement element) { return null; }
}
