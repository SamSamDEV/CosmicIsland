package fr.cosmicisland.admin;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.admin.client.AdminClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Reseau de la fenetre d'administration : le client demande l'etat, le serveur l'envoie (organisateurs et admins seulement). */
public final class AdminNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CosmicIsland.MODID, "admin"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private AdminNetwork() {}

    public static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(0, RequestPacket.class, RequestPacket::encode, RequestPacket::decode, RequestPacket::handle);
            CHANNEL.registerMessage(1, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle);
        });
    }

    /** Envoie l'etat (et ouvre la fenetre si open) a un organisateur ou admin. */
    public static void sendState(ServerPlayer player, boolean open) {
        if (KLPermissions.levelOf(player) < KLPermissions.ORGANISATEUR) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StatePacket(AdminState.build(player), open));
    }

    public static class RequestPacket {
        final boolean open;

        public RequestPacket(boolean open) {
            this.open = open;
        }

        static void encode(RequestPacket p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.open);
        }

        static RequestPacket decode(FriendlyByteBuf buf) {
            return new RequestPacket(buf.readBoolean());
        }

        static void handle(RequestPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    sendState(sender, p.open);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class StatePacket {
        final String json;
        final boolean open;

        public StatePacket(String json, boolean open) {
            this.json = json;
            this.open = open;
        }

        static void encode(StatePacket p, FriendlyByteBuf buf) {
            buf.writeUtf(p.json, 262144);
            buf.writeBoolean(p.open);
        }

        static StatePacket decode(FriendlyByteBuf buf) {
            return new StatePacket(buf.readUtf(262144), buf.readBoolean());
        }

        static void handle(StatePacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AdminClient.onState(p.json, p.open)));
            ctx.get().setPacketHandled(true);
        }
    }
}
