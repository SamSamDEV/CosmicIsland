package fr.cosmicisland.survival;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Deshydratation : la soif baisse plus vite (le multiplicateur est applique par ThirstManager). */
public class DehydrationEffect extends MobEffect {
    public DehydrationEffect() {
        super(MobEffectCategory.HARMFUL, 0x8DB360);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return fr.cosmicisland.Tr.c(getDescriptionId());
    }
}
