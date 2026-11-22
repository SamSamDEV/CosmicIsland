package fr.cosmicisland.conseil.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.cosmicisland.CosmicIsland;
import com.mojang.math.Transformation;
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

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.function.Function;

/**
 * Anneau d'Exemption : modele ARRONDI genere par code (vrai tore d'or + gemme en losange),
 * cuit une seule fois dans le chunk (aucun cout par image). Declare par "loader": "cosmicisland:ring".
 */
public final class RingModel {
    private RingModel() {}

    public static final class Geometry implements IUnbakedGeometry<Geometry> {
        final double cx, cy, cz, scale;

        Geometry(double cx, double cy, double cz, double scale) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.scale = scale;
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                               ItemOverrides overrides, ResourceLocation modelLocation) {
            TextureAtlasSprite tex = spriteGetter.apply(context.getMaterial("0"));
            TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
            IModelBuilder<?> builder = IModelBuilder.of(false, context.useBlockLight(), true,
                    context.getTransforms(), overrides, particle, RenderTypeGroup.EMPTY);
            new Mesh(builder, tex, cx, cy, cz, scale, modelState.getRotation()).build();
            return builder.build();
        }
    }

    public static final class Loader implements IGeometryLoader<Geometry> {
        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext ctx) throws JsonParseException {
            // "cx", "cy", "cz" : centre de l'anneau (par defaut : au milieu du bloc, pour l'objet ; le bloc pose le colle a une paroi)
            double cx = json.has("cx") ? json.get("cx").getAsDouble() : 0.5;
            double cy = json.has("cy") ? json.get("cy").getAsDouble() : 0.45;
            double cz = json.has("cz") ? json.get("cz").getAsDouble() : 0.5;
            double scale = json.has("scale") ? json.get("scale").getAsDouble() : 1.0;
            return new Geometry(cx, cy, cz, scale);
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
        final double CX, CY, CZ;
        final double RM, RT;      // rayon du cercle / rayon du jonc (multiplies par "scale")
        final double scale;
        final Matrix4f rot;       // rotation du blockstate (x / y) : un modele a loader ne la recoit pas tout seul
        static final int SEG = 20, SIDES = 8;

        private final IModelBuilder<?> builder;
        private final TextureAtlasSprite tex;
        private final QuadBakingVertexConsumer baker;

        Mesh(IModelBuilder<?> builder, TextureAtlasSprite tex, double cx, double cy, double cz, double scale, Transformation rotation) {
            this.scale = scale;
            this.RM = 0.22 * scale;
            this.RT = 0.05 * scale;
            this.rot = rotation == null ? new Matrix4f() : rotation.getMatrix();
            this.CX = cx;
            this.CY = cy;
            this.CZ = cz;
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
            double gy = CY + RM + RT * 0.6, h = 0.09 * scale, w = 0.06 * scale;   // sertie dans le jonc
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
        /** Applique la rotation du blockstate autour du centre du bloc. */
        double[] turn(double[] v) {
            Vector4f t = new Vector4f((float) (v[0] - 0.5), (float) (v[1] - 0.5), (float) (v[2] - 0.5), 1f);
            rot.transform(t);
            return new double[]{t.x + 0.5, t.y + 0.5, t.z + 0.5};
        }

        void quad(double[][] p0, float[][] uv, double[] ref0) {
            double[][] p = {turn(p0[0]), turn(p0[1]), turn(p0[2]), turn(p0[3])};
            double[] ref = turn(ref0);
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
