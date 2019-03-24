package com.github.manevolent.jbot.plugin.audio.channel;

import com.github.manevolent.jbot.conversation.Conversation;
import com.github.manevolent.jbot.plugin.audio.mixer.Mixer;
import com.github.manevolent.jbot.plugin.audio.mixer.input.AudioProvider;
import com.github.manevolent.jbot.plugin.audio.mixer.input.MixerChannel;
import com.github.manevolent.jbot.plugin.audio.player.AudioPlayer;
import com.github.manevolent.jbot.user.User;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AudioChannel implements MixerChannel.Registrant {

    private final Mixer mixer;
    private final AudioChannelRegistrant owner;
    private final List<AudioPlayer> players = new ArrayList<>();
    private final Map<User, Boolean> userSpeakingMap = Collections.synchronizedMap(new HashMap<>());
    private final Map<AudioProvider, Boolean> providerSpeakingMap = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<Listener> listeners = new LinkedList<>();

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
     * Gets a list of active listeners in this channel.
     * @return Channel listener list.
     */
    public abstract List<User> getActiveListeners();

    public abstract Conversation getConversation();

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
     * @param player
     * @return true if the player was registered, false otherwise.
     */
    public boolean addPlayer(AudioPlayer player) {
        try {
            if (!isRegistered())
                throw new IllegalStateException("not registered");

            if (mixer == null) throw new NullPointerException("mixer");

            // Check for maximum player count if we are adding a blocking player.
            if (player.isBlocking() && getBlockingPlayers() >= getMaximumPlayers()) return false;

            // Add channel to mixer.
            boolean b = mixer.addChannel(player);
            if (!b) return false;

            fireListenerAction(x -> x.onPlayerAdded(this, player));

            if (isIdle())
                setIdle(false);

            return true;
        } catch (Exception e) {
            Logger.getGlobal().log(Level.SEVERE, "Problem adding audio player to channel", e);
            return false;
        }
    }

    public AudioChannelRegistrant getRegistrant() {
        return owner;
    }

    protected final Ownership obtain(User user) {
        Ownership o = new Ownership(user);
        lock.lock();
        return o;
    }

    /**
     * Obtains exclusive ownership over the audio channel.
     * @param user User obtaining ownership.
     * @return Ownership object.
     */
    public Ownership obtainChannel(User user) {
        Ownership ownership = null;

        try {
            ownership = obtain(user);

            fireListenerAction(x -> x.onOwnershipObtained(AudioChannel.this, user));

            return ownership;
        } catch (Throwable e) {
            if (ownership != null)
                try {
                    ownership.close();
                } catch (Exception e1) {
                    Logger.getGlobal().log(Level.SEVERE, "Problem closing ownership after exception", e1);
                }

            throw e;
        }
    }

    public boolean isSpeaking(AudioProvider user) {
        Boolean value = providerSpeakingMap.get(user);

        if (value == null)
            return false;
        else
            return value;
    }

    public void setSpeaking(AudioProvider provider, boolean speaking) {
        if (provider == null) return;

        boolean value = isSpeaking(provider);

        if (value != speaking) {
            Logger.getGlobal().fine("Provider " + provider + " speaking state changed: " + speaking);

            synchronized (providerSpeakingMap) {
                if (speaking)
                    providerSpeakingMap.put(provider, true);
                else
                    providerSpeakingMap.remove(provider);
            }

            fireListenerAction(x -> x.onSpeaking(this, provider, speaking));
        }
    }

    public boolean isSpeaking(User user) {
        Boolean value = userSpeakingMap.get(user);

        if (value == null)
            return false;
        else
            return value;
    }

    public void setSpeaking(User user, boolean speaking) {
        if (user == null) return;

        boolean value = isSpeaking(user);

        if (value != speaking) {
            Logger.getGlobal().fine("User " + user.getDisplayName() + " speaking state changed: " + speaking);

            synchronized (userSpeakingMap) {
                userSpeakingMap.put(user, speaking);
            }

            fireListenerAction(x -> x.onSpeaking(this, user, speaking));
        }
    }

    /**
     * Listens to a particular user on the audio channel.
     * If the channel does not support listening to others, UnsupportedOperationException() is thrown.
     * @param user User to listen to.
     * @return AudioProvider instance.
     */
    public AudioProvider listen(User user) {
        throw new UnsupportedOperationException();
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

    protected final void fireListenerAction(Consumer<? super Listener> action) {
        new ArrayList<>(getListeners()).forEach(action);
    }

    @Override
    public void onChannelAdded(MixerChannel channel) {
        // NOTE: my "&" character here is intended!
        if (this.players.size() <= 0 & this.players.add((AudioPlayer)channel))
            owner.onChannelActivated(this);
    }

    @Override
    public void onChannelRemoved(MixerChannel channel) {
        if (channel == null) throw new NullPointerException();

        try {
            channel.close();
        } catch (Exception e) {
            Logger.getGlobal().log(Level.SEVERE, "Problem closing mixer channel", e);
        }

        if (!players.remove((AudioPlayer)channel))
            throw new IllegalArgumentException("Failed to remove audio player");

        if (players.size() <= 0)
            owner.onChannelPassivated(this);
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

    public List<Listener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    public boolean registerListener(Listener listener) {
        if (this.listeners.add(listener)) {
            listener.onRegistered(this);

            List<User> activeListeners = getActiveListeners();
            Map<User, Boolean> speakingMap = new HashMap<>(this.userSpeakingMap);
            Map<AudioProvider, Boolean> providerSpeakingMap = new HashMap<>(this.providerSpeakingMap);

            for (User user : activeListeners) listener.onJoin(this, user);
            for (User user : speakingMap.keySet()) listener.onSpeaking(this, user, speakingMap.get(user));
            for (AudioProvider provider : providerSpeakingMap.keySet())
                listener.onSpeaking(this, provider, providerSpeakingMap.get(provider));

            return true;
        } else return false;
    }

    public boolean unregisterListener(Listener listener) {
        if (this.listeners.remove(listener)) {
            listener.onUnregistered(this);

            List<User> activeListeners = getActiveListeners();
            Map<User, Boolean> speakingMap = new HashMap<>(this.userSpeakingMap);

            for (User user : activeListeners) listener.onLeave(this, user);
            for (User user : speakingMap.keySet()) listener.onSpeaking(this, user, false);
            for (AudioProvider provider : providerSpeakingMap.keySet())
                listener.onSpeaking(this, provider, false);

            return true;
        } else return false;
    }

    public enum State {
        PLAYING,
        WAITING
    }

    public class Ownership implements AutoCloseable {
        private final User user;

        public Ownership(User user) {
            this.user = user;
        }

        public final AudioChannel getChannel() {
            return AudioChannel.this;
        }

        public User getUser() {
            return user;
        }

        @Override
        public void close() throws Exception {
            lock.unlock();

            fireListenerAction(x -> x.onOwnershipReleased(AudioChannel.this, user));
        }
    }

    public interface Listener {
        default void onRegistered(AudioChannel channel) {}
        default void onUnregistered(AudioChannel channel) {}

        default void onSpeaking(AudioChannel channel, User user, boolean speaking) {}
        default void onSpeaking(AudioChannel channel, AudioProvider provider, boolean speaking) {}

        default void onJoin(AudioChannel channel, User user) {}
        default void onLeave(AudioChannel channel, User user) {}

        default void onPlayerAdded(AudioChannel channel, AudioPlayer player) {}

        default void onOwnershipObtained(AudioChannel channel, User user) {}
        default void onOwnershipReleased(AudioChannel channel, User user) {}
    }
}