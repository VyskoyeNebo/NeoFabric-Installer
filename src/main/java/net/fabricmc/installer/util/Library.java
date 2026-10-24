/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.installer.util;

import java.io.File;
import java.nio.file.Path;

import mjson.Json;

public class Library {
	public final String name;
	public final String url;
	public final Path inputPath;
	private final String artifactUrl;
	private final String artifactPath;

	public Library(String name, String url, Path inputPath) {
		this.name = name;
		this.url = url;
		this.inputPath = inputPath;
		this.artifactUrl = null;
		this.artifactPath = null;
	}

	public Library(Json json) {
		name = json.at("name").asString();
		url = json.has("url") ? json.at("url").asString() : null;
		inputPath = null;
		Json artifact = json.has("downloads") ? json.at("downloads").at("artifact") : null;
		artifactUrl = artifact == null ? null : artifact.at("url").asString();
		artifactPath = artifact == null ? null : artifact.at("path").asString();
	}

	public String getURL() {
		if (artifactUrl != null) return artifactUrl;

		String path;
		String[] parts = this.name.split(":", 3);
		path = parts[0].replace(".", "/") + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";

		return url + path;
	}

	public String getPath() {
		if (artifactPath != null) return artifactPath.replace('/', File.separatorChar);

		String[] parts = this.name.split(":", 3);
		String path = parts[0].replace(".", File.separator) + File.separator + parts[1] + File.separator + parts[2] + File.separator + parts[1] + "-" + parts[2] + ".jar";
		return path.replaceAll(" ", "_");
	}
}
