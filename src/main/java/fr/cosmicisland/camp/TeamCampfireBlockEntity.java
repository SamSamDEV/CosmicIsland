package fr.cosmicisland.camp;

import fr.cosmicisland.survival.GourdeItem;
import fr.cosmicisland.survival.MarmiteItem;
import fr.cosmicisland.survival.SurvivalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Contenu du feu de camp d'equipe : marmite, ingredient / combustible, gourde, sortie.
 * Rien ne fonctionne tant que le feu est eteint (jauge a 0 %).
 *  - un sac de riz + une marmite d'eau -> riz en cuisson, puis des coquillages de riz cuit en sortie ;
 *  - du bois / du charbon dans la case ingredient alimente la jauge ;
 *  - une gourde d'eau non purifiee est purifiee apres un delai.
 */
public class TeamCampfireBlockEntity extends BlockEntity {
    private float speedAcc;   // cumul du bonus de vitesse (non sauvegarde)
    public static final int SLOT_POT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_FLASK = 2;
    public static final int SLOT_OUT = 3;
    public static final int SLOT_GRILL = 4;   // 4 a 7 : cases de grill (poisson, poulet...)
    public static final int SLOT_WOOD = 8;    // bois / combustible pour nourrir le feu
    public static final int SIZE = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final int[] grillTicks = new int[4];
    private final int[] grillTotal = new int[4];
    private int cookTicks = 0;
    private int boilTicks = 0;
    private boolean outputBlocked = false;

