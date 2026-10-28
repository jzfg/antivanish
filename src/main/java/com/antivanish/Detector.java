package com.antivanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumParticleTypes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/** Logica de deteccion. Todo lo que toca el mundo corre en el hilo principal. */
public final class Detector {
    private Detector() {}

    // ---- Ajustes (edita aqui) ----
    static final boolean WATCH_ALL_PARTICLES = false;
    static final Set<EnumParticleTypes> WATCHED = EnumSet.of(
            EnumParticleTypes.CRIT, EnumParticleTypes.CRIT_MAGIC,
            EnumParticleTypes.SMOKE_NORMAL, EnumParticleTypes.SMOKE_LARGE);
    static final double PARTICLE_RANGE = 24.0;   // solo particulas cerca de ti
    static final double CAUSE_RADIUS = 5.0;      // si hay una entidad visible tan cerca, se ignora
    static final double STAFF_RANGE = 48.0;      // bloques a la redonda en los que un staff de tu lista salta
    static final long ALERT_MS = 8000;
    static final int PARTICLE_COOLDOWN_TICKS = 60;
    static final int CHECK_DELAY_TICKS = 30;     // 1.5 s tras salir de TAB
    static final long CHAT_KEEP_MS = 10000;
    private static final String[] LEAVE_KEYS = {
            "left", "quit", "disconnected", "logged out", "[-]",
            "salio", "sali\u00f3", "se fue", "desconect", "abandon"
    };
    private static final String PREFIX = "\u00a7c[AntiVanish] \u00a7f";
    private static final Pattern FORMAT = Pattern.compile("(?i)\u00a7[0-9a-fk-or]");

    // ---- Estado compartido con el hilo de red ----
    static final Deque<ChatLine> chat = new ConcurrentLinkedDeque<ChatLine>();
    static final Map<UUID, String> names = new ConcurrentHashMap<UUID, String>();
    public static final List<Alert> alerts = new CopyOnWriteArrayList<Alert>();

    // ---- Estado solo del hilo principal ----
    private static final List<Pending> pending = new ArrayList<Pending>();
    private static final Map<String, Long> staffVanishedAt = new HashMap<String, Long>();
    private static boolean serverUsesLeaveMessages = false;
    private static int tick = 0;
    private static int lastParticleTick = -1000;

    public static final class ChatLine {
        final String text; final long at;
        ChatLine(String text, long at) { this.text = text; this.at = at; }
    }

    public static final class Alert {
        public final String key; public final String text; public volatile long expiresAt;
        Alert(String key, String text, long expiresAt) { this.key = key; this.text = text; this.expiresAt = expiresAt; }
    }

    private static final class Pending {
        final UUID uuid; final String name; final long atMs; final int dueTick;
        Pending(UUID uuid, String name, long atMs, int dueTick) {
            this.uuid = uuid; this.name = name; this.atMs = atMs; this.dueTick = dueTick;
        }
    }

    public static void reset() {
        chat.clear(); names.clear(); alerts.clear();
        pending.clear(); staffVanishedAt.clear();
        serverUsesLeaveMessages = false; tick = 0; lastParticleTick = -1000;
    }

    // ---------------------------------------------------------------- tick
    public static void tick(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        tick++;

        if (tick % 40 == 0) refreshNames(mc);

        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (tick >= p.dueTick) { it.remove(); evaluate(mc, p); }
        }

        if (tick % 10 == 0) scanStaffNearby(mc);

