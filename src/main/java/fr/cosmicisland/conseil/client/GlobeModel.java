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
 * Globe d'immunite : modele ARRONDI genere par code (vraie sphere inclinee + anneau + socle),
 * cuit une seule fois dans le chunk (aucun cout par image). Declare par "loader": "cosmicisland:globe".
 */
public final class GlobeModel {
    private GlobeModel() {}

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
    public static final class GlobeSetup {
        @SubscribeEvent
        public static void onRegister(ModelEvent.RegisterGeometryLoaders event) {
            event.register("globe", new Loader());
        }
    }

    // ------------------------------------------------------------------ geometrie

    private static final class Mesh {
        // sphere
        static final double CX = 0.5, CY = 0.60, CZ = 0.5, R = 0.30;
        static final double TILT = Math.toRadians(22.5);
        static final int SLICES = 20, STACKS = 10;
        // anneau meridien
        static final double RING_R = 0.40, RING_W = 0.035, RING_D = 0.03;
        static final int RING_SEG = 20;

        private final IModelBuilder<?> builder;
        private final TextureAtlasSprite tex;
        private final QuadBakingVertexConsumer baker;

        Mesh(IModelBuilder<?> builder, TextureAtlasSprite tex) {
            this.builder = builder;
            this.tex = tex;
            this.baker = new QuadBakingVertexConsumer(q -> this.builder.addUnculledFace(q));
        }

        void build() {
            sphere();
            ring();
            box(CX, CY, CZ, 0.012, 0.41, 0.012, TILT);          // axe incline
            cylinder(0.5, 0.5, 0.34, 0.0, 0.05, 12, true);       // socle
            cylinder(0.5, 0.5, 0.20, 0.05, 0.10, 12, true);
            cylinder(0.5, 0.5, 0.045, 0.10, 0.21, 8, false);     // col
        }

        // ---- sphere : texture equirectangulaire (moitie haute de l'atlas : v 0..8)
        void sphere() {
            for (int j = 0; j < STACKS; j++) {
                for (int i = 0; i < SLICES; i++) {
                    double[][] p = new double[4][];
                    float[][] uv = new float[4][];
                    int[][] corners = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                    for (int k = 0; k < 4; k++) {
                        p[k] = spherePoint(corners[k][0], corners[k][1]);
                        uv[k] = new float[]{corners[k][0] * 16.0f / SLICES, corners[k][1] * 8.0f / STACKS};
                    }
                    quad(p, uv, new double[]{CX, CY, CZ});
                }
            }
        }

        double[] spherePoint(int i, int j) {
            double th = Math.PI * j / STACKS;
            double ph = 2.0 * Math.PI * i / SLICES;
            double x = R * Math.sin(th) * Math.cos(ph);
            double y = R * Math.cos(th);
            double z = -R * Math.sin(th) * Math.sin(ph);
            return new double[]{CX + x * Math.cos(TILT) - y * Math.sin(TILT), CY + x * Math.sin(TILT) + y * Math.cos(TILT), CZ + z};
        }

        // ---- anneau argente autour de la sphere (plan x-y)
        void ring() {
            for (int k = 0; k < RING_SEG; k++) {
                double a0 = 2 * Math.PI * k / RING_SEG, a1 = 2 * Math.PI * (k + 1) / RING_SEG;
                double rm = (a0 + a1) / 2;
                double[] ref = {CX + RING_R * Math.cos(rm), CY + RING_R * Math.sin(rm), CZ};
                double ro = RING_R + RING_W / 2, ri = RING_R - RING_W / 2, hz = RING_D / 2;
                double[][] c = new double[8][];       // 0-3 : z+ ; 4-7 : z-
                double[] rr = {ro, ro, ri, ri};
                double[] aa = {a0, a1, a1, a0};
                for (int q = 0; q < 4; q++) {
                    c[q] = new double[]{CX + rr[q] * Math.cos(aa[q]), CY + rr[q] * Math.sin(aa[q]), CZ + hz};
                    c[q + 4] = new double[]{CX + rr[q] * Math.cos(aa[q]), CY + rr[q] * Math.sin(aa[q]), CZ - hz};
                }
                metal(new double[][]{c[0], c[1], c[2], c[3]}, ref);   // face avant
                metal(new double[][]{c[4], c[5], c[6], c[7]}, ref);   // face arriere
                metal(new double[][]{c[0], c[1], c[5], c[4]}, ref);   // exterieur
                metal(new double[][]{c[3], c[2], c[6], c[7]}, ref);   // interieur
            }
        }

        // ---- pave incline autour de l'axe z (axe de la sphere)
        void box(double cx, double cy, double cz, double hx, double hy, double hz, double tilt) {
            double[][] c = new double[8][];
            int n = 0;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        double x = sx * hx, y = sy * hy;
                        c[n++] = new double[]{cx + x * Math.cos(tilt) - y * Math.sin(tilt), cy + x * Math.sin(tilt) + y * Math.cos(tilt), cz + sz * hz};
                    }
                }
            }
            double[] ref = {cx, cy, cz};
            // index = sx(0/1)*4 + sy(0/1)*2 + sz(0/1)
            metal(new double[][]{c[0], c[1], c[3], c[2]}, ref);
            metal(new double[][]{c[4], c[5], c[7], c[6]}, ref);
            metal(new double[][]{c[0], c[1], c[5], c[4]}, ref);
            metal(new double[][]{c[2], c[3], c[7], c[6]}, ref);
            metal(new double[][]{c[0], c[2], c[6], c[4]}, ref);
            metal(new double[][]{c[1], c[3], c[7], c[5]}, ref);
        }

        // ---- cylindre (socle, col)
        void cylinder(double cx, double cz, double r, double y0, double y1, int sides, boolean cap) {
            double[] ref = {cx, (y0 + y1) / 2, cz};
            for (int k = 0; k < sides; k++) {
                double a0 = 2 * Math.PI * k / sides, a1 = 2 * Math.PI * (k + 1) / sides;
                metal(new double[][]{
                        {cx + r * Math.cos(a0), y0, cz + r * Math.sin(a0)},
                        {cx + r * Math.cos(a1), y0, cz + r * Math.sin(a1)},
                        {cx + r * Math.cos(a1), y1, cz + r * Math.sin(a1)},
                        {cx + r * Math.cos(a0), y1, cz + r * Math.sin(a0)}}, ref);
            }
            if (cap) {
                for (int k = 0; k < sides; k += 2) {
                    double[][] p = new double[4][];
                    p[0] = new double[]{cx, y1, cz};
                    for (int m = 0; m < 3; m++) {
                        double a = 2 * Math.PI * (k + m) / sides;
                        p[m + 1] = new double[]{cx + r * Math.cos(a), y1, cz + r * Math.sin(a)};
                    }
                    metal(p, ref);
                }
            }
        }

        void metal(double[][] p, double[] ref) {
            quad(p, new float[][]{{1, 9}, {3, 9}, {3, 11}, {1, 11}}, ref);
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