    public TeamCampfireBlockEntity(BlockPos pos, BlockState state) {
        super(SurvivalRegistry.FEU_BE.get(), pos, state);
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    // ------------------------------------------------------------ avancement (pour l'interface)

    public static int cookTotal() {
        return Math.max(1, (int) (CampfireManager.config().cookSeconds * 20));
    }

    public static int boilTotal() {
        return Math.max(1, (int) (CampfireManager.config().boilSeconds * 20));
    }

    public int cookPercent() {
        return Math.min(100, cookTicks * 100 / cookTotal());
    }

    public int boilPercent() {
        return Math.min(100, boilTicks * 100 / boilTotal());
    }

    public boolean outputBlocked() {
        return outputBlocked;
    }

    public ItemStack grillItem(int i) {
        return items.get(SLOT_GRILL + i);
    }

    /** Donnees envoyees a l'ecran du feu (voir FireMenu.D_*). */
    public int menuData(int i) {
        BlockState state = getBlockState();
        ItemStack pot = items.get(SLOT_POT);
        ItemStack flask = items.get(SLOT_FLASK);
        switch (i) {
            case FireMenu.D_LIT:
                return state.hasProperty(TeamCampfireBlock.LIT) && state.getValue(TeamCampfireBlock.LIT) ? 1 : 0;
            case FireMenu.D_PCT:
                return level instanceof ServerLevel sl ? Math.max(0, CampfireManager.percentAt(sl, worldPosition)) : 0;
            case FireMenu.D_COOK:
                return pot.getItem() instanceof MarmiteItem && MarmiteItem.state(pot) == MarmiteItem.RICE ? cookPercent() : -1;
            case FireMenu.D_FLASK:
                return !(flask.getItem() instanceof GourdeItem) ? -1 : GourdeItem.sips(flask) <= 0 ? 0 : GourdeItem.purified(flask) ? 2 : 1;
            case FireMenu.D_BOIL:
                return boilPercent();
            case FireMenu.D_BLOCKED:
                return outputBlocked ? 1 : 0;
            default:
                int g = i - FireMenu.D_GRILL;
                if (g < 0 || g > 3 || items.get(SLOT_GRILL + g).isEmpty() || grillTotal[g] <= 0) {
                    return -1;
                }
                return Math.min(100, grillTicks[g] * 100 / grillTotal[g]);
        }
    }

    // ------------------------------------------------------------ synchronisation (affichage du grill)

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------ tick serveur

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeamCampfireBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        // Apparence de la marmite sur le grill
        ItemStack pot = be.items.get(SLOT_POT);
        int potVisual = pot.getItem() instanceof MarmiteItem ? 1 + MarmiteItem.state(pot) : 0;
        if (state.getValue(TeamCampfireBlock.POT) != potVisual) {
            state = state.setValue(TeamCampfireBlock.POT, potVisual);
            level.setBlock(pos, state, 3);
        }
        if (!state.getValue(TeamCampfireBlock.LIT)) {
            // Feu eteint : rien ne fonctionne, la progression est perdue
            if (be.cookTicks != 0 || be.boilTicks != 0) {
                be.cookTicks = 0;
                be.boilTicks = 0;
            }
            java.util.Arrays.fill(be.grillTicks, 0);
            return;
        }
        CampfireConfig cfg = CampfireManager.config();
        long time = level.getGameTime();

        ambience(sl, pos, time);

        // gros feu : la cuisson (riz, grill) et la purification vont plus vite au-dessus du seuil de la config
        int step = 1;
        if (cfg.fastCookBonus > 0.0 && CampfireManager.percentAt(sl, pos) > cfg.fastCookAbovePercent) {
            be.speedAcc += (float) cfg.fastCookBonus;
            if (be.speedAcc >= 1.0f) {
                be.speedAcc -= 1.0f;
                step = 2;
            }
        }

        // 1) combustible dans la case bois : facon four, un objet a la fois.
        // Le suivant n'est consomme que quand le precedent a fini de se verser dans la jauge.
        ItemStack fuel = be.items.get(SLOT_WOOD);
        if (!fuel.isEmpty() && time % 5 == 0) {
            Double points = CampfireManager.fuelPoints(fuel);
            if (points != null && CampfireManager.addFuelAt(sl, pos, points)) {
                fuel.shrink(1);
                be.setChanged();
            }
        }

        // 2) cuisson du riz : marmite d'eau + sac de riz -> riz qui cuit
        fuel = be.items.get(SLOT_FUEL);
        if (pot.getItem() instanceof MarmiteItem && MarmiteItem.state(pot) == MarmiteItem.WATER
                && fuel.is(SurvivalRegistry.SAC_RIZ.get())) {
            fuel.shrink(1);
            MarmiteItem.setState(pot, MarmiteItem.RICE);
            be.cookTicks = 0;
            be.setChanged();
        }
        if (pot.getItem() instanceof MarmiteItem && MarmiteItem.state(pot) == MarmiteItem.RICE) {
            be.cookTicks += step;
            if (be.cookTicks >= cookTotal()) {
                ItemStack out = be.items.get(SLOT_OUT);
                int n = Math.max(1, cfg.riceOutput);
                if (out.isEmpty()) {
                    be.items.set(SLOT_OUT, new ItemStack(SurvivalRegistry.COQUILLAGE_RIZ.get(), n));
                    MarmiteItem.setState(pot, MarmiteItem.EMPTY);
                    be.cookTicks = 0;
                    be.outputBlocked = false;
                    sl.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5f, 1.6f);
                    be.setChanged();
                } else if (out.is(SurvivalRegistry.COQUILLAGE_RIZ.get()) && out.getCount() + n <= out.getMaxStackSize()) {
                    out.grow(n);
                    MarmiteItem.setState(pot, MarmiteItem.EMPTY);
                    be.cookTicks = 0;
                    be.outputBlocked = false;
                    be.setChanged();
                } else {
                    be.cookTicks = cookTotal();   // en attente : la sortie doit etre videe
                    be.outputBlocked = true;
                }
            }
            if (time % 15 == 0) {
                sl.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.55, pos.getZ() + 0.5, 1, 0.12, 0.0, 0.12, 0.01);
            }
        } else if (be.cookTicks != 0) {
            be.cookTicks = 0;
            be.outputBlocked = false;
        }

        // 2 bis) cuisson sur le grill (recettes de feu de camp : poisson, poulet, patate...)
        for (int g = 0; g < 4; g++) {
            ItemStack food = be.items.get(SLOT_GRILL + g);
            if (food.isEmpty()) {
                be.grillTicks[g] = 0;
                be.grillTotal[g] = 0;
                continue;
            }
            SimpleContainer single = new SimpleContainer(food);
            Optional<CampfireCookingRecipe> recipe = sl.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, single, sl);
            if (recipe.isEmpty()) {
                be.grillTicks[g] = 0;
                be.grillTotal[g] = 0;
                continue;
            }
            be.grillTotal[g] = Math.max(20, (int) (recipe.get().getCookingTime() * cfg.grillTimeFactor));
            be.grillTicks[g] += step;
            if (be.grillTicks[g] >= be.grillTotal[g]) {
                ItemStack cooked = recipe.get().assemble(single, sl.registryAccess());
                be.items.set(SLOT_GRILL + g, cooked.copy());
                be.grillTicks[g] = 0;
                be.grillTotal[g] = 0;
                sl.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6f, 1.2f);
                be.setChanged();
            }
        }

        // 3) purification de la gourde
        ItemStack flask = be.items.get(SLOT_FLASK);
        if (flask.getItem() instanceof GourdeItem && GourdeItem.sips(flask) > 0 && !GourdeItem.purified(flask)) {
            be.boilTicks += step;
            if (be.boilTicks >= boilTotal()) {
                GourdeItem.setSips(flask, GourdeItem.sips(flask), true);
                be.boilTicks = 0;
                sl.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.4f);
                be.setChanged();
            }
        } else if (be.boilTicks != 0) {
            be.boilTicks = 0;
        }
    }

    /** Ambiance du feu : crepitements, braises et fumee selon l'intensite (rien quand le feu se nourrit). */
    private static void ambience(ServerLevel sl, BlockPos pos, long time) {
        int pct = CampfireManager.percentAt(sl, pos);
        float i = pct < 0 ? 0.5f : Math.max(0f, Math.min(1f, pct / 100f));
        net.minecraft.util.RandomSource r = sl.random;
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5, cy = pos.getY() + 0.55;
        // crepitements : plus frequents et plus forts quand le feu est grand
        if (time % 10 == 0 && r.nextFloat() < 0.12f + 0.55f * i) {
            sl.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.25f + 0.5f * i, 0.85f + r.nextFloat() * 0.45f);
        }
        // braises qui montent (feu moyen a grand)
        if (i > 0.25f && time % Math.max(4, (int) (14 - 10 * i)) == 0) {
            sl.sendParticles(ParticleTypes.LAVA, cx + (r.nextDouble() - 0.5) * 0.4, cy + 0.1, cz + (r.nextDouble() - 0.5) * 0.4, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // etincelles vives (grand feu)
        if (i > 0.6f && time % 6 == 0 && r.nextFloat() < i) {
            sl.sendParticles(ParticleTypes.FLAME, cx + (r.nextDouble() - 0.5) * 0.3, cy + 0.2, cz + (r.nextDouble() - 0.5) * 0.3,
                    0, (r.nextDouble() - 0.5) * 0.04, 0.06 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.04, 1.0);
        }
        // fumee : mince quand le feu est fort, epaisse quand il faiblit
        int every = i > 0.5f ? 12 : (i > 0.2f ? 7 : 4);
        if (time % every == 0) {
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx + (r.nextDouble() - 0.5) * 0.3, cy + 0.5, cz + (r.nextDouble() - 0.5) * 0.3,
                    0, 0.0, 0.07, 0.0, 1.0);
        }
        if (i <= 0.2f && time % 10 == 0) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy + 0.4, cz, 1, 0.12, 0.05, 0.12, 0.01);
        }
    }

    // ------------------------------------------------------------ sauvegarde

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Cook", cookTicks);
        tag.putInt("Boil", boilTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, items);
        cookTicks = tag.getInt("Cook");
        boilTicks = tag.getInt("Boil");
    }
}
