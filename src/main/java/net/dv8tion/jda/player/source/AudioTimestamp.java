/**
 * Copyright 2016 Austin Keener Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package net.dv8tion.jda.player.source;

public class AudioTimestamp {
	protected int	hours;
	protected int	minutes;
	protected int	seconds;
	protected int	milliseconds;

	public AudioTimestamp(final int hours, final int minutes, final int seconds, final int milliseconds) {
		this.hours = hours;
		this.minutes = minutes;
		this.seconds = seconds;
		this.milliseconds = milliseconds;
	}

	public static AudioTimestamp fromFFmpegTimestamp(final String ffmpegTimestamp) {
		String[] timeParts = ffmpegTimestamp.split(":");
		final int hours = Integer.parseInt(timeParts[0]);
		final int minutes = Integer.parseInt(timeParts[1]);

		timeParts = timeParts[2].split("\\.");
		final int seconds = Integer.parseInt(timeParts[0]);
		final int milliseconds = Integer.parseInt(timeParts[1]) * 10; // Multiply by 10 because it gives us .##, instead of .###

		return new AudioTimestamp(hours, minutes, seconds, milliseconds);
	}

	public static AudioTimestamp fromSeconds(int seconds) {
		final int hours = seconds / 3600;
		seconds = seconds % 3600;

		final int minutes = seconds / 60;
		seconds = seconds % 60;

		return new AudioTimestamp(hours, minutes, seconds, 0);
	}

	@Override
	public boolean equals(final Object o) {
		if (!(o instanceof AudioTimestamp)) {
			return false;
		}

		final AudioTimestamp oTime = (AudioTimestamp) o;
		return oTime.hours == this.hours && oTime.minutes == this.minutes && oTime.seconds == this.seconds && oTime.milliseconds == this.milliseconds;
	}

	public String getFullTimestamp() {
		return String.format("%02d:%02d:%02d.%03d", this.hours, this.minutes, this.seconds, this.milliseconds);
	}

	public int getHours() {
		return this.hours;
	}

	public int getMilliseconds() {
		return this.milliseconds;
	}

	public int getMinutes() {
		return this.minutes;
	}

	public int getSeconds() {
		return this.seconds;
	}

	public String getTimestamp() {
		String timestamp = "";
		timestamp += this.hours != 0 ? String.format("%02d:", this.hours) : "";
		timestamp += String.format("%02d:%02d", this.minutes, this.seconds);
		return timestamp;
	}

	@Override
	public String toString() {
		return "AudioTimeStamp(" + this.getFullTimestamp() + ")";
	}
}
