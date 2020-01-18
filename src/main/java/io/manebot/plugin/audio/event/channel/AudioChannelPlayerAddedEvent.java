package io.manebot.plugin.audio.event.channel;

import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.channel.*;
import io.manebot.plugin.audio.player.*;

public class AudioChannelPlayerAddedEvent extends AudioChannelEvent {
    private final AudioPlayer player;
    
    public AudioChannelPlayerAddedEvent(Object sender, Audio audio, AudioChannel channel, AudioPlayer player) {
	super(sender, audio, channel);
	this.player = player;
    }
    
    public AudioPlayer getPlayer() {
	return player;
    }
}
