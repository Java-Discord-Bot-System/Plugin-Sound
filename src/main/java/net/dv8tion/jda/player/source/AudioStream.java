/**
 * Copyright 2016 Austin Keener Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package net.dv8tion.jda.player.source;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioStream extends BufferedInputStream {
	private static Pattern		TIME_PATTERN	= Pattern.compile("(?<=time=).*?(?= bitrate)");

	// Represent the processes that control the Python Youtube-dl and the FFmpeg program.
	private Process				ytdlProcess;
	private Process				ffmpegProcess;

	// Async threads that deal with the piping of the outputs of the processes.
	private Thread				ytdlToFFmpegThread;
	private Thread				ytdlErrGobler;
	private Thread				ffmpegErrGobler;

	private final List<String>	ytdlLaunchArgs;
	private final List<String>	ffmpegLaunchArgs;
	private AudioTimestamp		timestamp		= null;

	protected AudioStream(final String url, final List<String> ytdlLaunchArgs, final List<String> ffmpegLaunchArgs) {
		super(null);
		this.ytdlLaunchArgs = ytdlLaunchArgs;
		this.ffmpegLaunchArgs = ffmpegLaunchArgs;
		this.setup();
	}

	@Override
	public void close() throws IOException {
		if (this.in != null) {
			this.in.close();
			this.in = null;
		}
		if (this.ytdlToFFmpegThread != null) {
			this.ytdlToFFmpegThread.interrupt();
			this.ytdlToFFmpegThread = null;
		}
		if (this.ytdlErrGobler != null) {
			this.ytdlErrGobler.interrupt();
			this.ytdlErrGobler = null;
		}
		if (this.ffmpegErrGobler != null) {
			this.ffmpegErrGobler.interrupt();
			this.ffmpegErrGobler = null;
		}
		if (this.ffmpegProcess != null) {
			this.ffmpegProcess.destroy();
			this.ffmpegProcess = null;
		}
		if (this.ytdlProcess != null) {
			this.ytdlProcess.destroy();
			this.ytdlProcess = null;
		}
		super.close();
	}

	public AudioTimestamp getCurrentTimestamp() {
		return this.timestamp;
	}

	private void setup() {
		try {
			final ProcessBuilder pBuilder = new ProcessBuilder();

			pBuilder.command(this.ytdlLaunchArgs);
			System.out.println("Command: " + pBuilder.command());
			this.ytdlProcess = pBuilder.start();

			pBuilder.command(this.ffmpegLaunchArgs);
			System.out.println("Command: " + pBuilder.command());
			this.ffmpegProcess = pBuilder.start();

			final Process ytdlProcessF = this.ytdlProcess;
			final Process ffmpegProcessF = this.ffmpegProcess;

			this.ytdlToFFmpegThread = new Thread() {
				@Override
				public void run() {
					InputStream fromYTDL = null;
					OutputStream toFFmpeg = null;
					try {
						fromYTDL = ytdlProcessF.getInputStream();
						toFFmpeg = ffmpegProcessF.getOutputStream();

						final byte[] buffer = new byte[1024];
						int amountRead = -1;
						while (!this.isInterrupted() && (amountRead = fromYTDL.read(buffer)) > -1) {
							toFFmpeg.write(buffer, 0, amountRead);
						}
						toFFmpeg.flush();
					} catch (final IOException e) {
						e.printStackTrace();
					} finally {
						try {
							if (fromYTDL != null) {
								fromYTDL.close();
							}
						} catch (final IOException e) {
							e.printStackTrace();
						}
						try {
							if (toFFmpeg != null) {
								toFFmpeg.close();
							}
						} catch (final IOException e) {
							e.printStackTrace();
						}
					}
				}
			};

			this.ytdlErrGobler = new Thread() {
				@Override
				public void run() {

					try {
						InputStream fromYTDL = null;

						fromYTDL = ytdlProcessF.getErrorStream();
						if (fromYTDL == null) {
							System.out.println("fromYTDL is null");
						}

						final byte[] buffer = new byte[1024];
						while (!this.isInterrupted() && fromYTDL.read(buffer) > -1) {
							// Dont spam my sysout!
							// System.out.println("ERR YTDL: " + new String(Arrays.copyOf(buffer, amountRead)));
						}
					} catch (final IOException e) {
						e.printStackTrace();
					}
				}
			};

			this.ffmpegErrGobler = new Thread() {
				@Override
				public void run() {
					try {
						InputStream fromFFmpeg = null;

						fromFFmpeg = ffmpegProcessF.getErrorStream();
						if (fromFFmpeg == null) {
							System.out.println("fromYTDL is null");
						}

						final byte[] buffer = new byte[1024];
						int amountRead = -1;
						while (!this.isInterrupted() && (amountRead = fromFFmpeg.read(buffer)) > -1) {
							final String info = new String(Arrays.copyOf(buffer, amountRead));
							if (info.contains("time=")) {
								final Matcher m = AudioStream.TIME_PATTERN.matcher(info);
								if (m.find()) {
									AudioStream.this.timestamp = AudioTimestamp.fromFFmpegTimestamp(m.group());
								}
							}
						}
					} catch (final IOException e) {
						e.printStackTrace();
					}
				}
			};

			this.ytdlToFFmpegThread.start();
			this.ytdlErrGobler.start();
			this.ffmpegErrGobler.start();
			this.in = this.ffmpegProcess.getInputStream();
		} catch (final IOException e) {
			e.printStackTrace();
			try {
				this.close();
			} catch (final IOException e1) {
				e1.printStackTrace();
			}
		}
	}
}
