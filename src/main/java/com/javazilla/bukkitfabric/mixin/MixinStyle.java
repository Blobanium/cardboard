package com.javazilla.bukkitfabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.text.Style;

@Mixin(Style.class)
public class MixinStyle {

    @Shadow
    public Boolean bold;

    @Shadow
    public Style withBold(Boolean obool) {
        return null;
    }

    public Style setStrikethrough(Boolean obool) {
        Style st = withBold(this.bold);
        st.strikethrough = obool;
        return st;
    }

    public Style setUnderline(Boolean obool) {
        return this.withBold(this.bold).withUnderline(obool);
    }

    public Style setRandom(Boolean obool) {
        Style st = withBold(this.bold);
        st.obfuscated = obool;
        return st;
    }

}