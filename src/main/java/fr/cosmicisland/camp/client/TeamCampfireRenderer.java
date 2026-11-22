package fr.cosmicisland.camp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.cosmicisland.camp.TeamCampfireBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Affiche sur le grill les aliments en train de cuire (poisson, poulet...). */
public class TeamCampfireRenderer implements BlockEntityRenderer<TeamCampfireBlockEntity> {
    private static final float[][] SPOTS = {{0.30f, -0.32f}, {0.38f, -0.10f}, {0.38f, 0.10f}, {0.30f, 0.32f}};
    private final ItemRenderer itemRenderer;

    public TeamCampfireRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(TeamCampfireBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        int lightAbove = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        for (int i = 0; i < 4; i++) {
            ItemStack stack = be.grillItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5 + SPOTS[i][0], 0.72, 0.5 + SPOTS[i][1]);
            pose.mulPose(Axis.YP.rotationDegrees(i * 70f + 20f));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.scale(0.22f, 0.22f, 0.22f);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, lightAbove, overlay, pose, buffers, level,
                    (int) be.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }
}
