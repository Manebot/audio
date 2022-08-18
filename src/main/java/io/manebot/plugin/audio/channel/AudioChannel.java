package io.manebot.plugin.audio.channel;

import io.manebot.conversation.Conversation;
import io.manebot.event.Event;
import io.manebot.event.EventDispatcher;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.event.channel.*;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.user.UserAssociation;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class AudioChannel {
    private static int defaultMaximumQueueSize = 3;

    private final Mixer mixer;
    private final AudioChannelRegistrant owner;
    private final List<AudioPlayer> players = new ArrayList<>();
    private final Map<PlatformUser, AudioProvider> providerMap = Collections.synchronizedMap(new LinkedHashMap<>());
    private final ReentrantLock lock = new ReentrantLock();

    private boolean idle = false;
    private volatile boolean registered = false;

    public AudioChannel(Mixer mixer, AudioChannelRegistrant owner) {
        this.mixer = mixer;
        this.owner = owner;
    }

    /**
     * Gets the ID of this audio channel.
     * @return Audio channel ID.
     */
    public String getId() {
        return "generic:unassigned";
    }

    /**
     * Gets the platform associated with this audio channel.
     * @return platform instance.
     */
    public abstract Platform getPlatform();

    /**
     * Gets the mixer associated with this audio channel.
     * @return Mixer.
     */
    public final Mixer getMixer() {
        return mixer;
    }

    /**
     * Gets the total maximum player count.
     * @return Maximum player count.
     */
    public int getMaximumPlayers() {
        return 1;
    }

    /**
     * Gets the total count of blocking players
     * @return Blocking player count.
     */
    public int getBlockingPlayers() {
        return (int) getPlayers().stream().filter(AudioPlayer::isBlocking).count();
    }

    /**
     * Gets a list of active members in this channel.
     * @return Channel listener list.
     */
    public abstract List<PlatformUser> getMembers();

    /**
     * Gets a list of active listeners in this channel.
     * @return Channel listener list.
     */
    public abstract List<PlatformUser> getListeners();

    /**
     * Gets the maximum count of tracks that may be queued.
     * @return maximum count of tracks that may be queued.
     */
    public int getMaximumQueueSize() {
        return defaultMaximumQueueSize;
    }

    /**
     * Gets a list of active listeners in this channel.
     * @return Channel listener list.
     */
    public List<UserAssociation> getRegisteredListeners() {
        return getListeners().stream()
                .map(user -> getPlatform().getUserAssocation(user))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Gets the conversation associated with this channel.
     * @return conversation instance.
     */
    public abstract Conversation getConversation();

    public final boolean isObtainedByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    public final boolean isObtained() {
        if (lock.tryLock()) {
            try {
                return false;
            } finally {
                lock.unlock();
            }
        } else return true;
    }

    /**
     * Gets the audio channel's state.
     * @return State.
     */
    public State getState() {
        if (getPlayers().stream().anyMatch(AudioPlayer::isPlaying))
            return State.PLAYING;
        else
            return State.WAITING;
    }

    /**
     * Gets a list of audio players on this channel.
     * @return Audio player list.
     */
    public List<AudioPlayer> getPlayers() {
        return Collections.unmodifiableList(new ArrayList<>(players));
    }

    /**
     * Adds a player to this channel.
     * @param player player to add to the channel.
     * @throws IllegalStateException if the channel cannot accept any more blocking players.
     */
    public void addPlayer(AudioPlayer player) throws IllegalStateException {
        Objects.requireNonNull(player, "player");

        if (!isRegistered())
            throw new IllegalStateException("not registered");

        Mixer mixer = Objects.requireNonNull(getMixer(), "mixer");

        // Check for maximum player count if we are adding a blocking player.
        int blockingPlayers = getBlockingPlayers();
        if (player.isBlocking() && blockingPlayers >= getMaximumPlayers()) {
            throw new IllegalStateException(blockingPlayers == 1 ?
                    "A track is already playing on this channel." :
                    blockingPlayers + " tracks are already playing on this channel."
            );
        }

        // Add channel to mixer.
        CompletableFuture<MixerChannel> future = Objects.requireNonNull(mixer.addChannel(player), "future");

        onChannelAdded(player);

        Audio audio = mixer.getAudio();
        EventDispatcher dispatcher = audio.getPlugin().getBot().getEventDispatcher();
        dispatcher.execute(new AudioChannelPlayerAddedEvent(this, audio, this, player));

        if (isIdle())
            setIdle(false);

        future.thenAcceptAsync(this::onChannelRemoved);
    }

    public AudioChannelRegistrant getRegistrant() {
        return owner;
    }

    protected final Ownership obtain(UserAssociation association) {
        if (lock.isHeldByCurrentThread()) {
            return new PassiveOwnership(association);
        } else {
            lock.lock();
            return new ActiveOwnership(association);
        }
    }

    /**
     * Obtains exclusive ownership over the audio channel.
     * @param association command sender obtaining ownership.
     * @return Ownership object.
     */
    public Ownership obtainChannel(UserAssociation association) {
        Ownership ownership = null;

        try {
            ownership = obtain(association);

            if (ownership.holdsLock()) {
                Mixer mixer = getMixer();
                Audio audio = mixer.getAudio();

                Event event = new AudioChannelLockedEvent(this, audio, this, association);
                audio.getPlugin().getBot().getEventDispatcher().execute(event);
            }
            
            return ownership;
        } catch (Throwable e) {
            if (ownership != null)
                try {
                    ownership.close();
                } catch (Exception e1) {
                    e.addSuppressed(e1);
                }

            throw e;
        }
    }

    public boolean isProviding(PlatformUser user) {
        return providerMap.containsKey(user);
    }

    public boolean setProvider(PlatformUser user, AudioProvider provider) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(provider, "provider");

        boolean added;
        synchronized (providerMap) {
            AudioProvider currentProvider = providerMap.get(user);

            if (currentProvider != null) {
                removeProvider(user);
            }

            if (provider != null) {
                providerMap.put(user, provider);
            }
        }

        if (provider != null) {
            Audio audio = getMixer().getAudio();
            Event event = new AudioChannelUserBeginEvent(this, audio, this, user, provider);
            audio.getPlugin().getBot().getEventDispatcher().execute(event);
        }

        return provider != null;
    }

    public boolean removeProvider(PlatformUser user) {
        Objects.requireNonNull(user);
    
        AudioProvider provider;
        synchronized (providerMap) {
            provider = providerMap.remove(user);
        }
        boolean removed = provider != null;
        if (removed) {
            Audio audio = getMixer().getAudio();
            Event event = new AudioChannelUserEndEvent(this, audio, this, user, provider);
            audio.getPlugin().getBot().getEventDispatcher().execute(event);
        }
        return removed;
    }

    /**
     * Listens to a particular user on the audio channel.
     * If the channel does not support listening to others, UnsupportedOperationException() is thrown.
     * @param user User to listen to.
     * @return AudioProvider instance.
     */
    public AudioProvider getProvider(PlatformUser user) {
        return providerMap.get(user);
    }

    public void setIdle(boolean idle) {
        if (this.idle != idle) {
            this.idle = idle;

            if (idle)
                getRegistrant().onChannelSleep(this);
            else
                getRegistrant().onChannelWake(this);
        }
    }

    public boolean isIdle() {
        return idle;
    }

    private void onChannelAdded(MixerChannel channel) {
        Objects.requireNonNull(channel, "channel");
        
        // NOTE: my "&" character here is intended!
        if (this.players.size() <= 0 & this.players.add((AudioPlayer)channel))
            owner.onChannelActivated(this);
    }

    private void onChannelRemoved(MixerChannel channel) {
        Objects.requireNonNull(channel, "channel");

        try {
            channel.close();
        } catch (Exception e) {
            Logger.getGlobal().log(Level.SEVERE, "Problem closing mixer channel", e);
        }

        if (!players.remove((AudioPlayer) channel))
            throw new IllegalArgumentException("Failed to remove audio player");

        if (players.size() <= 0) {
            owner.onChannelPassivated(this);
        }
    }

    public final boolean stopAll() {
        Iterator<AudioPlayer> playerIterator = players.iterator();
        boolean b = true;

        while (playerIterator.hasNext()) {
            AudioPlayer player = playerIterator.next();
            try {
                player.stop();
            } catch (Exception ex) {
                Logger.getGlobal().log(Level.WARNING, "Problem stopping audio player", ex);
                player.kill();
                b = false;
            }
        }

        return b;
    }

    public final void onUnregistered() {
        registered = false;
    }

    public final void onRegistered() {
        registered = true;
    }

    public boolean isRegistered() {
        return registered;
    }

    public enum State {
        PLAYING,
        WAITING
    }
    
    public interface Ownership extends AutoCloseable {
        AudioChannel getChannel();
        UserAssociation getAssociation();
        boolean holdsLock();
    }

    public class PassiveOwnership implements Ownership {
        private final UserAssociation association;

        public PassiveOwnership(UserAssociation association) {
            this.association = association;
        }

        @Override
        public final AudioChannel getChannel() {
            return AudioChannel.this;
        }

        @Override
        public UserAssociation getAssociation() {
            return association;
        }
    
        @Override
        public boolean holdsLock() {
            return false;
        }
    
        @Override
        public void close() throws Exception {
            // Do nothing
        }
    }
    
    public class ActiveOwnership implements Ownership {
        private final UserAssociation association;
        
        public ActiveOwnership(UserAssociation association) {
            this.association = association;
        }
        
        @Override
        public final AudioChannel getChannel() {
            return AudioChannel.this;
        }
        
        @Override
        public UserAssociation getAssociation() {
            return association;
        }
    
        @Override
        public boolean holdsLock() {
            return true;
        }
    
        @Override
        public void close() throws Exception {
            lock.unlock();
    
            Mixer mixer = getMixer();
            Audio audio = mixer.getAudio();
            audio.getPlugin().getBot().getEventDispatcher().execute(new AudioChannelUnlockedEvent(this, audio, AudioChannel.this, getAssociation()));
        }
    }
}