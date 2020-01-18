package io.manebot.plugin.audio.event.api;

import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.api.*;

public class AudioRegistrationRemovedEvent extends AudioRegistrationEvent {
    public AudioRegistrationRemovedEvent(Object sender, Audio audio, AudioRegistration registration) {
	super(sender, audio, registration);
    }
}
