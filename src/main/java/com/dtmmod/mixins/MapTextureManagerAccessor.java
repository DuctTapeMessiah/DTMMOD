package com.dtmmod.mixins;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.texture.MapTextureManager;
import net.minecraft.client.texture.MapTextureManager.MapTexture;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapTextureManager.class)
public interface MapTextureManagerAccessor {
    @Accessor("texturesByMapId")
    Int2ObjectMap<MapTexture> getTexturesByMapId();

    @Invoker("getMapTexture")
    MapTexture invokeGetMapTexture(MapIdComponent id, MapState state);
}
