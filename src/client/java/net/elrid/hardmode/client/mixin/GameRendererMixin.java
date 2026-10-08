package net.elrid.hardmode.client.mixin;

import net.elrid.hardmode.zone.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(
            method = "bobHurt",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hardmode$cancelHurtShake(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (player.hasEffect(ModEffects.LETHAL_POISON_ONE) || player.hasEffect(ModEffects.LETHAL_POISON_TWO)) {
            player.hurtTime = 0;
            player.hurtDuration = 0;
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "bobHurt",
            at = @At("STORE"),
            ordinal = 0
    )
    private float hardmode$scaleHurtShake(float value) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return value;

        if (player.hasEffect(ModEffects.LETHAL_POISON_TREE)) return value * 0.725F;
        if (player.hasEffect(ModEffects.LETHAL_POISON_FOUR)) return value * 0.82F;
        return value;
    }
}