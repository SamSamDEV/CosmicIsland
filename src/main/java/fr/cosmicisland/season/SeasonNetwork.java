package fr.cosmicisland.season;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.season.client.SeasonClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Envoie le jour de l'aventure aux joueurs (affiche a la place du niveau d'XP). */
public final class SeasonNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CosmicIsland.MODID, "season"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private SeasonNetwork() {}

    public static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(0, DayPacket.class, DayPacket::encode, DayPacket::decode, DayPacket::handle);
        });
    }

    public static void send(ServerPlayer player, int day) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DayPacket(day));
    }

    public static void sendAll(Iterable<ServerPlayer> players, int day) {
        for (ServerPlayer p : players) {
            send(p, day);
        }
    }

    public static class DayPacket {
        final int day;

        public DayPacket(int day) {
            this.day = day;
        }

        static void encode(DayPacket p, FriendlyByteBuf buf) {
            buf.writeInt(p.day);
        }

        static DayPacket decode(FriendlyByteBuf buf) {
            return new DayPacket(buf.readInt());
        }

        static void handle(DayPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> SeasonClient.day = p.day);
            ctx.get().setPacketHandled(true);
        }
    }
}
