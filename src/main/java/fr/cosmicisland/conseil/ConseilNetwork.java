package fr.cosmicisland.conseil;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.conseil.client.ConseilClient;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Canal reseau du conseil : serveur -> client, decompte des votes pour le HUD. */
public final class ConseilNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CosmicIsland.MODID, "conseil"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ConseilNetwork() {}

    public static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ConseilNetwork::register);
    }

    private static void register() {
        CHANNEL.registerMessage(0, TallyPacket.class, TallyPacket::encode, TallyPacket::decode, TallyPacket::handle);
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Decompte des votes : active ou non, noms des joueurs et nombre de votes reveles pour chacun. */
    public static class TallyPacket {
        final boolean active;
        final List<String> names;
        final List<Integer> counts;

        public TallyPacket(boolean active, List<String> names, List<Integer> counts) {
            this.active = active;
            this.names = names;
            this.counts = counts;
        }

        static void encode(TallyPacket p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.active);
            buf.writeVarInt(p.names.size());
            for (int i = 0; i < p.names.size(); i++) {
                buf.writeUtf(p.names.get(i));
                buf.writeVarInt(p.counts.get(i));
            }
        }

        static TallyPacket decode(FriendlyByteBuf buf) {
            boolean active = buf.readBoolean();
            int size = buf.readVarInt();
            List<String> names = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                names.add(buf.readUtf());
                counts.add(buf.readVarInt());
            }
            return new TallyPacket(active, names, counts);
        }

        static void handle(TallyPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ConseilClient.onTally(p.active, p.names, p.counts)));
            ctx.get().setPacketHandled(true);
        }
    }
}
