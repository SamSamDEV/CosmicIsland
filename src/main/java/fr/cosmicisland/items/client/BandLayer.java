package fr.cosmicisland.items.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.items.BandeauItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dessine le Bandeau (autour du front) et le Brassard (bras gauche) directement sur le joueur, avec un volume
 * epais qui colle a la peau (pas d'effet « flottant »). Teinte = couleur de l'objet.
 * Textures : assets/cosmicisland/textures/entity/bandeau.png et brassard.png (blanc/gris : l'ombre est dans la texture).
 */
public class BandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation HEAD = new ModelLayerLocation(new ResourceLocation(CosmicIsland.MODID, "bandeau"), "main");
    public static final ModelLayerLocation ARM = new ModelLayerLocation(new ResourceLocation(CosmicIsland.MODID, "brassard"), "main");
    public static final ModelLayerLocation ARM_STAND = new ModelLayerLocation(new ResourceLocation(CosmicIsland.MODID, "brassard_stand"), "main");
    public static final ModelLayerLocation ARM_SLIM = new ModelLayerLocation(new ResourceLocation(CosmicIsland.MODID, "brassard_slim"), "main");

    static final ResourceLocation TEX_HEAD = new ResourceLocation(CosmicIsland.MODID, "textures/entity/bandeau.png");
    static final ResourceLocation TEX_ARM = new ResourceLocation(CosmicIsland.MODID, "textures/entity/brassard.png");

    private final ModelPart head;  // (la tete du joueur et celle de l'armor stand ont la meme taille)
    private final ModelPart arm;

    public BandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                     EntityModelSet models, boolean slim) {
        super(parent);
        this.head = models.bakeLayer(HEAD); // racine : bande + noeud + 2 pans
        this.arm = models.bakeLayer(slim ? ARM_SLIM : ARM).getChild("band");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (headStack.getItem() == BandeauItems.BANDEAU) {
            draw(poseStack, buffer, light, getParentModel().head, head, TEX_HEAD, headStack);
        }
        ItemStack chestStack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestStack.getItem() == BandeauItems.BRASSARD) {
            draw(poseStack, buffer, light, getParentModel().leftArm, arm, TEX_ARM, chestStack);
        }
    }

    static void draw(PoseStack poseStack, MultiBufferSource buffer, int light, ModelPart anchor,
                             ModelPart band, ResourceLocation texture, ItemStack stack) {
        int color = ((BandeauItems.Band) stack.getItem()).getColor(stack);
        float r = (color >> 16 & 255) / 255.0f;
        float g = (color >> 8 & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        poseStack.pushPose();
        anchor.translateAndRotate(poseStack);
        band.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
                OverlayTexture.NO_OVERLAY, r, g, b, 1.0f);
        poseStack.popPose();
    }

    // ------------------------------------------------------------ enregistrement (cote client)

    @Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {}

        private static LayerDefinition ring(int width, int height, int depth, float y, float inflate, float growY,
                                            float x, int texW, int texH) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("band",
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(x, y, -depth / 2.0f, width, height, depth, new CubeDeformation(inflate, growY, inflate)),
                    PartPose.ZERO);
            return LayerDefinition.create(mesh, texW, texH);
        }

        /** Bande autour de la tete + noeud a l'arriere + deux pans qui pendent (comme un bandana). */
        private static LayerDefinition headDefinition() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            // 2 lignes de texture etirees : de y -7,5 a -4,7 (le haut de la tete depasse de 0,5 px)
            root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-4.0f, -7.1f, -4.0f, 8, 2, 8, new CubeDeformation(0.7f, 0.4f, 0.7f)), PartPose.ZERO);
            // noeud derriere la tete (z positif = arriere)
            root.addOrReplaceChild("knot", CubeListBuilder.create().texOffs(8, 11)
                    .addBox(-1.0f, -7.0f, 4.5f, 2, 2, 2), PartPose.ZERO);
            // deux pans, ecartes vers l'exterieur et rejetes vers l'arriere
            root.addOrReplaceChild("tail_l", CubeListBuilder.create().texOffs(0, 11)
                    .addBox(-1.0f, 0.0f, -0.5f, 2, 4, 1), PartPose.offsetAndRotation(-0.9f, -5.4f, 6.0f, 0.35f, 0.0f, 0.35f));
            root.addOrReplaceChild("tail_r", CubeListBuilder.create().texOffs(16, 11)
                    .addBox(-1.0f, 0.0f, -0.5f, 2, 4, 1), PartPose.offsetAndRotation(0.9f, -5.4f, 6.0f, 0.35f, 0.0f, -0.35f));
            return LayerDefinition.create(mesh, 32, 16);
        }

        @SubscribeEvent
        public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            // Tete : 2 lignes de texture (blanc + moucheté) etirees sur 2,8 px de haut, de y -7,5 a -4,7 (le haut de la tete depasse de 0,5 px)
            event.registerLayerDefinition(HEAD, Setup::headDefinition);
            // Bras gauche : bande de 4 px sous l'epaule (bras fin « slim » : 3 px de large)
            event.registerLayerDefinition(ARM, () -> ring(4, 4, 4, 1.0f, 0.5f, 0.5f, -1.0f, 16, 8));
            // Armor stand : bras de 2 px -> meme volume reduit de moitie (xScale/zScale = 0,5 dans BandStandLayer)
            event.registerLayerDefinition(ARM_STAND, () -> ring(4, 4, 4, 1.0f, 0.5f, 0.5f, -2.0f, 16, 8));
            event.registerLayerDefinition(ARM_SLIM, () -> ring(3, 4, 4, 1.0f, 0.5f, 0.5f, -1.0f, 16, 8));
        }

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : new String[] {"default", "slim"}) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                    renderer.addLayer(new BandLayer(renderer, event.getEntityModels(), skin.equals("slim")));
                }
            }
            if (event.getRenderer(net.minecraft.world.entity.EntityType.ARMOR_STAND)
                    instanceof net.minecraft.client.renderer.entity.ArmorStandRenderer stand) {
                stand.addLayer(new BandStandLayer(stand, event.getEntityModels()));
            }
        }
    }
}
