package io.manebot.plugin.audio.event.channel;

import io.manebot.platform.PlatformUser;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.mixer.input.AudioProvider;

public class AudioChannelUserLeaveEvent extends AudioChannelEvent {
    private final PlatformUser platformUser;

    public AudioChannelUserLeaveEvent(Object sender, Audio audio, AudioChannel channel, PlatformUser platformUser) {
        super(sender, audio, channel);

        this.platformUser = platformUser;
    }
    
    public PlatformUser getPlatformUser() {
	return platformUser;
    }
}
