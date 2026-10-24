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

package net.fabricmc.installer.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;

import mjson.Json;

import net.fabricmc.installer.LoaderVersion;
import net.fabricmc.installer.NeoFabricReleaseProvider;
import net.fabricmc.installer.util.FabricService;
import net.fabricmc.installer.util.InstallerProgress;
import net.fabricmc.installer.util.Library;
import net.fabricmc.installer.util.Utils;

public class ClientInstaller {
	public static String install(Path mcDir, String gameVersion, LoaderVersion loaderVersion, InstallerProgress progress) throws IOException {
		NeoFabricReleaseProvider.requireSupportedVersions(gameVersion, loaderVersion);
		System.out.println("Installing " + gameVersion + " with NeoFabric " + loaderVersion.name);

		String profileName = NeoFabricReleaseProvider.PROFILE_ID;
		Path libsDir = mcDir.resolve("libraries");
		progress.updateProgress(new MessageFormat(Utils.BUNDLE.getString("progress.download.library.entry")).format(new Object[]{NeoFabricReleaseProvider.LOADER_LIBRARY_NAME}));
		Path loaderJar = NeoFabricReleaseProvider.downloadLoader(libsDir);
		Json json = NeoFabricReleaseProvider.createClientProfile(loaderJar);

		Path versionsDir = mcDir.resolve("versions");
		Path profileDir = versionsDir.resolve(profileName);
		Path profileJson = profileDir.resolve(profileName + ".json");

		if (!Files.exists(profileDir)) {
			Files.createDirectories(profileDir);
		}

		Path profileJar = profileDir.resolve(profileName + ".jar");
		Files.deleteIfExists(profileJar);

		/*
		Downloading the libraries isn't strictly necessary as the launcher will do it for us.
		Do it anyway in case the launcher fails, we know we have a working connection to maven here.
		 */
		for (Json libraryJson : json.at("libraries").asJsonList()) {
			Library library = new Library(libraryJson);
			Path libraryFile = libsDir.resolve(library.getPath());
			String url = library.getURL();

			// The pinned release has already been downloaded and verified by the provider.
			if (NeoFabricReleaseProvider.LOADER_LIBRARY_NAME.equals(library.name)) continue;

			//System.out.println("Downloading "+url+" to "+libraryFile);
			progress.updateProgress(new MessageFormat(Utils.BUNDLE.getString("progress.download.library.entry")).format(new Object[]{library.name}));
			FabricService.downloadSubstitutedMaven(url, libraryFile);
		}

		Files.write(profileJson, json.toString().getBytes(StandardCharsets.UTF_8));
		progress.updateProgress(Utils.BUNDLE.getString("progress.done"));

		return profileName;
	}
}
