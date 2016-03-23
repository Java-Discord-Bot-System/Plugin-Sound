/**
 * Copyright 2016 Austin Keener Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package net.dv8tion.jda.player.source;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONObject;

import com.almightyalpaca.discord.bot.plugin.sound.NativUtil;

import sun.misc.IOUtils;

public class RemoteSource implements AudioSource {
	public static final List<String>	YOUTUBE_DL_LAUNCH_ARGS	= Collections.unmodifiableList(Arrays.asList("python", "\"" + NativUtil.getYoutubeDLFile() + "\"", "-q", "-f", "bestaudio/best",
			"--no-playlist", "-o", "-"));
	public static final List<String>	FFMPEG_LAUNCH_ARGS		= Collections.unmodifiableList(Arrays.asList("\"" + NativUtil.getFFMPEGFile() + "\"", "-i", "-", "-f", "mp3", "-map", "a", "-"));

	private final String				url;
	private final List<String>			ytdlLaunchArgsF;
	private final List<String>			ffmpegLaunchArgsF;
	private AudioInfo					audioInfo;

	private final Object				infoLock				= new Object();

	public RemoteSource(final String url) {
		this(url, null, null);
	}

	public RemoteSource(final String url, final List<String> ytdlLaunchArgs, final List<String> ffmpegLaunchArgs) {
		if (url == null || url.isEmpty()) {
			throw new NullPointerException("String url provided to RemoteSource was null or empty.");
		}
		this.url = url;
		this.ytdlLaunchArgsF = ytdlLaunchArgs;
		this.ffmpegLaunchArgsF = ffmpegLaunchArgs;
	}

	@Override
	public File asFile(final String path, final boolean deleteIfExists) throws FileAlreadyExistsException, FileNotFoundException {
		if (path == null || path.isEmpty()) {
			throw new NullPointerException("Provided path was null or empty!");
		}

		final File file = new File(path);
		if (file.isDirectory()) {
			throw new IllegalArgumentException("The provided path is a directory, not a file!");
		}
		if (file.exists()) {
			if (!deleteIfExists) {
				throw new FileAlreadyExistsException("The provided path already has an existing file " + " and the `deleteIfExists` boolean was set to false.");
			} else {
				if (!file.delete()) {
					throw new UnsupportedOperationException("Cannot delete the file. Is it in use?");
				}
			}
		}

		final Thread currentThread = Thread.currentThread();
		final FileOutputStream fos = new FileOutputStream(file);
		final InputStream input = this.asStream();

		// Writes the bytes of the downloaded audio into the file.
		// Has detection to detect if the current thread has been interrupted to respect calls to
		// Thread#interrupt() when an instance of RemoteSource is in an async thread.
		// TODO: consider replacing with a Future.
		try {
			final byte[] buffer = new byte[1024];
			int amountRead = -1;
			while (!currentThread.isInterrupted() && (amountRead = input.read(buffer)) > -1) {
				fos.write(buffer, 0, amountRead);
			}
			fos.flush();
		} catch (final IOException e) {
			e.printStackTrace();
		} finally {
			try {
				input.close();
			} catch (final IOException e) {
				e.printStackTrace();
			}

			try {
				fos.close();
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
		return file;
	}

	@Override
	public AudioStream asStream() {
		final List<String> ytdlLaunchArgs = new ArrayList<>();
		final List<String> ffmpegLaunchArgs = new ArrayList<>();
		if (this.ytdlLaunchArgsF == null) {
			ytdlLaunchArgs.addAll(RemoteSource.YOUTUBE_DL_LAUNCH_ARGS);
		} else {
			ytdlLaunchArgs.addAll(this.ytdlLaunchArgsF);
			if (!ytdlLaunchArgs.contains("-q")) {
				ytdlLaunchArgs.add("-q");
			}
		}

		if (this.ffmpegLaunchArgsF == null) {
			ffmpegLaunchArgs.addAll(RemoteSource.FFMPEG_LAUNCH_ARGS);
		} else {
			ffmpegLaunchArgs.addAll(this.ytdlLaunchArgsF);
		}

		ytdlLaunchArgs.add(this.url);    // specifies the URL to download.

		return new AudioStream(this.url, ytdlLaunchArgs, ffmpegLaunchArgs);
	}

	@Override
	public AudioInfo getInfo() {
		synchronized (this.infoLock) {
			if (this.audioInfo != null) {
				return this.audioInfo;
			}

			final List<String> infoArgs = new LinkedList<>();
			if (this.ytdlLaunchArgsF != null) {
				infoArgs.addAll(this.ytdlLaunchArgsF);
				if (!infoArgs.contains("-q")) {
					infoArgs.add("-q");
				}
			} else {
				infoArgs.addAll(RemoteSource.YOUTUBE_DL_LAUNCH_ARGS);
			}

			infoArgs.add("--ignore-errors");    // Ignore errors, obviously
			infoArgs.add("-j");                 // Dumps the json about the file into STDout
			infoArgs.add("--skip-download");    // Doesn't actually download the file.
			infoArgs.add(this.url);                  // specifies the URL to download.

			try {
				final Process infoProcess = new ProcessBuilder().command(infoArgs).start();
				final byte[] infoData = IOUtils.readFully(infoProcess.getErrorStream(), -1, false);   // YT-DL outputs to STDerr
				if (infoData == null || infoData.length == 0) {
					throw new NullPointerException("The Youtube-DL process resulted in a null or zero-length INFO!");
				}

				final JSONObject info = new JSONObject(new String(infoData));
				final AudioInfo aInfo = new AudioInfo();

				aInfo.jsonInfo = info;
				aInfo.title = !info.optString("title", "").isEmpty() ? info.getString("title") : !info.optString("fulltitle", "").isEmpty() ? info.getString("fulltitle") : null;
				aInfo.origin = !info.optString("webpage_url", "").isEmpty() ? info.getString("webpage_url") : this.url;
				aInfo.id = !info.optString("id", "").isEmpty() ? info.getString("id") : null;
				aInfo.encoding = !info.optString("acodec", "").isEmpty() ? info.getString("acodec") : !info.optString("ext", "").isEmpty() ? info.getString("ext") : null;
				aInfo.description = !info.optString("description", "").isEmpty() ? info.getString("description") : null;
				aInfo.extractor = !info.optString("extractor", "").isEmpty() ? info.getString("extractor") : !info.optString("extractor_key").isEmpty() ? info.getString("extractor_key") : null;
				aInfo.thumbnail = !info.optString("thumbnail", "").isEmpty() ? info.getString("thumbnail") : null;
				aInfo.duration = info.optInt("duration", -1) != -1 ? AudioTimestamp.fromSeconds(info.getInt("duration")) : null;

				this.audioInfo = aInfo;
				return aInfo;
			} catch (final IOException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	@Override
	public String getSource() {
		return this.url;
	}
}
