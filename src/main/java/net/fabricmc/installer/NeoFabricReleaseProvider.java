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

package net.fabricmc.installer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import mjson.Json;

import net.fabricmc.installer.util.HttpClient;
import net.fabricmc.installer.util.Library;
import net.fabricmc.installer.util.Utils;

public final class NeoFabricReleaseProvider {
	public static final List<String> MINECRAFT_VERSIONS = Collections.unmodifiableList(Arrays.asList(
			"1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1"));
	public static final String LOADER_VERSION = "1.0.0";
	public static final String LOADER_URL = "https://github.com/VyskoyeNebo/NeoFabric/releases/download/v1.0.0/neofabric-loader-1.0.0.jar";
	public static final String LOADER_SHA256 = "155b81273ac03d56c3ea6c67e7fd7a64be5e3d29aa24551deceb09e413c8673d";
	public static final String LOADER_LIBRARY_NAME = "io.github.vyskoyenebo:neofabric-loader:" + LOADER_VERSION;
	public static final String LOADER_LIBRARY_PATH = "io/github/vyskoyenebo/neofabric-loader/" + LOADER_VERSION + "/neofabric-loader-" + LOADER_VERSION + ".jar";
	private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";

	private NeoFabricReleaseProvider() {
	}

	public static String profileId(String gameVersion) throws IOException {
		requireSupportedGameVersion(gameVersion);
		return "neofabric-loader-" + LOADER_VERSION + "-" + gameVersion;
	}

	private static void requireSupportedGameVersion(String gameVersion) throws IOException {
		if (!MINECRAFT_VERSIONS.contains(gameVersion)) {
			throw new IOException("NeoFabric only supports Minecraft 1.20.1 through 1.20.6 official releases; requested: " + gameVersion);
		}
	}

	public static void requireSupportedVersions(String gameVersion, LoaderVersion loaderVersion) throws IOException {
		requireSupportedGameVersion(gameVersion);

		if (loaderVersion == null || !LOADER_VERSION.equals(loaderVersion.name) || loaderVersion.path != null) {
			throw new IOException("NeoFabric Installer only supports the official NeoFabric " + LOADER_VERSION + " release; custom Loader jars are not supported.");
		}
	}

	public static Path downloadLoader(Path librariesDir) throws IOException {
		Path destination = librariesDir.resolve(LOADER_LIBRARY_PATH);

		if (Files.isRegularFile(destination) && LOADER_SHA256.equals(digest(destination, "SHA-256"))) {
			return destination;
		}

		Files.createDirectories(destination.getParent());
		Path temporary = Files.createTempFile(destination.getParent(), "neofabric-loader-", ".tmp");

		try {
			HttpClient.downloadFile(new URL(LOADER_URL), temporary);
			verifyLoader(temporary);

			try {
				Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			}

			return destination;
		} catch (IOException e) {
			throw new IOException("Failed to download or verify NeoFabric " + LOADER_VERSION + " from " + LOADER_URL, e);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public static Json createClientProfile(Path loaderJar, String gameVersion) throws IOException {
		String profileId = profileId(gameVersion);
		verifyLoader(loaderJar);

		try (ZipFile zip = new ZipFile(loaderJar.toFile())) {
			ZipEntry entry = zip.getEntry("fabric-installer.json");
			if (entry == null) throw new IOException("NeoFabric release is missing fabric-installer.json");
			Json metadata;

			try (InputStream input = zip.getInputStream(entry)) {
				metadata = Json.read(Utils.readString(input));
			}

			Json libraries = Json.array();
			libraries.add(Json.object("name", LOADER_LIBRARY_NAME, "downloads", Json.object("artifact", Json.object(
					"path", LOADER_LIBRARY_PATH, "url", LOADER_URL, "sha1", digest(loaderJar, "SHA-1"), "size", Files.size(loaderJar)))));
			libraries.add(Json.object("name", "net.fabricmc:intermediary:" + gameVersion, "url", FABRIC_MAVEN));

			for (String side : new String[] {"common", "client"}) {
				for (Json library : metadata.at("libraries").at(side).asJsonList()) {
					Library dependency = new Library(library);
					Json artifact = Json.object("path", dependency.getPath().replace('\\', '/'), "url", dependency.getURL());

					if (library.has("sha1")) artifact.set("sha1", library.at("sha1"));
					if (library.has("size")) artifact.set("size", library.at("size"));

					libraries.add(Json.object("name", dependency.name, "downloads", Json.object("artifact", artifact)));
				}
			}

			int javaVersion = "1.20.5".equals(gameVersion) || "1.20.6".equals(gameVersion) ? 21 : 17;
			return Json.object("id", profileId, "inheritsFrom", gameVersion, "type", "release",
					"mainClass", metadata.at("mainClass").at("client").asString(),
					"javaVersion", Json.object("component", javaVersion == 21 ? "java-runtime-delta" : "java-runtime-gamma", "majorVersion", javaVersion),
					"arguments", Json.object("game", Json.array()), "libraries", libraries);
		} catch (RuntimeException e) {
			throw new IOException("Invalid fabric-installer.json in NeoFabric " + LOADER_VERSION, e);
		}
	}

	private static void verifyLoader(Path loaderJar) throws IOException {
		if (!LOADER_SHA256.equals(digest(loaderJar, "SHA-256"))) {
			throw new IOException("NeoFabric " + LOADER_VERSION + " SHA-256 verification failed; refusing to install this jar.");
		}
	}

	private static String digest(Path file, String algorithm) throws IOException {
		MessageDigest digest;

		try {
			digest = MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Missing Java digest algorithm: " + algorithm, e);
		}

		try (InputStream input = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			int length;

			while ((length = input.read(buffer)) != -1) {
				digest.update(buffer, 0, length);
			}
		}

		StringBuilder hex = new StringBuilder();

		for (byte value : digest.digest()) {
			hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
			hex.append(Character.forDigit(value & 0xf, 16));
		}

		return hex.toString();
	}
}
