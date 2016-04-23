package com.almightyalpaca.discord.bot.plugin.sound;

import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.MoreExecutors;

import net.dv8tion.jda.player.MusicPlayer;
import net.dv8tion.jda.player.source.AudioSource;
import net.dv8tion.jda.player.source.RemoteSource;

public class SoundPlayer extends MusicPlayer {

	private final ExecutorService executor = new ThreadPoolExecutor(0, 5, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>(), (ThreadFactory) r -> {
		final Thread thread = new Thread(r, "AudioInfo-Thread");
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.setDaemon(true);
		return thread;
	});

	public SoundPlayer() {
		super();
	}

	public void add(final AudioSource source) {
		this.audioQueue.add(source);
		this.executor.submit(() -> source.getInfo());
	}

	public void add(final URL url) {
		this.add(new RemoteSource(url.toString()));
	}

	public void shuffle() {
		Collections.shuffle(this.audioQueue);
	}

	public void shutdown() {
		MoreExecutors.shutdownAndAwaitTermination(this.executor, 10, TimeUnit.SECONDS);
		this.stop();
	}
}
