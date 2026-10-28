package com.antivanish;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.util.EnumParticleTypes;

import java.util.UUID;

/** Lee paquetes entrantes sin modificarlos y pasa los datos al Detector en el hilo principal. */
public class PacketWatcher extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof S38PacketPlayerListItem) {
                handleTab((S38PacketPlayerListItem) msg);
            } else if (msg instanceof S02PacketChat) {
                S02PacketChat p = (S02PacketChat) msg;
                if (p.getType() != 2 && p.getChatComponent() != null) {
                    Detector.chat.addLast(new Detector.ChatLine(
                            p.getChatComponent().getUnformattedText(), System.currentTimeMillis()));
                }
            } else if (msg instanceof S2APacketParticles) {
                S2APacketParticles p = (S2APacketParticles) msg;
                final EnumParticleTypes type = p.getParticleType();
                final double x = p.getXCoordinate(), y = p.getYCoordinate(), z = p.getZCoordinate();
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    public void run() { Detector.onParticle(type, x, y, z); }
                });
            }
        } catch (Throwable ignored) {
            // nunca romper la conexion por culpa del detector
        }
        ctx.fireChannelRead(msg);
    }

    private void handleTab(S38PacketPlayerListItem p) {
        S38PacketPlayerListItem.Action action = p.getAction();
        for (S38PacketPlayerListItem.AddPlayerData d : p.getEntries()) {
            if (d.getProfile() == null || d.getProfile().getId() == null) continue;
            final UUID id = d.getProfile().getId();
            if (action == S38PacketPlayerListItem.Action.ADD_PLAYER) {
                String n = d.getProfile().getName();
                if (n != null) Detector.names.put(id, n);
            } else if (action == S38PacketPlayerListItem.Action.REMOVE_PLAYER) {
                final String name = Detector.names.remove(id);
                if (name == null) continue;
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    public void run() { Detector.onTabRemove(id, name); }
                });
            }
        }
    }
}