        if (tick % 20 == 0) {
            long now = System.currentTimeMillis();
            while (!chat.isEmpty() && now - chat.peekFirst().at > CHAT_KEEP_MS) chat.pollFirst();
            for (Alert a : alerts) if (a.expiresAt < now) alerts.remove(a);
        }
    }

    private static void refreshNames(Minecraft mc) {
        if (mc.getNetHandler() == null) return;
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info.getGameProfile() != null && info.getGameProfile().getId() != null
                    && info.getGameProfile().getName() != null) {
                names.put(info.getGameProfile().getId(), info.getGameProfile().getName());
            }
        }
    }

    // ------------------------------------------------------- salida de TAB
    public static void onTabRemove(UUID uuid, String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || name.equalsIgnoreCase(mc.thePlayer.getName())) return;
        pending.add(new Pending(uuid, name, System.currentTimeMillis(), tick + CHECK_DELAY_TICKS));
    }

    private static void evaluate(Minecraft mc, Pending p) {
        // si ya volvio a TAB no es vanish
        if (mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(p.uuid) != null) return;

        if (hadLeaveMessage(p.name, p.atMs)) {
            serverUsesLeaveMessages = true;   // el servidor si avisa de las salidas
            return;
        }
        EntityPlayer ent = findPlayer(mc, p.uuid, p.name);
        boolean nearby = ent != null && mc.thePlayer.getDistanceToEntity(ent) <= STAFF_RANGE;

        if (nearby) {
            reportVanish(p.name, "su entidad sigue cerca tras salir de TAB");
        } else if (serverUsesLeaveMessages) {
            reportVanish(p.name, "salio de TAB sin mensaje de salida");
        }
    }

    private static boolean hadLeaveMessage(String name, long atMs) {
        for (ChatLine l : chat) {
            if (l.at >= atMs - 5000 && l.at <= atMs + 2500 && looksLikeLeave(l.text, name)) return true;
        }
        return false;
    }

    private static boolean looksLikeLeave(String text, String name) {
        String t = FORMAT.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
        if (!t.contains(name.toLowerCase(Locale.ROOT))) return false;
        for (String k : LEAVE_KEYS) if (t.contains(k)) return true;
        return false;
    }

    private static EntityPlayer findPlayer(Minecraft mc, UUID uuid, String name) {
        for (EntityPlayer e : mc.theWorld.playerEntities) {
            if (e == mc.thePlayer) continue;
            if (e.getUniqueID().equals(uuid) || e.getName().equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    private static void reportVanish(String name, String reason) {
        chat(PREFIX + "Posible vanish: \u00a7e" + name + " \u00a77(" + reason + ")");
        if (StaffManager.isStaff(name)) {
            staffVanishedAt.put(name.toLowerCase(Locale.ROOT), System.currentTimeMillis());
            addAlert(name, name + " te est\u00e1 viendo", true);
        }
    }

    // ------------------------------------------------------ staff cercano
    private static void scanStaffNearby(Minecraft mc) {
        for (EntityPlayer e : mc.theWorld.playerEntities) {
            if (e == mc.thePlayer) continue;
            if (!StaffManager.isStaff(e.getName())) continue;
            if (mc.thePlayer.getDistanceToEntity(e) > STAFF_RANGE) continue;
            addAlert(e.getName(), e.getName() + " te est\u00e1 viendo", true);
        }
    }

    /** Crea, refresca o actualiza una alerta de pantalla. */
    private static void addAlert(String name, String text, boolean announce) {
        long exp = System.currentTimeMillis() + ALERT_MS;
        for (Alert a : alerts) {
            if (a.key.equalsIgnoreCase(name)) {
                if (a.text.equals(text)) { a.expiresAt = exp; return; }
                alerts.remove(a);
                break;
            }
        }
        alerts.add(new Alert(name, text, exp));
        if (announce) chat(PREFIX + "\u00a7e" + text);
    }

    // ---------------------------------------------------------- particulas
    public static void onParticle(EnumParticleTypes type, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || type == null) return;
        if (!WATCH_ALL_PARTICLES && !WATCHED.contains(type)) return;
        if (tick - lastParticleTick < PARTICLE_COOLDOWN_TICKS) return;

        double dist = mc.thePlayer.getDistance(x, y, z);
        if (dist > PARTICLE_RANGE) return;

        // si hay una entidad viva y visible cerca, la particula tiene causa visible
        double r2 = CAUSE_RADIUS * CAUSE_RADIUS;
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (e instanceof EntityLivingBase && !e.isInvisible() && e.getDistanceSq(x, y, z) <= r2) return;
        }

        lastParticleTick = tick;
        String extra = "";
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> en : staffVanishedAt.entrySet()) {
            if (now - en.getValue() < 60000) { extra = " \u00a77(staff en vanish: \u00a7e" + en.getKey() + "\u00a77)"; break; }
        }
        chat(PREFIX + "Part\u00edculas sin origen visible: \u00a7e" + type.getParticleName()
                + " \u00a77a " + String.format(Locale.ROOT, "%.1f", dist) + " bloques" + extra);
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }
}
