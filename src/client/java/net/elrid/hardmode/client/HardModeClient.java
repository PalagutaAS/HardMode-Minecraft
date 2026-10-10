package net.elrid.hardmode.client;

import net.elrid.hardmode.HardMode;
import net.elrid.hardmode.client.command.FogTestCommand;
import net.elrid.hardmode.zone.ModEffects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class HardModeClient implements ClientModInitializer {

	private static final Identifier OVERLAY_ID =
			Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "lethal_poison_overlay");

	private static final Identifier OVERLAY_TEXTURE =
			Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "textures/misc/lethal_poison_overlay.png");

	// Fade in/out за 20 тиков (~1 сек).
	private static final int FADE_TICKS = 20;

	@Override
	public void onInitializeClient() {
		ClientZoneState.register();
		SafeZoneWallRenderer.register();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
			FogTestCommand.register(dispatcher);
		});
		//вокруг экрана штука
//		HudElementRegistry.attachElementAfter(
//				VanillaHudElements.MISC_OVERLAYS,
//				OVERLAY_ID,
//				HardModeClient::renderOverlay
//		);
	}

	private static void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}

		MobEffectInstance inst = findPoison(mc.player);
		if (inst == null) {
			return;
		}

		// Плавное появление в начале и затухание в конце.
		int dur = inst.getDuration();
		int total = maxTotalDuration(inst);
		float alpha = 1.0F;
		if (dur < FADE_TICKS) {
			alpha = dur / (float) FADE_TICKS;
		} else if (total - dur < FADE_TICKS) {
			alpha = (total - dur) / (float) FADE_TICKS;
		}
		if (alpha <= 0.01F) return;

		int w = graphics.guiWidth();
		int h = graphics.guiHeight();

		// Tint с альфой: ARGB.
		int tint = ((int) (alpha * 255) << 24) | 0xFFFFFF;

		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				OVERLAY_TEXTURE,
				0, 0,          // x, y
				0.0F, 0.0F,    // u, v
				w, h,          // width, height
				w, h,          // textureWidth, textureHeight
				tint
		);
	}

	private static MobEffectInstance findPoison(LivingEntity e) {
		Holder<MobEffect>[] tiers = new Holder[] {
				ModEffects.LETHAL_POISON_ONE,
				ModEffects.LETHAL_POISON_TWO,
				ModEffects.LETHAL_POISON_TREE,
				ModEffects.LETHAL_POISON_FOUR
		};
		for (Holder<MobEffect> h : tiers) {
			MobEffectInstance inst = e.getEffect(h);
			if (inst != null) return inst;
		}
		return null;
	}

	/** Длительность «натуральная» для этого тира — нужна только для fade-out. */
	private static int maxTotalDuration(MobEffectInstance inst) {
		try {
			return ((net.elrid.hardmode.zone.effects.AbstractLethalPoisonEffect)
					inst.getEffect().value()).totalDuration();
		} catch (ClassCastException ex) {
			return inst.getDuration();
		}
	}
}