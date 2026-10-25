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

package net.fabricmc.installer.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import mjson.Json;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.fabricmc.installer.LoaderVersion;
import net.fabricmc.installer.NeoFabricReleaseProvider;
import net.fabricmc.installer.client.ClientInstaller;
import net.fabricmc.installer.util.Utils;

public class NeoFabricReleaseProviderTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void supportedVersionsAreImmutableInDescendingOrder() {
		Assert.assertEquals(Arrays.asList("1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21", "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1"), NeoFabricReleaseProvider.MINECRAFT_VERSIONS);
		Assert.assertThrows(UnsupportedOperationException.class, () -> NeoFabricReleaseProvider.MINECRAFT_VERSIONS.add("1.22"));
		Assert.assertThrows(UnsupportedOperationException.class, () -> NeoFabricReleaseProvider.MINECRAFT_VERSIONS.set(0, "1.22"));
		Assert.assertThrows(UnsupportedOperationException.class, () -> NeoFabricReleaseProvider.MINECRAFT_VERSIONS.remove(0));
	}

	@Test
	public void acceptsPinnedOfficialLoaderForAllEighteenVersions() throws Exception {
		Assert.assertEquals("1.2.0", NeoFabricReleaseProvider.LOADER_VERSION);
		Assert.assertEquals("https://github.com/VyskoyeNebo/NeoFabric/releases/download/v1.2.0/neofabric-loader-1.2.0.jar", NeoFabricReleaseProvider.LOADER_URL);
		Assert.assertEquals("b6c5d9d1f27fc0da250fc96a5095ac68cedb99bf46d6bd45206e0dcb60171f23", NeoFabricReleaseProvider.LOADER_SHA256);

		for (String version : NeoFabricReleaseProvider.MINECRAFT_VERSIONS) {
			NeoFabricReleaseProvider.requireSupportedVersions(version, new LoaderVersion("1.2.0"));
		}
	}

	@Test
	public void rejectsUnsupportedVersionsBeforeAccessingLoaderJar() {
		Path missingJar = temporary.getRoot().toPath().resolve("must-not-be-opened.jar");

		for (String version : Arrays.asList(null, "", "1.20", "1.20.0", "1.20.7", "1.21.0", "1.21.12", "1.22", "1.20.5-rc.1", "24w14a", " 1.20.6", "1.20.6 ")) {
			IOException error = Assert.assertThrows(IOException.class, () -> NeoFabricReleaseProvider.requireSupportedVersions(version, new LoaderVersion("1.2.0")));
			Assert.assertTrue(error.getMessage().contains("1.20.1-1.20.6 and 1.21-1.21.11 (18 versions)"));
			Assert.assertThrows(IOException.class, () -> NeoFabricReleaseProvider.profileId(version));
			IOException profileError = Assert.assertThrows(IOException.class, () -> NeoFabricReleaseProvider.createClientProfile(missingJar, version));
			Assert.assertTrue(profileError.getMessage().contains("official releases"));
		}
	}

	@Test
	public void rejectsMissingDifferentAndCustomLoaderEvenWhenVersionMatches() throws Exception {
		for (LoaderVersion version : Arrays.asList(null, new LoaderVersion("0.20.1"), new LoaderVersion("1.0.0"), new LoaderVersion("1.2.0+local"), new LoaderVersion("latest"))) {
			IOException error = Assert.assertThrows(IOException.class, () -> NeoFabricReleaseProvider.requireSupportedVersions("1.20.6", version));
			Assert.assertTrue(error.getMessage().contains("official NeoFabric 1.2.0"));
		}

		LoaderVersion custom = new LoaderVersion(NeoFabricLoaderFixture.requireLoaderJar());
		Assert.assertEquals("1.2.0", custom.name);
		Assert.assertNotNull(custom.path);
		IOException error = Assert.assertThrows(IOException.class, () -> NeoFabricReleaseProvider.requireSupportedVersions("1.20.6", custom));
		Assert.assertTrue(error.getMessage().contains("custom Loader jars are not supported"));
	}

	@Test
	public void rejectsChangedJarHashBeforeReadingInstallerMetadata() throws Exception {
		Path changed = temporary.getRoot().toPath().resolve("changed-loader.jar");

		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(changed))) {
			zip.putNextEntry(new ZipEntry("fabric-installer.json"));
			zip.write("{\"mainClass\":{\"client\":\"untrusted.Main\"},\"libraries\":{\"common\":[],\"client\":[]}}".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}

		IOException error = Assert.assertThrows(IOException.class, () -> NeoFabricReleaseProvider.createClientProfile(changed, "1.20.6"));
		Assert.assertTrue(error.getMessage().contains("SHA-256 verification failed"));
	}

	@Test
	public void clientInstallerRejectsUnsupportedRequestsBeforeCreatingDirectories() {
		Path gameDir = temporary.getRoot().toPath().resolve("new-game-directory");
		Assert.assertThrows(IOException.class, () -> ClientInstaller.install(gameDir, "1.22", new LoaderVersion("1.2.0"), null));
		Assert.assertThrows(IOException.class, () -> ClientInstaller.install(gameDir, "1.20.6", new LoaderVersion("0.20.1"), null));
		Assert.assertFalse(Files.exists(gameDir));
	}

	@Test
	public void officialLoaderMetadataHasExactlyTheInstallerWhitelist() throws Exception {
		try (ZipFile zip = new ZipFile(NeoFabricLoaderFixture.requireLoaderJar().toFile());
				InputStream input = zip.getInputStream(zip.getEntry("fabric.mod.json"))) {
			Json metadata = Json.read(Utils.readString(input));
			Assert.assertEquals(NeoFabricReleaseProvider.LOADER_VERSION, metadata.at("version").asString());
			Set<String> versions = new HashSet<>();

			for (Json version : metadata.at("depends").at("minecraft").asJsonList()) {
				Assert.assertTrue(versions.add(version.asString()));
			}

			Assert.assertEquals(new HashSet<>(NeoFabricReleaseProvider.MINECRAFT_VERSIONS), versions);
		}
	}

	@Test
	public void profileIdsAreDistinctAndSeparateFromOfficialFabric() throws Exception {
		Set<String> profileIds = new HashSet<>();

		for (String version : NeoFabricReleaseProvider.MINECRAFT_VERSIONS) {
			String profileId = NeoFabricReleaseProvider.profileId(version);
			Assert.assertEquals("neofabric-loader-1.2.0-" + version, profileId);
			Assert.assertFalse(profileId.startsWith("fabric-loader-"));
			Assert.assertTrue(profileIds.add(profileId));
		}

		Assert.assertEquals(18, profileIds.size());
	}
}
