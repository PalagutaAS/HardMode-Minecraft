package net.elrid.hardmode.client;

import net.elrid.hardmode.network.SafeZoneNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ClientZoneState {

    private static volatile double cx, cy, cz;
    private static volatile float radius;
    private static volatile long lastUpdateMs;

    private ClientZoneState() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                SafeZoneNetworking.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    cx = payload.x();
                    cy = payload.y();
                    cz = payload.z();
                    radius = payload.radius();
                    lastUpdateMs = System.currentTimeMillis();
                })
        );
    }

    /** Данные свежие (пришли за последние 2 сек) и валидные. */
    public static boolean isValid() {
        return radius > 0.0F
                && System.currentTimeMillis() - lastUpdateMs < 2000L;
    }

    public static double x() { return cx; }
    public static double y() { return cy; }
    public static double z() { return cz; }
    public static float  radius() { return radius; }

    public static boolean isPlayerInZone() {
        if (!isValid() || Minecraft.getInstance().player == null) return true;
        double dx = Minecraft.getInstance().player.getX() - x();
        double dz = Minecraft.getInstance().player.getZ() - z();
        double distSq = dx * dx + dz * dz;
        return distSq <= radius() * radius();
    }

    public static float getOutsideFactor(float fadeRange) {
        if (!isValid()) return 0.0F;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0.0F;

        double dx = mc.player.getX() - x();
        double dz = mc.player.getZ() - z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double outsideDist = dist - radius();

        if (outsideDist <= 0)              return 0.0F;
        if (outsideDist >= fadeRange)      return 1.0F;
        return (float) (outsideDist / fadeRange);
    }
}