package io.manebot.plugin.audio.event.channel;

import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.channel.*;
import io.manebot.user.*;

public class AudioChannelUnlockedEvent extends AudioChannelEvent {
    private final UserAssociation association;
    
    public AudioChannelUnlockedEvent(Object sender, Audio audio, AudioChannel channel, UserAssociation association) {
	super(sender, audio, channel);
	this.association = association;
    }
    
    public UserAssociation getAssociation() {
	return association;
    }
}
