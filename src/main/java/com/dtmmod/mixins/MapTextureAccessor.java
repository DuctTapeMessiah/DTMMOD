package com.dtmmod.mixins;

import net.minecraft.client.texture.MapTextureManager.MapTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapTexture.class)
public interface MapTextureAccessor {
    @Accessor("texture")
    NativeImageBackedTexture getTexture();

    @Invoker("updateTexture")
    void invokeUpdateTexture();
}
