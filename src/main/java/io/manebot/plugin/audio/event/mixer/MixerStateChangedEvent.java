package io.manebot.plugin.audio.event.mixer;

import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.mixer.Mixer;

public class MixerStateChangedEvent extends MixerEvent {
    public MixerStateChangedEvent(Object sender, Audio audio, Mixer mixer) {
        super(sender, audio, mixer);
    }
}
