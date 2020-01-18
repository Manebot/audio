package io.manebot.plugin.audio.event.channel;

import io.manebot.platform.*;
import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.channel.*;
import io.manebot.plugin.audio.mixer.input.*;
import io.manebot.user.*;

public class AudioChannelLockedEvent extends AudioChannelEvent {
    private final UserAssociation association;
    
    public AudioChannelLockedEvent(Object sender, Audio audio, AudioChannel channel, UserAssociation association) {
	super(sender, audio, channel);
	this.association = association;
    }
    
    public UserAssociation getAssociation() {
	return association;
    }
}
