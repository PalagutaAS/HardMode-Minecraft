package net.elrid.hardmode.zone.effects;

import net.elrid.hardmode.HardMode;
import net.elrid.hardmode.zone.ModDamageTypes;
import net.elrid.hardmode.zone.SafeZoneConfig;
import net.elrid.hardmode.zone.SafeZoneState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractLethalPoisonEffect extends MobEffect {

    private static final float LOW_HP_THRESHOLD = 0.5F;
    private static final int LOW_HP_SAFE_DURATION = 15;

    protected AbstractLethalPoisonEffect() {
        super(MobEffectCategory.HARMFUL, 0x4E9331);
    }

    // ✅ Ссылка на нашу кастомную текстуру
    private static final Identifier CUSTOM_ICON =
            Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "mob_effect/lethal_poison");

    /** Интервал удара в тиках (30, 20, 15, 10). */
    public abstract int damageInterval();


    /** Урон за один тик удара. */
    protected abstract float damage();

    protected boolean canKill() {
        return true;
    }

    public int totalDuration() { return SafeZoneConfig.POISON_EFFECT_DURATION; }

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
        if (damage != 0
                && mercyEnabled()
                && entity.getHealth() <= LOW_HP_THRESHOLD
                && remaining < LOW_HP_SAFE_DURATION
                && isSafeFromPoison(level, entity)) {
            damage = 0;
        }

        Holder<DamageType> damageType = level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ModDamageTypes.LETHAL_POISON);

        entity.hurtServer(level, new DamageSource(damageType), damage);

        return true;
    }

    /**
     * ★ Щадить ли игрока, если он в зоне / под водой и HP низкий.
     * Переопределите на false в старших тирах — они не дают поблажек.
     */
    protected boolean mercyEnabled() {
        return true;
    }

    /**
     * Игрок вне опасности, если он в зоне или под водой.
     * Спасает от добивания в момент, когда игрок уже вернулся, но эффект ещё висит.
     */
    private boolean isSafeFromPoison(ServerLevel level, LivingEntity entity) {
        if (entity.isUnderWater()) return true;

        Vec3 center = SafeZoneState.getCenter(level);
        double radius = SafeZoneState.getRadius(level);
        double dx = entity.getX() - center.x;
        double dz = entity.getZ() - center.z;
        return dx * dx + dz * dz <= radius * radius;
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

