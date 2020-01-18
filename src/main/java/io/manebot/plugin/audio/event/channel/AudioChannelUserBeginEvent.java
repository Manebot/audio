package io.manebot.plugin.audio.event.channel;

import io.manebot.platform.*;
import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.channel.*;
import io.manebot.plugin.audio.mixer.input.*;

public class AudioChannelUserBeginEvent extends AudioChannelEvent {
    private final PlatformUser platformUser;
    private final AudioProvider provider;
    
    public AudioChannelUserBeginEvent(Object sender, Audio audio, AudioChannel channel, PlatformUser platformUser,
		    AudioProvider provider) {
	super(sender, audio, channel);
	this.platformUser = platformUser;
	this.provider = provider;
    }
    
    public PlatformUser getPlatformUser() {
	return platformUser;
    }
    
    public AudioProvider getProvider() {
	return provider;
    }
}
