package net.elrid.hardmode.zone;

import net.elrid.hardmode.HardMode;
import net.elrid.hardmode.network.SafeZoneNetworking;
import net.elrid.hardmode.zone.effects.AbstractLethalPoisonEffect;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class SafeZoneManager {

    private static final int UPGRADE_THRESHOLD = 10;
    private static final int CHECK_INTERVAL    = 5;
    private static final int SAVE_INTERVAL_TICKS = 20 * 30;

    /** Радиус поиска вокруг текущего центра зоны (для сущностей, кроме игроков). */
    private static final double SEARCH_RADIUS = 1000.0;

    // ===== Кэш поиска крепости =====
    private static @Nullable BlockPos cachedStrongholdPos = null;
    private static long cachedStrongholdExpiry = 0L;
    /** Сколько тиков кэш считается валидным. 5 минут = 20 * 60 * 5. */
    private static final int STRONGHOLD_CACHE_TICKS = 20 * 60 * 5;
    /** Радиус поиска крепости в чанках. */
    private static final int STRONGHOLD_SEARCH_RADIUS_CHUNKS = 100;

    // ==================== РЕГИСТРАЦИЯ ====================

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SafeZoneManager::onServerTick);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel overworld = server.overworld();
            invalidateStrongholdCache();  // ★ сброс кэша крепости - тоже костыль, архитектурный долг
            SafeZoneState.loadFrom(SafeZoneSavedData.get(overworld), overworld);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerLevel overworld = server.overworld();
            SafeZoneSavedData data = SafeZoneSavedData.get(overworld);
            SafeZoneState.saveTo(data, overworld);
            HardMode.LOGGER.info("[SafeZone] Состояние сохранено перед выключением");
        });

        // ★ Режим Ока Эндера
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(Items.ENDER_EYE)) return InteractionResult.PASS;

            if (!SafeZoneState.canEnterEyeMode(serverLevel)) {
                String reason = SafeZoneState.getEyeModeBlockReason(serverLevel);
                if (reason != null && player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(
                            Component.literal(reason).withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.PASS;
            }

            // ★ Поиск с кэшем
            @Nullable BlockPos nearest = findStrongholdCached(serverLevel, player.blockPosition());

            if (nearest == null) {
                HardMode.LOGGER.warn("[SafeZone] Крепость не найдена для {}",
                        player.getName().getString());
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal(
                                    "Крепость не найдена в радиусе " + STRONGHOLD_SEARCH_RADIUS_CHUNKS
                                            + " чанков. Око не сработало.")
                            .withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.PASS;
            }

            BlockPos shPos = nearest.above();
            double dx = shPos.getX() + 0.5 - player.getX();
            double dz = shPos.getZ() + 0.5 - player.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.01) return InteractionResult.PASS;

            Vec3 direction = new Vec3(dx / len, 0, dz / len);
            Vec3 strongholdPos = new Vec3(shPos.getX() + 0.5, shPos.getY(), shPos.getZ() + 0.5);

            SafeZoneState.enterEyeMode(serverLevel, direction, strongholdPos);
            return InteractionResult.PASS;
        });

        HardMode.LOGGER.info("SafeZoneManager зарегистрирован");
    }

    // ==================== ГЛАВНЫЙ ЦИКЛ ====================

    private static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;

        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        SafeZoneState.tick(server, overworld);

        Vec3 center = SafeZoneState.getCenter(overworld);
        double radius = SafeZoneState.getRadius(overworld);

        if (server.getTickCount() % SAVE_INTERVAL_TICKS == 0) {
            SafeZoneState.saveTo(SafeZoneSavedData.get(overworld), overworld);
        }

        SafeZoneNetworking.broadcast(overworld,
                center.x, center.y, center.z, (float) radius);

        // Игроки — всегда.
        for (ServerPlayer player : overworld.players()) {
            applyPoisonIfOutside(player, center, radius);
            SafeZoneParticles.spawn(overworld, player, center, radius);
        }

        // Прочие сущности — только рядом с зоной.
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

    // ==================== ЭФФЕКТ ЯДА ====================

    private static Holder<MobEffect>[] tiers() {
        return new Holder[] {
                ModEffects.LETHAL_POISON_ONE,
                ModEffects.LETHAL_POISON_TWO,
                ModEffects.LETHAL_POISON_TREE,
                ModEffects.LETHAL_POISON_FOUR
        };
    }

    private static void applyPoisonIfOutside(LivingEntity entity, Vec3 center, double radius) {
        // ★ Не травим нежить, коров и пауков
        Holder<EntityType<?>> typeHolder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType());
        if (typeHolder.is(EntityTypeTags.UNDEAD)) return;
        if (typeHolder.is(EntityTypeTags.ARTHROPOD)) return;
        //if (entity.getType() == EntityType.COW)   return;
        //if (entity.getType() == EntityType.SPIDER) return;

        double dx = entity.getX() - center.x;
        double dz = entity.getZ() - center.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Целевой тир по расстоянию от края зоны
        int target = tierForDistance(dist, radius);

        // Под водой — считаем как «в зоне»
        if (entity.isUnderWater()) target = 0;

        // Мобы — не выше 1-го тира
        if (!(entity instanceof ServerPlayer) && target > 1) target = 1;

        // Текущий активный тир (0 = нет)
        Holder<MobEffect>[] tiers = tiers();
        int current = 0;
        MobEffectInstance activeInst = null;
        for (int i = tiers.length - 1; i >= 0; i--) {
            MobEffectInstance inst = entity.getEffect(tiers[i]);
            if (inst != null) {
                current = i + 1;
                activeInst = inst;
                break;
            }
        }

        if (target == 0 && current == 0) return;

        // ===== АПГРЕЙД — мгновенно, с перезаписью =====
        if (target > current) {
            if (current > 0) entity.removeEffect(tiers[current - 1]);
            applyTier(entity, tiers[target - 1], activeInst);
            return;
        }

        // ===== СОВПАЛ — обновляем при подходе к концу =====
        if (target == current && current > 0) {
            if (activeInst != null && activeInst.getDuration() <= UPGRADE_THRESHOLD) {
                applyTier(entity, tiers[current - 1], activeInst);
            }
            return;
        }

        // ===== ДАУНГРЕЙД — постепенно, тир за тиром =====
        if (current == 0 || activeInst == null) return;
        if (activeInst.getDuration() > UPGRADE_THRESHOLD) return;

        entity.removeEffect(tiers[current - 1]);
        int next = current - 1;
        if (next > 0) {
            applyTier(entity, tiers[next - 1], activeInst);
        }
    }

    /** Тир яда по расстоянию от центра: 0 — в зоне, 1..4 — вне. */
    private static int tierForDistance(double dist, double radius) {
        double outside = dist - radius;
        if (outside <= 0)                                    return 0;
        if (outside <= SafeZoneConfig.POISON_RING_TIER1)     return 1;
        if (outside <= SafeZoneConfig.POISON_RING_TIER2)     return 2;
        if (outside <= SafeZoneConfig.POISON_RING_TIER3)     return 3;
        return 4;
    }

    /** Наложить эффект нужного тира с фиксированной длительностью. */
    /** Наложить эффект нужного тира, сохранив ритм ударов. */
    private static void applyTier(LivingEntity entity,
                                  Holder<MobEffect> effect,
                                  @Nullable MobEffectInstance previous) {

        AbstractLethalPoisonEffect eff = (AbstractLethalPoisonEffect) effect.value();
        int newInterval = eff.damageInterval();
        int baseDuration = SafeZoneConfig.POISON_EFFECT_DURATION;
        int duration = baseDuration;

        if (previous != null) {
            int prevInterval = ((AbstractLethalPoisonEffect) previous.getEffect().value())
                    .damageInterval();

            // Удары старого эффекта происходили при duration % prevInterval == 0.
            // Сколько тиков осталось до следующего удара?
            int mod = previous.getDuration() % prevInterval;
            int ticksUntilNext = (mod == 0) ? prevInterval : mod;

            // Первый удар нового эффекта произойдёт через (duration % newInterval) тиков.
            // Выставляем duration так, чтобы это число совпало с ticksUntilNext.
            int desired = ticksUntilNext % newInterval;
            int current = baseDuration % newInterval;
            int shift = (desired - current + newInterval) % newInterval;

            duration = baseDuration + shift;
        }

        entity.addEffect(new MobEffectInstance(effect, duration, 0, false, true, true));
    }

    /**
     * Ищет ближайшую крепость с кэшем.
     * Первый вызов в течение STRONGHOLD_CACHE_TICKS использует закэшированное значение.
     */
    private static @Nullable BlockPos findStrongholdCached(ServerLevel level, BlockPos near) {
        long now = level.getGameTime();

        if (cachedStrongholdPos != null && now < cachedStrongholdExpiry) {
            return cachedStrongholdPos;
        }

        @Nullable BlockPos found = level.findNearestMapStructure(
                StructureTags.EYE_OF_ENDER_LOCATED,
                near,
                STRONGHOLD_SEARCH_RADIUS_CHUNKS,
                false
        );

        if (found != null) {
            cachedStrongholdPos    = found;
            cachedStrongholdExpiry = now + STRONGHOLD_CACHE_TICKS;
            HardMode.LOGGER.info("[SafeZone] Крепость закэширована: ({}, {}), до тика {}",
                    found.getX(), found.getZ(), cachedStrongholdExpiry);
        }
        return found;
    }

    /** Сбросить кэш — на случай /reload или переноса между мирами. */
    public static void invalidateStrongholdCache() {
        cachedStrongholdPos = null;
        cachedStrongholdExpiry = 0L;
    }
}