package io.manebot.plugin.audio.event.channel;

import io.manebot.platform.PlatformUser;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;

public class AudioChannelUserJoinEvent extends AudioChannelEvent {
    private final PlatformUser platformUser;

    public AudioChannelUserJoinEvent(Object sender, Audio audio, AudioChannel channel, PlatformUser platformUser) {
        super(sender, audio, channel);

        this.platformUser = platformUser;
    }
    
    public PlatformUser getPlatformUser() {
	return platformUser;
    }
}
