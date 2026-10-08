package net.elrid.hardmode.zone;

import net.elrid.hardmode.network.SafeZoneNetworking;
import net.elrid.hardmode.zone.effects.AbstractLethalPoisonEffect;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.elrid.hardmode.HardMode;

import java.util.List;

public class SafeZoneManager {

    private static final int UPGRADE_THRESHOLD = 10;
    private static final int CHECK_INTERVAL = 5;
    private static final int SAVE_INTERVAL_TICKS = 20 * 30; // раз в 30 секунд

    /** Радиус поиска вокруг текущего центра зоны (для всех сущностей, кроме игроков). */
    private static final double SEARCH_RADIUS = 1000.0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SafeZoneManager::onServerTick);

        // ★ Загрузка при старте
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SafeZoneState.loadFrom(SafeZoneSavedData.get(server.overworld()), server.overworld());
        });

        // ★ Сохранение при остановке
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SafeZoneSavedData data = SafeZoneSavedData.get(server.overworld());
            SafeZoneState.saveTo(data, server.overworld());
            HardMode.LOGGER.info("[SafeZone] Состояние сохранено перед выключением");
        });

        HardMode.LOGGER.info("SafeZoneManager зарегистрирован");
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }

        ServerLevel overworld = server.overworld();

        // Продвигаем состояние зоны (раз в 5 тиков — достаточно для интерполяции).
        SafeZoneState.tick(server, overworld);

        Vec3 center = SafeZoneState.getCenter(overworld);
        double radius = SafeZoneState.getRadius(overworld);

        // ★ Периодическое сохранение
        if (server.getTickCount() % SAVE_INTERVAL_TICKS == 0) {
            SafeZoneState.saveTo(SafeZoneSavedData.get(overworld), overworld);
        }

        // ★ Отправляем клиентам состояние зоны.
        SafeZoneNetworking.broadcast(overworld,
                center.x, center.y, center.z, (float) radius);

        // Игроков проверяем всегда — они могли уйти далеко от зоны.
        for (ServerPlayer player : overworld.players()) {
            applyPoisonIfOutside(player, center, radius);
            SafeZoneParticles.spawn(overworld, player, center, radius);
        }

        // Остальные сущности — только рядом с зоной (иначе это дорого).
        AABB searchBox = new AABB(
                center.x - SEARCH_RADIUS, overworld.getMinY(), center.z - SEARCH_RADIUS,
                center.x + SEARCH_RADIUS, overworld.getMaxY(), center.z + SEARCH_RADIUS
        );
        List<LivingEntity> entities = overworld.getEntitiesOfClass(LivingEntity.class, searchBox);
        for (LivingEntity living : entities) {
            if (living instanceof ServerPlayer) continue;
            applyPoisonIfOutside(living, center, radius);
        }
    }

    private static Holder<MobEffect>[] tiers() {
        return new Holder[] {
                ModEffects.LETHAL_POISON_ONE,
                ModEffects.LETHAL_POISON_TWO,
                ModEffects.LETHAL_POISON_TREE,
                ModEffects.LETHAL_POISON_FOUR
        };
    }

    private static void applyPoisonIfOutside(LivingEntity entity, Vec3 center, double radius) {
        // ★ Нежить не травим
        Holder<EntityType<?>> typeHolder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType());
        if (typeHolder.is(EntityTypeTags.UNDEAD)) {
            return;
        }

        boolean inWater = entity.isUnderWater();


        double dx = entity.getX() - center.x;
        double dz = entity.getZ() - center.z;
        double distanceSq = dx * dx + dz * dz;
        boolean physicallyInZone = distanceSq <= radius * radius;

        boolean treatAsInZone = physicallyInZone || inWater;
        Holder<MobEffect>[] tiers = tiers();


        int activeIndex = -1;
        MobEffectInstance activeInst = null;
        for (int i = 0; i < tiers.length; i++) {
            MobEffectInstance inst = entity.getEffect(tiers[i]);
            if (inst != null) {
                activeIndex = i;
                activeInst = inst;
                break;
            }
        }

        // Активного эффекта нет
        if (activeIndex == -1) {
            if (!treatAsInZone) {
                applyTier(entity, tiers[0], null, true);
            }
            return;
        }

        // Эффект ещё долго — не трогаем.
        if (activeInst.getDuration() > UPGRADE_THRESHOLD) {
            return;
        }

        // Эффект почти истёк — понижаем или повышаем тир.
        if (treatAsInZone) {
            // Понижение тира (в зоне ИЛИ в воде)
            entity.removeEffect(tiers[activeIndex]);
            if (activeIndex > 0) {
                applyTier(entity, tiers[activeIndex - 1], activeInst, false);
            }
        } else {
            // Повышение тира
            entity.removeEffect(tiers[activeIndex]);
            int nextIndex = (activeIndex + 1 < tiers.length) ? activeIndex + 1 : activeIndex;
            applyTier(entity, tiers[nextIndex], activeInst, true);
        }
    }

    private static void applyTier(LivingEntity entity,
                                  Holder<MobEffect> effect,
                                  MobEffectInstance previous,
                                  boolean isGrow) {

        AbstractLethalPoisonEffect eff = (AbstractLethalPoisonEffect) effect.value();
        int newInterval = eff.damageInterval();
        int duration = isGrow ? eff.totalDuration() : eff.totalDuration() / 3;

        if (previous != null) {
            AbstractLethalPoisonEffect prevEff =
                    (AbstractLethalPoisonEffect) previous.getEffect().value();
            int prevInterval = prevEff.damageInterval();
            int remaining = previous.getDuration();

            int elapsed = (prevInterval - (remaining % prevInterval)) % prevInterval;
            int desiredOffset = (newInterval - elapsed) % newInterval;
            if (desiredOffset < 0) desiredOffset += newInterval;

            int naturalOffset = duration % newInterval;
            int shift = (desiredOffset - naturalOffset + newInterval) % newInterval;
            duration += shift;
        }

        entity.addEffect(new MobEffectInstance(effect, duration, 0, false, true, true));
    }
}