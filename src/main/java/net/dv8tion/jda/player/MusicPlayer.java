/**
 * Copyright 2016 Austin Keener Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package net.dv8tion.jda.player;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import net.dv8tion.jda.audio.AudioConnection;
import net.dv8tion.jda.audio.player.Player;
import net.dv8tion.jda.player.source.AudioSource;
import net.dv8tion.jda.player.source.AudioStream;
import net.dv8tion.jda.player.source.AudioTimestamp;
import net.dv8tion.jda.utils.SimpleLog;

/**
 * Created by Austin on 3/8/2016.
 */
public class MusicPlayer extends Player {
	protected enum State {
		PLAYING, PAUSED, STOPPED;
	}

	protected List<AudioSource>	audioQueue			= Collections.synchronizedList(new LinkedList<>());
	protected AudioSource		previousAudioSource	= null;
	protected AudioSource		currentAudioSource	= null;
	protected AudioStream		currentAudioStream	= null;
	protected State				state				= State.STOPPED;
	protected boolean			autoContinue		= true;
	protected boolean			shuffle				= false;

	protected boolean			repeat				= false;

	public List<AudioSource> getAudioQueue() {
		return this.audioQueue;
	}

	public AudioSource getCurrentAudioSource() {
		return this.currentAudioSource;
	}

	public AudioTimestamp getCurrentTimestamp() {
		if (this.currentAudioStream != null) {
			return this.currentAudioStream.getCurrentTimestamp();
		} else {
			return null;
		}
	}

	public AudioSource getPreviousAudioSource() {
		return this.previousAudioSource;
	}

	@Override
	public boolean isPaused() {
		return this.state == State.PAUSED;
	}

	@Override
	public boolean isPlaying() {
		return this.state == State.PLAYING;
	}

	public boolean isRepeat() {
		return this.repeat;
	}

	public boolean isShuffle() {
		return this.shuffle;
	}

	@Override
	public boolean isStarted() {
		throw new UnsupportedOperationException("MusicPlayer doesn't support this");
	}

	@Override
	public boolean isStopped() {
		return this.state == State.STOPPED;
	}

	// ============ JDA Player interface overrides =============

	protected void loadFromSource(final AudioSource source) {
		try {
			final AudioStream stream = source.asStream();
			final AudioInputStream aStream = AudioSystem.getAudioInputStream(stream);
			this.setAudioSource(aStream);
			this.currentAudioSource = source;
			this.currentAudioStream = stream;    // We save the stream to be able to call getCurrentTimestamp()
		} catch (IOException | UnsupportedAudioFileException e) {
			throw new IllegalArgumentException("MusicPlayer: The AudioSource failed to load!\n" + "-> AudioSource url: " + source.getSource() + "\n" + "-> Error: " + e.getMessage(), e);
		}
	}

	@Override
	public void pause() {
		if (this.state == State.PAUSED) {
			return;
		}

		if (this.state == State.STOPPED) {
			throw new IllegalStateException("Cannot pause a stopped player!");
		}

		this.state = State.PAUSED;
		// TODO: fire onPause
	}

	@Override
	public void play() {
		this.play0(true);
	}

	protected void play0(final boolean fireEvent) {
		if (this.state == State.PLAYING) {
			return;
		}

		if (this.currentAudioSource != null) {
			this.state = State.PLAYING;
			return;
		}

		if (this.audioQueue.isEmpty()) {
			throw new IllegalStateException("MusicPlayer: The audio queue is empty! Cannot start playing.");
		}

		this.loadFromSource(this.audioQueue.remove(0));

		this.state = State.PLAYING;
		// TODO: fire onPlaying
	}

	protected void playNext(final boolean fireEvent) {
		if (this.audioQueue.isEmpty()) {
			this.stop0(false);   // Maybe true?
			// TODO: fire onFinish
			return;
		}

		this.stop0(false);
		AudioSource source;
		if (this.shuffle) {
			final Random rand = new Random();
			source = this.audioQueue.remove(rand.nextInt(this.audioQueue.size()));
		} else {
			source = this.audioQueue.remove(0);
		}
		this.loadFromSource(source);

		this.play0(false);
		// TODO: fire onNext
	}

	@Override
	public byte[] provide20MsAudio() {
		if (this.audioSource == null || this.audioFormat == null) {
			throw new IllegalStateException("The Audio source was never set for this player!\n" + "Please provide an AudioInputStream using setAudioSource.");
		}
		try {
			int amountRead;
			final byte[] audio = new byte[AudioConnection.OPUS_FRAME_SIZE * this.audioFormat.getFrameSize()];
			amountRead = this.audioSource.read(audio, 0, audio.length);
			if (amountRead > -1) {
				return audio;
			} else {
				if (this.autoContinue) {
					if (this.repeat) {
						this.reload0(true, false);
						// TODO: fire onRepeat
					} else {
						this.playNext(true);
					}
				} else {
					this.stop0(true);
				}
				return null;
			}
		} catch (final IOException e) {
			SimpleLog.getLog("JDAPlayer").log(e);
		}
		return null;
	}

	public void reload(final boolean autoPlay) {
		this.reload0(autoPlay, true);
	}

	protected void reload0(final boolean autoPlay, final boolean fireEvent) {
		if (this.previousAudioSource == null && this.currentAudioSource == null) {
			throw new IllegalStateException("Cannot restart or reload a player that has never been started!");
		}

		this.stop0(false);
		this.loadFromSource(this.previousAudioSource);

		if (autoPlay) {
			this.play0(false);
		}

		// TODO: fire onReload
	}

	@Override
	public void restart() {
		this.reload0(true, true);
	}

	// ========= Internal Functions ==========

	public void setRepeat(final boolean repeat) {
		this.repeat = repeat;
	}

	public void setShuffle(final boolean shuffle) {
		this.shuffle = shuffle;
	}

	public void skipToNext() {
		this.playNext(false);
		// TODO: fire onSkip
	}

	@Override
	public void stop() {
		this.stop0(true);
	}

	protected void stop0(final boolean fireEvent) {
		if (this.state == State.STOPPED) {
			return;
		}

		this.state = State.STOPPED;
		try {
			this.amplitudeAudioStream.close();
			this.audioSource.close();
			// We don't close currentAudioStream because it is handled by audioSource.close()
		} catch (final IOException e) {
			e.printStackTrace();
		} finally {
			this.amplitudeAudioStream = null;
			this.audioSource = null;
			this.previousAudioSource = this.currentAudioSource;
			this.currentAudioSource = null;
			this.currentAudioStream = null;
		}
		// TODO: fire onStop
	}
}
