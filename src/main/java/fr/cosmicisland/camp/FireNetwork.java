package fr.cosmicisland.camp;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.camp.client.FireClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Reseau de l'allumage : tapotement client -> serveur, progression serveur -> client. */
public final class FireNetwork {
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CosmicIsland.MODID, "fire"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private FireNetwork() {}

    public static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(0, TapPacket.class, TapPacket::encode, TapPacket::decode, TapPacket::handle);
            CHANNEL.registerMessage(1, ProgressPacket.class, ProgressPacket::encode, ProgressPacket::decode, ProgressPacket::handle);
            CHANNEL.registerMessage(3, OpenPacket.class, OpenPacket::encode, OpenPacket::decode, OpenPacket::handle);
            CHANNEL.registerMessage(2, LangPacket.class, LangPacket::encode, LangPacket::decode, LangPacket::handle);
        });
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendLang(ServerPlayer player) {
        toPlayer(player, new LangPacket(fr.cosmicisland.Lang.language()));
    }

    public static void broadcastLang() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                sendLang(p);
            }
        }
    }

    /** Langue du mod (fr / en), envoyee a la connexion et a chaque changement. */
    public static class LangPacket {
        final String lang;

        public LangPacket(String lang) {
            this.lang = lang;
        }

        static void encode(LangPacket p, FriendlyByteBuf buf) {
            buf.writeUtf(p.lang, 8);
        }

        static LangPacket decode(FriendlyByteBuf buf) {
            return new LangPacket(buf.readUtf(8));
        }

        static void handle(LangPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> fr.cosmicisland.Lang.setSynced(p.lang));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Le serveur demande d'ouvrir l'ecran d'allumage (le baton et la planche). */
    public static class OpenPacket {
        public OpenPacket() {}

        static void encode(OpenPacket p, FriendlyByteBuf buf) {}

        static OpenPacket decode(FriendlyByteBuf buf) {
            return new OpenPacket();
        }

        static void handle(OpenPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT, () -> fr.cosmicisland.camp.client.FireScreens::openIgnite));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Un coup de frottement (0 = vers la gauche, 1 = vers la droite, 2 = ecran ferme) ; le serveur verifie tout. */
    public static class TapPacket {
        final int side;

        public TapPacket(int side) {
            this.side = side;
        }

        static void encode(TapPacket p, FriendlyByteBuf buf) {
            buf.writeByte(p.side);
        }

        static TapPacket decode(FriendlyByteBuf buf) {
            return new TapPacket(Math.max(0, Math.min(2, buf.readByte())));
        }

        static void handle(TapPacket p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null) {
                    FireIgnition.stroke(sender, p.side);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Progression de la jauge d'allumage (0 a 1), ou -1 pour la masquer. */
    public static class ProgressPacket {
        final float progress;

        public ProgressPacket(float progress) {
            this.progress = progress;
        }

        static void encode(ProgressPacket p, FriendlyByteBuf buf) {
            buf.writeFloat(p.progress);
        }

        static ProgressPacket decode(FriendlyByteBuf buf) {
            return new ProgressPacket(buf.readFloat());
        }

        static void handle(ProgressPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> FireClient.progress = p.progress);
            ctx.get().setPacketHandled(true);
        }
    }
}
