package net.elrid.hardmode.zone;

import net.elrid.hardmode.zone.effects.LethalPoisonFourEffect;
import net.elrid.hardmode.zone.effects.LethalPoisonOneEffect;
import net.elrid.hardmode.zone.effects.LethalPoisonTreeEffect;
import net.elrid.hardmode.zone.effects.LethalPoisonTwoEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.elrid.hardmode.HardMode;

public class ModEffects {

    public static Holder<MobEffect> LETHAL_POISON_ONE;
    public static Holder<MobEffect> LETHAL_POISON_TWO;
    public static Holder<MobEffect> LETHAL_POISON_TREE;
    public static Holder<MobEffect> LETHAL_POISON_FOUR;


    public static void register() {
        LETHAL_POISON_ONE = Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "lethal_poison"),
                new LethalPoisonOneEffect()
        );
        LETHAL_POISON_TWO = Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "lethal_poison_2"),
                new LethalPoisonTwoEffect()
        );
        LETHAL_POISON_TREE = Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "lethal_poison_3"),
                new LethalPoisonTreeEffect()
        );
        LETHAL_POISON_FOUR = Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "lethal_poison_4"),
                new LethalPoisonFourEffect()
        );
        HardMode.LOGGER.info("LethalPoisonEffect зарегистрирован");
    }
}