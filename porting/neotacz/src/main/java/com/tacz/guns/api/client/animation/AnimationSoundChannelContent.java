package com.tacz.guns.api.client.animation;

import net.minecraft.resources.Identifier;

import java.util.Arrays;

public class AnimationSoundChannelContent {
    public double[] keyframeTimeS;
    public Identifier[] keyframeSoundName;

    public AnimationSoundChannelContent(){
    }

    public AnimationSoundChannelContent(AnimationSoundChannelContent source) {
        if (source.keyframeTimeS != null) {
            this.keyframeTimeS = Arrays.copyOf(source.keyframeTimeS, source.keyframeTimeS.length);
        }
        if (source.keyframeSoundName != null) {
            this.keyframeSoundName = Arrays.copyOf(source.keyframeSoundName, source.keyframeSoundName.length);
        }
    }
}
