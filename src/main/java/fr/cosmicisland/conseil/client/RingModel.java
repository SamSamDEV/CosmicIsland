package fr.cosmicisland.conseil.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.cosmicisland.CosmicIsland;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Function;

/**
 * Anneau d'Exemption : modele ARRONDI genere par code (vrai tore d'or + gemme en losange),
 * cuit une seule fois dans le chunk (aucun cout par image). Declare par "loader": "cosmicisland:ring".
 */
public final class RingModel {
    private RingModel() {}

    public static final class Geometry implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                               ItemOverrides overrides, ResourceLocation modelLocation) {
            TextureAtlasSprite tex = spriteGetter.apply(context.getMaterial("0"));
            TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
            IModelBuilder<?> builder = IModelBuilder.of(false, context.useBlockLight(), true,
                    context.getTransforms(), overrides, particle, RenderTypeGroup.EMPTY);
            new Mesh(builder, tex).build();
            return builder.build();
        }
    }

    public static final class Loader implements IGeometryLoader<Geometry> {
        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext ctx) throws JsonParseException {
            return new Geometry();
        }
    }

    @Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class RingSetup {
        @SubscribeEvent
        public static void onRegister(ModelEvent.RegisterGeometryLoaders event) {
            event.register("ring", new Loader());
        }
    }

    // ------------------------------------------------------------------ geometrie

    private static final class Mesh {
        static final double CX = 0.5, CY = 0.45, CZ = 0.5;
        static final double RM = 0.22, RT = 0.05;      // rayon du cercle / rayon du jonc
        static final int SEG = 20, SIDES = 8;

        private final IModelBuilder<?> builder;
        private final TextureAtlasSprite tex;
        private final QuadBakingVertexConsumer baker;

        Mesh(IModelBuilder<?> builder, TextureAtlasSprite tex) {
            this.builder = builder;
            this.tex = tex;
            this.baker = new QuadBakingVertexConsumer(q -> this.builder.addUnculledFace(q));
        }

        void build() {
            torus();
            gem();
        }

        double[] tp(int i, int j) {
            double a = 2 * Math.PI * i / SEG, b = 2 * Math.PI * j / SIDES;
            double rad = RM + RT * Math.cos(b);
            return new double[]{CX + rad * Math.cos(a), CY + rad * Math.sin(a), CZ + RT * Math.sin(b)};
        }

        void torus() {
            for (int i = 0; i < SEG; i++) {
                double am = 2 * Math.PI * (i + 0.5) / SEG;
                double[] ref = {CX + RM * Math.cos(am), CY + RM * Math.sin(am), CZ};
                for (int j = 0; j < SIDES; j++) {
                    double[][] p = {tp(i, j), tp(i + 1, j), tp(i + 1, j + 1), tp(i, j + 1)};
                    float u0 = i * 8.0f / SEG, u1 = (i + 1) * 8.0f / SEG, v0 = j * 16.0f / SIDES, v1 = (j + 1) * 16.0f / SIDES;
                    quad(p, new float[][]{{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}}, ref);
                }
            }
        }

        /** Gemme taillee en losange (bipyramide a 4 faces) sertie au sommet du jonc. */
        void gem() {
            double gy = CY + RM + RT * 0.6, h = 0.09, w = 0.06;   // sertie dans le jonc
            double[] top = {CX, gy + h, CZ}, bot = {CX, gy - h, CZ};
            double[][] e = {{CX + w, gy, CZ}, {CX, gy, CZ + w}, {CX - w, gy, CZ}, {CX, gy, CZ - w}};
            double[] ref = {CX, gy, CZ};
            for (int k = 0; k < 4; k++) {
                double[] a = e[k], b = e[(k + 1) % 4];
                float shade = (k % 2 == 0) ? 0f : 1.5f;
                quad(new double[][]{top, a, b, b}, new float[][]{{12, 1.5f}, {9 + shade, 9}, {15 - shade, 9}, {15 - shade, 9}}, ref);
                quad(new double[][]{bot, b, a, a}, new float[][]{{12, 15}, {15 - shade, 8}, {9 + shade, 8}, {9 + shade, 8}}, ref);
            }
        }

        /** Emet un quad en orientant automatiquement les sommets vers l'exterieur (ref = point a l'interieur du volume). */
        void quad(double[][] p, float[][] uv, double[] ref) {
            double ax = p[2][0] - p[0][0], ay = p[2][1] - p[0][1], az = p[2][2] - p[0][2];
            double bx = p[3][0] - p[1][0], by = p[3][1] - p[1][1], bz = p[3][2] - p[1][2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0e-9) {
                return;
            }
            nx /= len; ny /= len; nz /= len;
            double mx = (p[0][0] + p[1][0] + p[2][0] + p[3][0]) / 4 - ref[0];
            double my = (p[0][1] + p[1][1] + p[2][1] + p[3][1]) / 4 - ref[1];
            double mz = (p[0][2] + p[1][2] + p[2][2] + p[3][2]) / 4 - ref[2];
            int[] order = {0, 1, 2, 3};
            if (nx * mx + ny * my + nz * mz < 0) {
                order = new int[]{0, 3, 2, 1};
                nx = -nx; ny = -ny; nz = -nz;
            }
            baker.setSprite(tex);
            baker.setShade(true);
            baker.setDirection(Direction.getNearest((float) nx, (float) ny, (float) nz));
            for (int idx : order) {
                baker.vertex(p[idx][0], p[idx][1], p[idx][2]);
                baker.color(255, 255, 255, 255);
                baker.uv(tex.getU(uv[idx][0]), tex.getV(uv[idx][1]));
                baker.uv2(0);
                baker.normal((float) nx, (float) ny, (float) nz);
                baker.endVertex();
            }
        }
    }
}
