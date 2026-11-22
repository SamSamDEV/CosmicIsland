package fr.cosmicisland.survival;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.Tr;
import fr.cosmicisland.Txt;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * /ccis food : integre les aliments et boissons des autres mods dans la soif. On tient l'objet en main
 * (info, set, remove), ou on regle un tag entier (forge:raw_fishes...).
 */
public final class FoodCommand {
    private FoodCommand() {}

    private static String describe(ThirstConfig.Drink d) {
        return Tr.s("food.rule", d.hydration, d.saturation, Math.round(d.effectChance * 100), d.effectSeconds);
    }

    private static ItemStack held(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            ItemStack stack = p.getMainHandItem();
            if (stack.isEmpty()) {
                ctx.getSource().sendFailure(Txt.err(Tr.s("food.err.empty")));
                return null;
            }
            return stack;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Txt.err(Tr.s("food.err.empty")));
            return null;
        }
    }

    private static String id(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "?" : key.toString();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ItemStack stack = held(ctx);
        if (stack == null) {
            return 0;
        }
        ThirstConfig.Drink own = ThirstManager.config().drinks.get(id(stack));
        ThirstConfig.Drink any = ThirstManager.rule(stack);
        String text = any == null ? Tr.s("food.info.none", id(stack))
                : Tr.s(own != null ? "food.info.item" : "food.info.tag", id(stack), describe(any));
        ctx.getSource().sendSuccess(() -> Txt.info(text), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, float saturation, double chance) {
        ItemStack stack = held(ctx);
        if (stack == null) {
            return 0;
        }
        int hydration = IntegerArgumentType.getInteger(ctx, "hydration");
        ThirstConfig.Drink d = new ThirstConfig.Drink(hydration, saturation, chance, 15);
        ThirstManager.config().drinks.put(id(stack), d);
        ThirstManager.saveConfig();
        ctx.getSource().sendSuccess(() -> Txt.ok(Tr.s("food.set", id(stack), describe(d))), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        ItemStack stack = held(ctx);
        if (stack == null) {
            return 0;
        }
        boolean had = ThirstManager.config().drinks.remove(id(stack)) != null;
        ThirstManager.saveConfig();
        String text = Tr.s(had ? "food.removed" : "food.notfound", id(stack));
        ctx.getSource().sendSuccess(() -> Txt.ok(text), true);
        return had ? 1 : 0;
    }

    private static int tagSet(CommandContext<CommandSourceStack> ctx, float saturation, double chance) {
        String tag = StringArgumentType.getString(ctx, "tag");
        int hydration = IntegerArgumentType.getInteger(ctx, "hydration");
        ThirstConfig.Drink d = new ThirstConfig.Drink(hydration, saturation, chance, 15);
        ThirstManager.config().tags.put(tag, d);
        ThirstManager.saveConfig();
        ctx.getSource().sendSuccess(() -> Txt.ok(Tr.s("food.tag.set", tag, describe(d))), true);
        return 1;
    }

    private static int tagRemove(CommandContext<CommandSourceStack> ctx) {
        String tag = StringArgumentType.getString(ctx, "tag");
        boolean had = ThirstManager.config().tags.remove(tag) != null;
        ThirstManager.saveConfig();
        String text = Tr.s(had ? "food.tag.removed" : "food.notfound", tag);
        ctx.getSource().sendSuccess(() -> Txt.ok(text), true);
        return had ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder(Tr.s("food.list.title", ThirstManager.config().drinks.size(), ThirstManager.config().tags.size()));
        for (Map.Entry<String, ThirstConfig.Drink> e : ThirstManager.config().tags.entrySet()) {
            sb.append("\n &6#").append(e.getKey()).append(" &7: &f").append(describe(e.getValue()));
        }
        for (Map.Entry<String, ThirstConfig.Drink> e : ThirstManager.config().drinks.entrySet()) {
            if (!e.getKey().startsWith("legendarysurvivaloverhaul:")) {
                sb.append("\n &e").append(e.getKey()).append(" &7: &f").append(describe(e.getValue()));
            }
        }
        ctx.getSource().sendSuccess(() -> Txt.info(sb.toString()), false);
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("food")
                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                .then(Commands.literal("info").executes(FoodCommand::info))
                .then(Commands.literal("list").executes(FoodCommand::list))
                .then(Commands.literal("remove").executes(FoodCommand::remove))
                .then(Commands.literal("set")
                        .then(Commands.argument("hydration", IntegerArgumentType.integer(-20, 20))
                                .executes(c -> set(c, 0.5f, 0.0))
                                .then(Commands.argument("saturation", DoubleArgumentType.doubleArg(0, 20))
                                        .executes(c -> set(c, (float) DoubleArgumentType.getDouble(c, "saturation"), 0.0))
                                        .then(Commands.argument("chance", IntegerArgumentType.integer(0, 100))
                                                .executes(c -> set(c, (float) DoubleArgumentType.getDouble(c, "saturation"),
                                                        IntegerArgumentType.getInteger(c, "chance") / 100.0))))))
                .then(Commands.literal("tag")
                        .then(Commands.literal("remove")
                                .then(Commands.argument("tag", StringArgumentType.word()).executes(FoodCommand::tagRemove)))
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .then(Commands.argument("hydration", IntegerArgumentType.integer(-20, 20))
                                        .executes(c -> tagSet(c, 0.5f, 0.0))
                                        .then(Commands.argument("saturation", DoubleArgumentType.doubleArg(0, 20))
                                                .executes(c -> tagSet(c, (float) DoubleArgumentType.getDouble(c, "saturation"), 0.0))
                                                .then(Commands.argument("chance", IntegerArgumentType.integer(0, 100))
                                                        .executes(c -> tagSet(c, (float) DoubleArgumentType.getDouble(c, "saturation"),
                                                                IntegerArgumentType.getInteger(c, "chance") / 100.0)))))));
    }
}
