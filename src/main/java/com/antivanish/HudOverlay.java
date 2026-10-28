package com.antivanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Dibuja "X te esta viendo" en pantalla. */
public class HudOverlay {

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;

        long now = System.currentTimeMillis();
        int cx = e.resolution.getScaledWidth() / 2;
        float scale = 1.5f;
        int row = 0;

        for (Detector.Alert a : Detector.alerts) {
            if (a.expiresAt < now) continue;
            String s = "\u00a7c\u00a7l" + a.text;
            int w = mc.fontRendererObj.getStringWidth(s);
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1.0f);
            mc.fontRendererObj.drawStringWithShadow(s, cx / scale - w / 2.0f, (36 + row * 14) / scale, 0xFFFFFF);
            GlStateManager.popMatrix();
            row++;
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
