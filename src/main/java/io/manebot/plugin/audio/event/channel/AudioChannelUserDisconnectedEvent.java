package io.manebot.plugin.audio.event.channel;

import io.manebot.platform.PlatformUser;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;

public class AudioChannelUserDisconnectedEvent extends AudioChannelEvent {
    private final PlatformUser platformUser;

    private final boolean leftView;

    public AudioChannelUserDisconnectedEvent(Object sender,
                                             Audio audio,
                                             AudioChannel channel,
                                             PlatformUser platformUser,
                                             boolean leftView) {
        super(sender, audio, channel);

        this.platformUser = platformUser;
        this.leftView = leftView;
    }
    
    public PlatformUser getPlatformUser() {
	    return platformUser;
    }

    public boolean hasLeftView() {
        return leftView;
    }
}
