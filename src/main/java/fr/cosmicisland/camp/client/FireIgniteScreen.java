package fr.cosmicisland.camp.client;

import com.mojang.math.Axis;
import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.Tr;
import fr.cosmicisland.camp.FireNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Allumage du feu : une planche de bois et un baton. On maintient le clic gauche et on frotte le baton de gauche a droite
 * avec la souris, comme avec une vraie charrue a feu. La fumee monte, puis la braise rougeoie, puis la flamme prend.
 */
public class FireIgniteScreen extends Screen {
    private static final ResourceLocation ART = new ResourceLocation(CosmicIsland.MODID, "textures/gui/ignite.png");
    private static final int BOARD_W = 192, BOARD_H = 40, STICK_W = 96, STICK_H = 8;
    private static final float SCALE = 2.0f;
    private static final int MIN_TRAVEL = 46;

    private static final class Puff {
        float x, y, vx, vy;   // px et px/s
        float age, life;      // secondes
        boolean ember;
    }

    private final List<Puff> puffs = new ArrayList<>();
    private boolean pressed;
    private float tipX, mouseYf;
    private int slipTicks;
    private boolean slipping;
    private float fallTime;
    private long lastNs = 0L;
    private float time;
    private float moveAcc, smokeAcc;
    private boolean pendingClose;
    private float fallX, fallY, fallVX, fallVY, fallAng, fallSpin;
    private float lastExtreme;
    private int dir;          // +1 vers la droite, -1 vers la gauche, 0 = aucun
    private int lastSent = -1;
    private long lastStrokeTick = -100;
    private final java.util.ArrayDeque<Integer> history = new java.util.ArrayDeque<>();
    private boolean closedSent;
    private float shownProgress;
    private int ticks;

    public FireIgniteScreen() {
        super(Tr.c("screen.cosmicisland.ignite.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        tipX = width / 2f;
        lastExtreme = tipX;
    }

    private float boardX() {
        return width / 2f - BOARD_W * SCALE / 2f;
    }

    private float boardY() {
        return height / 2f - BOARD_H * SCALE / 2f + 18;
    }

    private float grooveY() {
        return boardY() + 17 * SCALE;
    }

    private float tolerance() {
        return 28f - 12f * Math.min(1f, Math.max(0f, shownProgress));
    }

    private float tipY() {
        float dy = Math.max(-34f, Math.min(34f, mouseYf - grooveY()));
        return grooveY() + dy * 0.8f;
    }

    private void startSlip() {
        if (slipping) {
            return;
        }
        slipping = true;
        pressed = false;
        fallTime = 0f;
        fallX = tipX;
        fallY = tipY();
        fallVX = (dir == 0 ? 1 : dir) * 150f;
        fallVY = -260f;
        fallAng = -26f;
        fallSpin = (dir == 0 ? 1 : dir) * 300f;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BAMBOO_HIT, 0.7f, 0.8f));
    }

    private void moveTo(double mouseX, double mouseY) {
        mouseYf = (float) mouseY;
        if (slipping) {
            return;
        }
        float lo = boardX() + 14 * SCALE, hi = boardX() + (BOARD_W - 14) * SCALE;
        float x = (float) Math.max(lo, Math.min(hi, mouseX));
        float dx = x - tipX;
        tipX = x;
        if (!pressed) {
            lastExtreme = x;
            dir = 0;
            return;
        }
        if (Math.abs(dx) > 0.5f) {
            int d = dx > 0 ? 1 : -1;
            if (d != dir) {
                // changement de sens : le point de depart du balayage est ici
                dir = d;
                lastExtreme = x - dx;
            }
            if (Math.abs(x - lastExtreme) >= MIN_TRAVEL) {
                int side = d > 0 ? 1 : 0;
                if (side != lastSent) {
                    sendStroke(side);
                }
                lastExtreme = x;
            }
            moveAcc += Math.abs(dx);
            while (moveAcc >= 9f) {
                moveAcc -= 9f;
                emit(false);
            }
        }
    }

