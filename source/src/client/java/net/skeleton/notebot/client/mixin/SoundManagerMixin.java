package net.skeleton.notebot.client.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.skeleton.notebot.client.NotebotClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
	@Inject(method = "play", at = @At("HEAD"))
	private void notebot$captureSound(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
		if (NotebotClient.controller() != null) {
			NotebotClient.controller().captureSound(instance);
		}
	}
}
