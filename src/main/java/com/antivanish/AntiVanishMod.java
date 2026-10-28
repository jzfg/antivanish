package com.antivanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = AntiVanishMod.MODID, name = "AntiVanish", version = "1.0",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class AntiVanishMod {
    public static final String MODID = "antivanishmod";
    private static KeyBinding openGui;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        StaffManager.init(e.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        openGui = new KeyBinding("Abrir AntiVanish", Keyboard.KEY_J, "AntiVanish");
        ClientRegistry.registerKeyBinding(openGui);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(new HudOverlay());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (openGui.isPressed() && mc.currentScreen == null && mc.thePlayer != null) {
            mc.displayGuiScreen(new StaffGui());
        }
        Detector.tick(mc);
    }

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent e) {
        Detector.reset();
        e.manager.channel().pipeline().addBefore("packet_handler", "antivanish_watcher", new PacketWatcher());
    }
}
