package fr.cosmicisland.items.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmorStandArmorModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import fr.cosmicisland.items.BandeauItems;

/** Meme rendu que sur le joueur, pour le Bandeau et le Brassard portes par un armor stand. */
public class BandStandLayer extends RenderLayer<ArmorStand, ArmorStandArmorModel> {
    private final ModelPart head;
    private final ModelPart arm;

    public BandStandLayer(RenderLayerParent<ArmorStand, ArmorStandArmorModel> parent, EntityModelSet models) {
        super(parent);
        this.head = models.bakeLayer(BandLayer.HEAD);
        this.arm = models.bakeLayer(BandLayer.ARM_STAND).getChild("band");
        this.arm.xScale = 0.5f;   // bras d'armor stand : 2 px de large au lieu de 4
        this.arm.zScale = 0.5f;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, ArmorStand stand,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (stand.isInvisible()) {
            return;
        }
        ItemStack headStack = stand.getItemBySlot(EquipmentSlot.HEAD);
        if (headStack.getItem() == BandeauItems.BANDEAU) {
            BandLayer.draw(poseStack, buffer, light, getParentModel().head, head, BandLayer.TEX_HEAD, headStack);
        }
        ItemStack chestStack = stand.getItemBySlot(EquipmentSlot.CHEST);
        if (chestStack.getItem() == BandeauItems.BRASSARD && getParentModel().leftArm.visible) {
            BandLayer.draw(poseStack, buffer, light, getParentModel().leftArm, arm, BandLayer.TEX_ARM, chestStack);
        }
    }
}
