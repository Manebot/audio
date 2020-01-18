package io.manebot.plugin.audio.event.channel;

import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.channel.*;
import io.manebot.plugin.audio.event.*;

public abstract class AudioChannelEvent extends AudioEvent {
    private final AudioChannel channel;
    
    protected AudioChannelEvent(Object sender, Audio audio, AudioChannel channel) {
	super(sender, audio);
	this.channel = channel;
    }
    
    public AudioChannel getChannel() {
	return channel;
    }
}
