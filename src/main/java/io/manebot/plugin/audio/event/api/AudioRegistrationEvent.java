package io.manebot.plugin.audio.event.api;

import io.manebot.event.*;
import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.api.*;
import io.manebot.plugin.audio.event.*;

public abstract class AudioRegistrationEvent extends AudioEvent {
    private final AudioRegistration registration;
    
    public AudioRegistrationEvent(Object sender, Audio audio, AudioRegistration registration) {
	super(sender, audio);
	this.registration = registration;
    }
    
    public AudioRegistration getRegistration() {
	return registration;
    }
}
