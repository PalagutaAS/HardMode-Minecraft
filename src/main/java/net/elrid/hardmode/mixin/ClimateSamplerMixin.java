package net.elrid.hardmode.mixin;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Climate.Sampler.class)
public class ClimateSamplerMixin {

    private static final double BIOME_SCALE = 10;

    @ModifyVariable(
            method = "sample(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private int scaleX(int quartX) {
        return (int) (quartX * BIOME_SCALE);
    }

    @ModifyVariable(
            method = "sample(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 3
    )
    private int scaleZ(int quartZ) {
        return (int) (quartZ * BIOME_SCALE);
    }
}