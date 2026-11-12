package fr.cosmicisland.games.poteaux;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.games.poteaux.client.PoteauxClient;
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

/** Canal reseau des poteaux. Serveur -> client : fleche et etat (HUD). Client -> serveur : touche pressee. */
public final class PoteauxNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CosmicIsland.MODID, "poteaux"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private PoteauxNetwork() {}

    public static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PoteauxNetwork::register);
    }

    private static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, StatePacket.class, StatePacket::encode, StatePacket::decode, StatePacket::handle);
        CHANNEL.registerMessage(id++, ArrowPacket.class, ArrowPacket::encode, ArrowPacket::decode, ArrowPacket::handle);
        CHANNEL.registerMessage(id++, KeyPacket.class, KeyPacket::encode, KeyPacket::decode, KeyPacket::handle);
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Etat du HUD : actif ou non, erreurs, erreurs max, resultat de la derniere fleche (0 aucun, 1 reussi, 2 rate). */
    public static class StatePacket {
        final boolean active;
        final int errors;
        final int maxErrors;
        final int result;

        public StatePacket(boolean active, int errors, int maxErrors, int result) {
            this.active = active;
            this.errors = errors;
            this.maxErrors = maxErrors;
            this.result = result;
        }

        static void encode(StatePacket p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.active);
            buf.writeInt(p.errors);
            buf.writeInt(p.maxErrors);
            buf.writeInt(p.result);
        }

        static StatePacket decode(FriendlyByteBuf buf) {
            return new StatePacket(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        static void handle(StatePacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> PoteauxClient.onState(p.active, p.errors, p.maxErrors, p.result)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Fleche a afficher : direction 0 haut, 1 bas, 2 gauche, 3 droite ; -1 = effacer. Fenetre en millisecondes. */
    public static class ArrowPacket {
        final int direction;
        final int windowMs;

        public ArrowPacket(int direction, int windowMs) {
            this.direction = direction;
            this.windowMs = windowMs;
        }

        static void encode(ArrowPacket p, FriendlyByteBuf buf) {
            buf.writeInt(p.direction);
            buf.writeInt(p.windowMs);
        }

        static ArrowPacket decode(FriendlyByteBuf buf) {
            return new ArrowPacket(buf.readInt(), buf.readInt());
        }

        static void handle(ArrowPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> PoteauxClient.onArrow(p.direction, p.windowMs)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Touche pressee par le joueur (client -> serveur). */
    public static class KeyPacket {
        final int direction;

        public KeyPacket(int direction) {
            this.direction = direction;
        }

        static void encode(KeyPacket p, FriendlyByteBuf buf) {
            buf.writeInt(p.direction);
        }

        static KeyPacket decode(FriendlyByteBuf buf) {
            return new KeyPacket(buf.readInt());
        }

        static void handle(KeyPacket p, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) {
                    PoteauxGame.handleKey(sender, p.direction);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
