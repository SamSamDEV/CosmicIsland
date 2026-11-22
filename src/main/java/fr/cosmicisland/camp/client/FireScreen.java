package fr.cosmicisland.camp.client;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.camp.FireMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import fr.cosmicisland.survival.GourdeItem;
import fr.cosmicisland.survival.MarmiteItem;
import net.minecraft.world.entity.player.Inventory;
import fr.cosmicisland.survival.SurvivalRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ecran du feu de camp. Le fond est textures/gui/fire.png (256x256, panneau 176x186) : remplace ce fichier pour changer
 * le design ; les positions des cases sont dans FireMenu.POS, les barres sont dessinees ici.
 */
public class FireScreen extends AbstractContainerScreen<FireMenu> {
    private static final ResourceLocation BG = new ResourceLocation(CosmicIsland.MODID, "textures/gui/fire.png");
    private static final int TEXT = 0xFF4A2E18;

    public FireScreen(FireMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 186;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            for (int i = 0; i < GHOST_SLOTS.length; i++) {
                if (menu.slots.indexOf(hoveredSlot) == GHOST_SLOTS[i]) {
                    g.renderTooltip(font, fr.cosmicisland.Tr.c("screen.cosmicisland.fire." + GHOST_KEYS[i]), mouseX, mouseY);
                }
            }
        }
    }

    private static final int[] GHOST_SLOTS = {8, 0, 2, 3};
    private static final String[] GHOST_KEYS = {"wood", "pot", "flask", "out"};
    /** Taille des icones fantomes : bois -15 %, gourde -10 %. */
    private static final float[] GHOST_SCALE = {0.85f, 1.0f, 0.9f, 1.0f};
    private static final ItemStack[] GHOSTS = new ItemStack[4];

    private static ItemStack ghost(int i) {
        if (GHOSTS[i] == null) {
            GHOSTS[i] = switch (i) {
                case 0 -> new ItemStack(Items.OAK_LOG);
                case 1 -> new ItemStack(SurvivalRegistry.MARMITE.get());
                case 2 -> new ItemStack(SurvivalRegistry.GOURDE.get());
                default -> new ItemStack(SurvivalRegistry.COQUILLAGE_RIZ.get());
            };
        }
        return GHOSTS[i];
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight);

        // icones fantomes dans les cases vides (opacite reduite)
        for (int i = 0; i < GHOST_SLOTS.length; i++) {
            int slotIndex = GHOST_SLOTS[i];
            if (!menu.getSlot(slotIndex).hasItem()) {
                int gx = x + FireMenu.POS[slotIndex][0];
                int gy = y + FireMenu.POS[slotIndex][1];
                float gs = GHOST_SCALE[i];
                g.pose().pushPose();
                g.pose().translate(gx + 8, gy + 8, 0);
                g.pose().scale(gs, gs, 1.0f);
                g.pose().translate(-8, -8, 0);
                g.renderItem(ghost(i), 0, 0);
                g.pose().popPose();
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(gx, gy, gx + 16, gy + 16, 0xA8E0C8A2);
                g.pose().popPose();
            }
        }

        // jauge du feu
        boolean lit = menu.data(FireMenu.D_LIT) == 1;
        int pct = Math.max(0, Math.min(100, menu.data(FireMenu.D_PCT)));
        if (lit) {
            int w = 158 * pct / 100;
            g.fill(x + 9, y + 88, x + 9 + w, y + 92, pct > 50 ? 0xFFF2A81E : pct > 20 ? 0xFFE0701E : 0xFFD83A1E);
            g.fill(x + 9, y + 88, x + 9 + w, y + 89, 0x55FFFFFF);
        }
        // marmite : eau (bleu) ou cuisson du riz (rouge), sous la case
        ItemStack pot = menu.getSlot(0).getItem();
        int px = x + FireMenu.POS[0][0];
        int py = y + FireMenu.POS[0][1] + 20;
        if (pot.getItem() instanceof MarmiteItem) {
            int ps = MarmiteItem.state(pot);
            int cook = menu.data(FireMenu.D_COOK);
            if (ps == MarmiteItem.WATER) {
                g.fill(px, py, px + 17, py + 3, 0xFF2E7DD6);
            } else if (ps == MarmiteItem.RICE && cook >= 0) {
                boolean blocked = menu.data(FireMenu.D_BLOCKED) == 1;
                g.fill(px, py, px + 17 * cook / 100, py + 3, blocked ? 0xFF7A1208 : 0xFFE01818);
            }
        }
        // gourde : gorgees restantes, sous la case
        ItemStack flask = menu.getSlot(2).getItem();
        if (flask.getItem() instanceof GourdeItem && GourdeItem.sips(flask) > 0) {
            int fx = x + FireMenu.POS[2][0];
            int fy = y + FireMenu.POS[2][1] + 20;
            int fw = Math.max(1, 17 * GourdeItem.sips(flask) / GourdeItem.maxSips());
            g.fill(fx, fy, fx + fw, fy + 3, GourdeItem.purified(flask) ? 0xFF5CD0F5 : 0xFF2E7DD6);
        }
        // cuisson de chaque case de grill
        for (int i = 0; i < 4; i++) {
            int p = menu.data(FireMenu.D_GRILL + i);
            if (p >= 0) {
                int sx = x + FireMenu.POS[FireMenu.SLOT_GRILL_FIRST + i][0];
                int sy = y + FireMenu.POS[FireMenu.SLOT_GRILL_FIRST + i][1];
                g.fill(sx, sy + 17, sx + 16, sy + 18, 0xFF241608);
                g.fill(sx, sy + 17, sx + 16 * p / 100, sy + 18, 0xFFE01818);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, fr.cosmicisland.Tr.c("screen.cosmicisland.fire.title"), 8, 6, TEXT, false);
        if (menu.data(FireMenu.D_LIT) != 1) {
            Component state = fr.cosmicisland.Tr.c("screen.cosmicisland.fire.unlit");
            g.drawString(font, state, 168 - font.width(state), 6, 0xFFB03A1E, false);
        }
    }

    private void cl(GuiGraphics g, String key, int cx, int y) {
        Component c = fr.cosmicisland.Tr.c("screen.cosmicisland.fire." + key);
        g.drawString(font, c, cx - font.width(c) / 2, y, TEXT, false);
    }
}