    private void sendStroke(int side) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        long interval = now - lastStrokeTick;
        if (interval <= 3) {
            return;
        }
        lastSent = side;
        lastStrokeTick = now;
        int quality = interval <= 12 ? 2 : interval <= 22 ? 1 : 0;
        history.addLast(quality);
        while (history.size() > 10) {
            history.removeFirst();
        }
        FireNetwork.CHANNEL.sendToServer(new FireNetwork.TapPacket(side));
        for (int i = 0; i < 3; i++) {
            emit(true);
        }
    }

    private void emit(boolean ember) {
        Puff p = new Puff();
        p.x = tipX + (float) (Math.random() - 0.5) * 8;
        p.y = grooveY();
        p.vx = (float) (Math.random() - 0.5) * 46f;
        p.vy = -(38f + (float) Math.random() * 62f);
        p.life = 0.45f + (float) Math.random() * 0.5f;
        p.ember = ember && FireClient.progress > 0.35f;
        puffs.add(p);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !slipping) {
            pressed = true;
            lastExtreme = tipX;
            dir = 0;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            pressed = false;
            dir = 0;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        moveTo(mx, my);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        moveTo(mx, my);
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        if (pendingClose) {
            onClose();
            return;
        }
        if (!slipping && pressed) {
            float err = Math.abs(mouseYf - grooveY());
            float tol = tolerance();
            if (err > tol) {
                slipTicks++;
            } else {
                slipTicks = Math.max(0, slipTicks - 1);
            }
            if (slipTicks >= 5 || err > tol * 1.7f) {
                startSlip();
            }
        }
        // le serveur a termine (feu allume ou session perdue) : on ferme
        if (FireClient.progress < 0f && ticks > 5) {
            closedSent = true;
            onClose();
        }
    }

    /** Animation a chaque image (60 FPS et plus), independante des ticks du jeu. */
    private void animate(float dt) {
        time += dt;
        float target = Math.max(0f, FireClient.progress);
        shownProgress += (target - shownProgress) * (1f - (float) Math.exp(-14f * dt));
        for (int i = puffs.size() - 1; i >= 0; i--) {
            Puff p = puffs.get(i);
            p.age += dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx += (float) (Math.random() - 0.5) * 140f * dt;
            if (p.age >= p.life) {
                puffs.remove(i);
            }
        }
        if (!slipping && shownProgress > 0.12f) {
            smokeAcc += dt * (8f + 26f * shownProgress);
            while (smokeAcc >= 1f) {
                smokeAcc -= 1f;
                emit(false);
            }
        }
        if (slipping) {
            fallTime += dt;
            fallX += fallVX * dt;
            fallY += fallVY * dt;
            fallVY += 1100f * dt;
            fallAng += fallSpin * dt;
            if (fallTime >= 0.5f && !pendingClose) {
                pendingClose = true;
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(Tr.c("hud.cosmicisland.ignite.slip"), true);
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long nowNs = System.nanoTime();
        float dt = lastNs == 0L ? 0f : Math.min(0.05f, (nowNs - lastNs) / 1.0e9f);
        lastNs = nowNs;
        animate(dt);
        renderBackground(g);
        float bx = boardX(), by = boardY(), gy = grooveY();
        // planche
        g.pose().pushPose();
        g.pose().translate(bx, by, 0);
        g.pose().scale(SCALE, SCALE, 1f);
        g.blit(ART, 0, 0, 0, 0, BOARD_W, BOARD_H, 256, 128);
        g.pose().popPose();
        // braise qui rougeoie autour de la pointe
        float heat = shownProgress;
        if (heat > 0.05f) {
            for (int r = 5; r >= 1; r--) {
                int a = (int) (Math.min(1f, heat * 1.2f) * (r == 1 ? 210 : 60 / r + 10));
                int rad = (int) (r * (3 + heat * 6));
                int color = (a << 24) | (heat > 0.7f ? 0xFFC83C : 0xE2501C);
                g.fill((int) tipX - rad, (int) gy - rad / 2, (int) tipX + rad, (int) gy + rad / 2, color);
            }
        }
        // fumee et etincelles
        for (Puff p : puffs) {
            float t = p.age / p.life;
            int size = p.ember ? 2 : 3 + (int) (t * 5);
            int a = (int) ((1f - t) * (p.ember ? 230 : 120));
            int c = p.ember ? 0xFFB020 : 0xC8C8C8;
            g.fill((int) p.x - size / 2, (int) p.y - size / 2, (int) p.x + size / 2 + 1, (int) p.y + size / 2 + 1, (a << 24) | c);
        }
        // baton : la pointe est dans la rainure quand on appuie, sinon il flotte au-dessus ; il glisse si on derive
        g.pose().pushPose();
        if (slipping) {
            g.pose().translate(fallX, fallY, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(fallAng));
        } else {
            float lift = pressed ? 0f : 12f;
            float err = Math.abs(mouseYf - gy);
            float warn = pressed && err > tolerance() * 0.6f ? 1f : 0f;
            float wobble = pressed ? (float) Math.sin(time * 34f) * (0.6f + warn * 1.8f) : 0f;
            g.pose().translate(tipX, (pressed ? tipY() : gy) - lift + wobble, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-26f + (pressed ? 0f : -6f) + warn * (float) Math.sin(time * 46f) * 3f));
        }
        g.pose().scale(SCALE, SCALE, 1f);
        g.blit(ART, -2, -STICK_H / 2, 0, 44, STICK_W, STICK_H, 256, 128);
        g.pose().popPose();
        if (!slipping && pressed && Math.abs(mouseYf - gy) > tolerance() * 0.6f && ((int) (time * 9f)) % 2 == 0) {
            g.drawCenteredString(font, Tr.c("hud.cosmicisland.ignite.warn"), width / 2, (int) by - 14, 0xFFFF5040);
        }
        // titre, consigne, jauge et rythme
        g.drawCenteredString(font, title, width / 2, 26, 0xFFFFE9B0);
        g.drawCenteredString(font, Tr.c("hud.cosmicisland.ignite"), width / 2, 42, 0xFFFFFFFF);
        int bw = 180, x = width / 2 - bw / 2, y = (int) (by + BOARD_H * SCALE + 22);
        g.fill(x - 2, y - 2, x + bw + 2, y + 10, 0xCC000000);
        g.fill(x, y, x + bw, y + 8, 0xFF2A2A2A);
        int filled = (int) (bw * Math.min(1f, shownProgress));
        for (int i = 0; i < filled; i++) {
            float f = i / (float) bw;
            int r = Math.min(255, 190 + (int) (65 * f));
            int gr = Math.min(255, 50 + (int) (190 * f));
            g.fill(x + i, y, x + i + 1, y + 8, 0xFF000000 | (r << 16) | (gr << 8) | 20);
        }
        int px = width / 2 - history.size() * 4;
        for (int q : history) {
            g.fill(px, y + 14, px + 6, y + 18, q == 2 ? 0xFF50E050 : q == 1 ? 0xFFF2A81E : 0xFFD83A1E);
            px += 8;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, 0xB0100A06, 0xD0100A06);
    }

    @Override
    public void removed() {
        if (!closedSent) {
            closedSent = true;
            FireNetwork.CHANNEL.sendToServer(new FireNetwork.TapPacket(2));
        }
    }
}
