package fr.cosmicisland.conseil.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.conseil.ConseilRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * En premiere personne, tenir l'anneau affiche le BRAS du joueur (le jeu ne le dessine pas quand on tient un objet)
 * avec l'anneau passe autour du poignet.
 */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RingHand {
    // ---- reglages (en blocs, dans le repere du bras ; 1 px = 0,0625)
    private static final float RING_Y = 0.60f;      // position le long du bras (0 = epaule, 0,75 = bout de la main)
    private static final float RING_SCALE = 0.80f;  // taille de l'anneau
    private static final float GEM_ANGLE = 90.0f;   // 90 ou -90 : cote ou pointe la gemme

    private RingHand() {}

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ConseilRegistry.COLLIER.get())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }
        event.setCanceled(true);

        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm != HumanoidArm.LEFT;
        float f = right ? 1.0f : -1.0f;
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        PoseStack ps = event.getPoseStack();

        ps.pushPose();
        float f1 = Mth.sqrt(swing);
        float f2 = -0.3f * Mth.sin(f1 * (float) Math.PI);
        float f3 = 0.4f * Mth.sin(f1 * ((float) Math.PI * 2.0f));
        float f4 = -0.4f * Mth.sin(swing * (float) Math.PI);
        ps.translate(f * (f2 + 0.64000005f), f3 + -0.6f + equip * -0.6f, f4 + -0.71999997f);
        ps.mulPose(Axis.YP.rotationDegrees(f * 45.0f));
        float f5 = Mth.sin(swing * swing * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);
        ps.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0f));
        ps.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0f));
        ps.translate(f * -1.0f, 3.6f, 3.5f);
        ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0f));
        ps.mulPose(Axis.XP.rotationDegrees(200.0f));
        ps.mulPose(Axis.YP.rotationDegrees(f * -135.0f));
        ps.translate(f * 5.6f, 0.0f, 0.0f);

        if (right) {
            renderer.renderRightHand(ps, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(ps, event.getMultiBufferSource(), event.getPackedLight(), player);
        }

        // anneau autour du poignet (repere du bras)
        ps.pushPose();
        ps.translate(-f * 0.375f, RING_Y, 0.0f);
        ps.mulPose(Axis.XP.rotationDegrees(90.0f));        // plan de l'anneau perpendiculaire au bras
        ps.mulPose(Axis.ZP.rotationDegrees(GEM_ANGLE));    // gemme vers le dessus du bras (90) ou le dessous (-90)
        ps.scale(RING_SCALE, RING_SCALE, RING_SCALE);
        ps.translate(0.0f, 0.05f, 0.0f);                   // (le jeu recentre deja le modele de -0,5 : l'anneau est a y=0,45)
        mc.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE, !right, ps,
                event.getMultiBufferSource(), mc.level, event.getPackedLight(), OverlayTexture.NO_OVERLAY, player.getId());
        ps.popPose();

        ps.popPose();
    }
}
