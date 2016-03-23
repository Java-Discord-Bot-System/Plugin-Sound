/**
 * Copyright 2016 Austin Keener Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package net.dv8tion.jda.player.source;

import org.json.JSONObject;

public class AudioInfo {
	protected JSONObject		jsonInfo;
	protected String			title;
	protected String			origin;
	protected String			id;
	protected String			encoding;
	protected String			description;
	protected String			extractor;
	protected String			thumbnail;
	protected AudioTimestamp	duration;

	public String getDescription() {
		return this.description;
	}

	public AudioTimestamp getDuration() {
		return this.duration;
	}

	public String getEncoding() {
		return this.encoding;
	}

	public String getExtractor() {
		return this.extractor;
	}

	public String getId() {
		return this.id;
	}

	public JSONObject getJsonInfo() {
		return this.jsonInfo;
	}

	public String getOrigin() {
		return this.origin;
	}

	public String getThumbnail() {
		return this.thumbnail;
	}

	public String getTitle() {
		return this.title;
	}
}