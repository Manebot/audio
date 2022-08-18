package io.manebot.plugin.audio.event.mixer;

import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.event.AudioEvent;
import io.manebot.plugin.audio.mixer.Mixer;

public abstract class MixerEvent extends AudioEvent {
    private final Mixer mixer;

    protected MixerEvent(Object sender, Audio audio, Mixer mixer) {
	super(sender, audio);
	    this.mixer = mixer;
    }
    
    public Mixer getMixer() {
	    return mixer;
    }
}
