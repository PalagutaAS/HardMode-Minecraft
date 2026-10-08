package net.elrid.hardmode.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.elrid.hardmode.zone.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net.minecraft.client.gui.Hud$HeartType")
public class HeartTypeMixin {

    @Inject(method = "forPlayer", at = @At("HEAD"), cancellable = true)
    private static void hardmode$greenHeartsOnLethalPoison(
            Player player,
            CallbackInfoReturnable<Hud.HeartType> cir
    ) {
        if (player.hasEffect(ModEffects.LETHAL_POISON_ONE)
                || player.hasEffect(ModEffects.LETHAL_POISON_TWO)
                || player.hasEffect(ModEffects.LETHAL_POISON_TREE)
                || player.hasEffect(ModEffects.LETHAL_POISON_FOUR)) {
            cir.setReturnValue(Hud.HeartType.POISIONED);
        }
    }
}