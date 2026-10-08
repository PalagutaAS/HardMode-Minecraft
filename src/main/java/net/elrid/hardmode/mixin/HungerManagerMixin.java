package net.elrid.hardmode.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class HungerManagerMixin {

    private static final float MAX_SATURATION = 6.0f;

    @Shadow
    private int foodLevel;
    @Shadow
    public abstract float getSaturationLevel();
    @Shadow
    private float saturationLevel;

    @ModifyVariable(
            method = "addExhaustion",
            at = @At("HEAD"),
            argsOnly = true
    )
    private float multiplyExhaustion(float exhaustion) {
        float multiplier;
        if (foodLevel > 18) {
            multiplier = 2.5f;
        } else if (foodLevel < 18 && foodLevel > 8) {
            multiplier = 2.0f;
        } else {
            multiplier = 1.5f;
        }
        //System.out.println("Текущий голод: " + foodLevel + ", множитель: " + multiplier + ", Истощение до изменения: " + exhaustion + " Насыщение: " + getSaturationLevel());
        return exhaustion * multiplier;
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void onAdd(int food, float saturationModifier, CallbackInfo ci) {
        if (this.saturationLevel > MAX_SATURATION) {
            //System.out.println("Ограничили насыщение: " + getSaturationLevel());
            this.saturationLevel = MAX_SATURATION;
        }
    }
}