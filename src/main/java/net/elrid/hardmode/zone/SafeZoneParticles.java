package net.elrid.hardmode.zone;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Спавнит частицы вокруг игроков, находящихся вне зоны.
 * Всё локально: только для конкретного игрока и только в радиусе ~10 блоков.
 */
public final class SafeZoneParticles {

    // ===== Зелёные «висящие» частицы (DUST) =====
    private static final int     DUST_COLOR    = 0x8064FF50;
    private static final int     DUST_COLOR_SMALL    = 0x80006600;
    private static final float DUST_SIZE_SMALL = 0.4F;
    private static final float DUST_SIZE_MEDIUM = 0.8F;
    private static final int     DUST_PER_TICK = 10;

    // ===== Падающие снежинки (SNOWFLAKE) =====
    private static final int     SNOW_PER_TICK = 0;

    // ===== Геометрия =====
    /** Горизонтальное расстояние от игрока: минимум (не у лица) и максимум. */
    private static final double  HORIZ_MIN_DIST        = 1;
    private static final double  HORIZ_MAX_DIST        = 10.0;
    /** На сколько блоков вверх/вниз от игрока разбрасывать «висящие» частицы. */
    private static final double  VERTICAL_DISTRIBUTION = 8.0;
    /** Радиус сферы вокруг глаз — здесь частицы не спавнятся. */
    private static final double  FACE_EXCLUSION_RADIUS = 1F;

    private static final DustParticleOptions GREEN_DUST =
            new DustParticleOptions(DUST_COLOR, DUST_SIZE_MEDIUM);

    private static final DustParticleOptions GREEN_DUST_SMALL =
            new DustParticleOptions(DUST_COLOR_SMALL, DUST_SIZE_SMALL);

    private static final BlockParticleOption GREEN_FALLING = new BlockParticleOption(
            ParticleTypes.FALLING_DUST,
            Blocks.MOSS_BLOCK.defaultBlockState()
    );

    private SafeZoneParticles() {}

    /** Главная точка входа. Вызывать раз в CHECK_INTERVAL тиков для каждого игрока. */
    public static void spawn(ServerLevel level, ServerPlayer player,
                             Vec3 zoneCenter, double zoneRadius) {
        double dx = player.getX() - zoneCenter.x;
        double dz = player.getZ() - zoneCenter.z;

        if (dx * dx + dz * dz <= zoneRadius * zoneRadius)
            return;

        spawnAmbientDust(level, player, GREEN_DUST);
        spawnAmbientDust(level, player, GREEN_DUST_SMALL);
        spawnAmbientDust(level, player, GREEN_FALLING);
    }

    // ==================== Висящие зелёные частицы ====================

    private static void spawnAmbientDust(ServerLevel level, ServerPlayer player, ParticleOptions particleOptions) {
        double eyeY = player.getY() + player.getEyeHeight();

        for (int i = 0; i < DUST_PER_TICK; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double horizDist = HORIZ_MIN_DIST
                    + level.getRandom().nextDouble() * (HORIZ_MAX_DIST - HORIZ_MIN_DIST);

            double x = player.getX() + Math.cos(angle) * horizDist;
            double z = player.getZ() + Math.sin(angle) * horizDist;

            double y = player.getY()
                    + (level.getRandom().nextDouble() * 2.0 - 1.0) * VERTICAL_DISTRIBUTION;

            if (isInsideFaceSphere(x, y, z, player, eyeY)) continue;

            level.sendParticles(
                    player, particleOptions, true, true,
                    x, y, z,
                    1, 0, 0, 0, 0.0
            );
        }
    }

    // ==================== Хелпер ====================

    /** Точка внутри сферы вокруг глаз — там частицы не появляются. */
    private static boolean isInsideFaceSphere(double x, double y, double z,
                                              ServerPlayer player, double eyeY) {
        double fx = x - player.getX();
        double fy = y - eyeY;
        double fz = z - player.getZ();
        return fx * fx + fy * fy + fz * fz < FACE_EXCLUSION_RADIUS * FACE_EXCLUSION_RADIUS;
    }
}