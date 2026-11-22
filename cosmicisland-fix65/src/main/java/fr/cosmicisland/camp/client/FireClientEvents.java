package fr.cosmicisland.camp.client;

import fr.cosmicisland.CosmicIsland;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Evenements client du feu de camp (l'allumage se joue dans FireIgniteScreen). */
public final class FireClientEvents {
    private static final java.util.Set<String> HELD = java.util.Set.of("rice_shell", "lighter", "canteen", "cooking_pot");
    /** Reglage fin de la position de la main (en blocs, repere de la vue). */
    private static final float ARM_DX = 0.0f, ARM_DY = 0.0f, ARM_DZ = 0.0f;

    private FireClientEvents() {}

    @Mod.EventBusSubscriber(modid = CosmicIsland.MODID, value = Dist.CLIENT)
    public static final class FireForgeBus {
        /** Objets 3D du mod en vue FPS : on voit ta main (vanilla ne dessine pas le bras avec un objet). */
        @SubscribeEvent
        public static void onHand(net.minecraftforge.client.event.RenderHandEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            var stack = event.getItemStack();
            var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null || !CosmicIsland.MODID.equals(id.getNamespace()) || !HELD.contains(id.getPath())) {
                return;
            }
            var arm = event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite();
            boolean right = arm != net.minecraft.world.entity.HumanoidArm.LEFT;
            float f = right ? 1.0f : -1.0f;
            float swing = event.getSwingProgress();
            float equip = event.getEquipProgress();
            float f1 = net.minecraft.util.Mth.sqrt(swing);
            float f2 = -0.3f * net.minecraft.util.Mth.sin(f1 * (float) Math.PI);
            float f3 = 0.4f * net.minecraft.util.Mth.sin(f1 * (float) Math.PI * 2f);
            float f4 = -0.4f * net.minecraft.util.Mth.sin(swing * (float) Math.PI);
            var ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(f * (f2 + 0.64000005f + ARM_DX), f3 - 0.6f + equip * -0.6f + ARM_DY, f4 - 0.71999997f + ARM_DZ);
            ps.mulPose(Axis.YP.rotationDegrees(f * 45.0f));
            float f5 = net.minecraft.util.Mth.sin(swing * swing * (float) Math.PI);
            float f6 = net.minecraft.util.Mth.sin(f1 * (float) Math.PI);
            ps.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0f));
            ps.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0f));
            ps.translate(f * -1.0f, 3.6f, 3.5f);
            ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0f));
            ps.mulPose(Axis.XP.rotationDegrees(200.0f));
            ps.mulPose(Axis.YP.rotationDegrees(f * -135.0f));
            ps.translate(f * 5.6f, 0.0f, 0.0f);
            var renderer = (net.minecraft.client.renderer.entity.player.PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
            if (right) {
                renderer.renderRightHand(ps, event.getMultiBufferSource(), event.getPackedLight(), mc.player);
            } else {
                renderer.renderLeftHand(ps, event.getMultiBufferSource(), event.getPackedLight(), mc.player);
            }
            ps.popPose();
        }

        @SubscribeEvent
        public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            fr.cosmicisland.Lang.setSynced(null);
            FireClient.progress = -1f;
        }
    }
}
