package fr.cosmicisland.survival;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.survival.client.ThirstClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Reseau de la soif : niveau serveur -> client (HUD) et demande de boire dans l'eau client -> serveur. */
public final class ThirstNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CosmicIsland.MODID, "thirst"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ThirstNetwork() {}

    public static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(0, ThirstPacket.class, ThirstPacket::encode, ThirstPacket::decode, ThirstPacket::handle);
            CHANNEL.registerMessage(1, DrinkPacket.class, DrinkPacket::encode, DrinkPacket::decode, DrinkPacket::handle);
        });
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static class ThirstPacket {
        final boolean active;
        final int level;
        final int max;

        public ThirstPacket(boolean active, int level, int max) {
            this.active = active;
            this.level = level;
            this.max = max;
        }

        static void encode(ThirstPacket p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.active);
            buf.writeInt(p.level);
            buf.writeInt(p.max);
        }

        static ThirstPacket decode(FriendlyByteBuf buf) {
            return new ThirstPacket(buf.readBoolean(), buf.readInt(), buf.readInt());
        }

        static void handle(ThirstPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ThirstClient.update(p.active, p.level, p.max));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Le client demande a boire dans l'eau qu'il regarde (le serveur verifie tout). */
    public static class DrinkPacket {
        public DrinkPacket() {}

        static void encode(DrinkPacket p, FriendlyByteBuf buf) {}

        static DrinkPacket decode(FriendlyByteBuf buf) {
            return new DrinkPacket();
        }

        static void handle(DrinkPacket p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null) {
                    ThirstManager.drinkFromWater(sender);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
