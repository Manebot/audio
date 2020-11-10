package io.manebot.plugin.audio.event.channel;

import io.manebot.platform.PlatformUser;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;

public class AudioChannelUserMoveEvent extends AudioChannelEvent {
    private final PlatformUser platformUser;
    private final AudioChannel from;

    private final Runnable follow;
    private final boolean joined, left, away;

    private boolean followed;

    public AudioChannelUserMoveEvent(Object sender,
                                     Audio audio,
                                     AudioChannel from,
                                     AudioChannel to,
                                     PlatformUser platformUser,
                                     boolean joined,
                                     boolean left,
                                     Runnable follow,
                                     boolean away) {
        super(sender, audio, to);

        this.platformUser = platformUser;
        this.from = from;
        this.follow = follow;
        this.joined = joined;
        this.left = left;
        this.away = away;
    }

    public AudioChannel getFromChannel() {
        return from;
    }

    public PlatformUser getPlatformUser() {
	    return platformUser;
    }

    public boolean hasJoined() {
        return joined;
    }

    public boolean hasLeft() {
        return left;
    }

    public boolean wasFollowed() {
        return followed;
    }

    public boolean wasMovedAway() {
        return away;
    }

    public void follow() {
        follow.run();

        followed = true;
    }
}
