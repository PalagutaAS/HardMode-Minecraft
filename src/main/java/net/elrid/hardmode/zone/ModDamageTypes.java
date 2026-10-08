package net.elrid.hardmode.zone;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.elrid.hardmode.HardMode;

public class ModDamageTypes {

    public static final ResourceKey<DamageType> LETHAL_POISON =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "lethal_poison"));

    // Не нужно регистрировать вручную — датапак сделает это автоматически.
    // Достаточно иметь ключ, чтобы получить Holder в нужный момент.
}