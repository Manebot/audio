package io.manebot.plugin.audio.event.channel;

import io.manebot.platform.PlatformUser;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;

public class AudioChannelUserConnectedEvent extends AudioChannelEvent {
    private final PlatformUser platformUser;
    private final boolean enteredView;

    private final Runnable follow;
    private boolean followed;

    public AudioChannelUserConnectedEvent(Object sender, Audio audio, AudioChannel channel, PlatformUser platformUser,
                                          boolean enteredView, Runnable follow) {
        super(sender, audio, channel);

        this.platformUser = platformUser;
        this.enteredView = enteredView;
        this.follow = follow;
    }
    
    public PlatformUser getPlatformUser() {
	return platformUser;
    }

    public boolean hasEnteredView() {
        return enteredView;
    }

    public boolean wasFollowed() {
        return followed;
    }

    public void follow() {
        follow.run();

        followed = true;
    }
}
