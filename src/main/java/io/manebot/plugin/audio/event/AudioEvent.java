package io.manebot.plugin.audio.event;

import io.manebot.event.*;
import io.manebot.plugin.audio.*;

public abstract class AudioEvent extends Event {
    private final Audio audio;
    
    protected AudioEvent(Object sender, Audio audio) {
        super(sender);
        
	this.audio = audio;
    }
    
    public Audio getAudio() {
	return audio;
    }
}
