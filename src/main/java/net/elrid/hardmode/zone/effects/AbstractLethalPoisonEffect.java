package net.elrid.hardmode.zone.effects;

import net.elrid.hardmode.HardMode;
import net.elrid.hardmode.zone.ModDamageTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public abstract class AbstractLethalPoisonEffect extends MobEffect {

    private static final float LOW_HP_THRESHOLD = 0.7F;
    private static final int LOW_HP_SAFE_DURATION = 40;

    protected AbstractLethalPoisonEffect() {
        super(MobEffectCategory.HARMFUL, 0x4E9331);
    }

    // ✅ Ссылка на нашу кастомную текстуру
    private static final Identifier CUSTOM_ICON =
            Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "mob_effect/lethal_poison");

    /** Интервал удара в тиках (30, 20, 15, 10). */
    public abstract int damageInterval();

    /** Сколько всего должен длиться эффект (10 секунд = 200 тиков). */
    public abstract int totalDuration();

    /** Урон за один тик удара. */
    protected abstract float damage();

    protected boolean canKill() {
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % damageInterval() == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        MobEffectInstance inst = findInstance(entity);
        int remaining = (inst != null) ? inst.getDuration() : 0;


        float damage = damage();

        if (!canKill()) {
            float maxAllowed = entity.getHealth() - 1.0F;
            if (maxAllowed <= 0.0F) {
                // Уже на 1 HP или ниже — вообще не бьём.
                damage = 0.0F;
            } else if (damage > maxAllowed) {
                damage = maxAllowed;
            }
        }

        // Не добивать на низком HP в конце эффекта
        if (entity.getHealth() <= LOW_HP_THRESHOLD && remaining < LOW_HP_SAFE_DURATION) {
            damage = 0;
        }

        Holder<DamageType> damageType = level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ModDamageTypes.LETHAL_POISON);

        entity.hurtServer(level, new DamageSource(damageType), damage);

        return true;
    }

    /** Находит MobEffectInstance этой сущности, чей эффект == this. */
    private MobEffectInstance findInstance(LivingEntity entity) {
        for (MobEffectInstance active : entity.getActiveEffects()) {
            if (active.getEffect().value() == this) {
                return active;
            }
        }
        return null;
    }
}

