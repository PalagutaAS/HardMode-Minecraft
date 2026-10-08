package net.elrid.hardmode.network;

import net.elrid.hardmode.HardMode;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SafeZoneNetworking {

    public static final CustomPacketPayload.Type<ZoneStatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "zone_state"));

    public record ZoneStatePayload(double x, double y, double z, float radius)
            implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, ZoneStatePayload> CODEC =
                CustomPacketPayload.codec(ZoneStatePayload::write, ZoneStatePayload::new);

        public ZoneStatePayload(RegistryFriendlyByteBuf buf) {
            this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(radius);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private SafeZoneNetworking() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, ZoneStatePayload.CODEC);
    }

    public static void broadcast(ServerLevel level, double x, double y, double z, float radius) {
        ZoneStatePayload payload = new ZoneStatePayload(x, y, z, radius);
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}