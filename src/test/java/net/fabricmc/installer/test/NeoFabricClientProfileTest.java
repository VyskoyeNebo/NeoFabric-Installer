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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipFile;

import mjson.Json;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.fabricmc.installer.NeoFabricReleaseProvider;
import net.fabricmc.installer.util.Library;
import net.fabricmc.installer.util.Utils;

@RunWith(Parameterized.class)
public class NeoFabricClientProfileTest {
	private static Path loaderJar;
	private static Json installerMetadata;
	private final String gameVersion;
	private final int javaVersion;
	private final String runtimeComponent;

	@Parameterized.Parameters(name = "Minecraft {0}, Java {1}")
	public static Collection<Object[]> versions() {
		return Arrays.asList(new Object[][] {
				{"1.20.1", 17, "java-runtime-gamma"},
				{"1.20.2", 17, "java-runtime-gamma"},
				{"1.20.3", 17, "java-runtime-gamma"},
				{"1.20.4", 17, "java-runtime-gamma"},
				{"1.20.5", 21, "java-runtime-delta"},
				{"1.20.6", 21, "java-runtime-delta"},
				{"1.21", 21, "java-runtime-delta"},
				{"1.21.1", 21, "java-runtime-delta"},
				{"1.21.2", 21, "java-runtime-delta"},
				{"1.21.3", 21, "java-runtime-delta"},
				{"1.21.4", 21, "java-runtime-delta"},
				{"1.21.5", 21, "java-runtime-delta"},
				{"1.21.6", 21, "java-runtime-delta"},
				{"1.21.7", 21, "java-runtime-delta"},
				{"1.21.8", 21, "java-runtime-delta"},
				{"1.21.9", 21, "java-runtime-delta"},
				{"1.21.10", 21, "java-runtime-delta"},
				{"1.21.11", 21, "java-runtime-delta"}
		});
	}

	public NeoFabricClientProfileTest(String gameVersion, int javaVersion, String runtimeComponent) {
		this.gameVersion = gameVersion;
		this.javaVersion = javaVersion;
		this.runtimeComponent = runtimeComponent;
	}

	@BeforeClass
	public static void loadOfficialReleaseFixture() throws Exception {
		loaderJar = NeoFabricLoaderFixture.requireLoaderJar();

		try (ZipFile zip = new ZipFile(loaderJar.toFile());
				InputStream input = zip.getInputStream(zip.getEntry("fabric-installer.json"))) {
			installerMetadata = Json.read(Utils.readString(input));
		}
	}

	@Test
	public void generatedProfileUsesSelectedGameVersionAndVanillaArguments() throws Exception {
		Json profile = Json.read(NeoFabricReleaseProvider.createClientProfile(loaderJar, gameVersion).toString());
		Assert.assertEquals("neofabric-loader-1.2.0-" + gameVersion, profile.at("id").asString());
		Assert.assertEquals(gameVersion, profile.at("inheritsFrom").asString());
		Assert.assertEquals("release", profile.at("type").asString());
		Assert.assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", profile.at("mainClass").asString());
		Assert.assertEquals(installerMetadata.at("mainClass").at("client").asString(), profile.at("mainClass").asString());
		Assert.assertEquals(javaVersion, profile.at("javaVersion").at("majorVersion").asInteger());
		Assert.assertEquals(runtimeComponent, profile.at("javaVersion").at("component").asString());
		Assert.assertTrue(profile.at("arguments").at("game").asJsonList().isEmpty());
		Assert.assertEquals(1, profile.at("arguments").asJsonMap().size());
		Assert.assertFalse(profile.has("minecraftArguments"));
		Assert.assertFalse(profile.has("jvmArguments"));
	}

	@Test
	public void librariesUsePinnedReleaseArtifactAndMatchingIntermediary() throws Exception {
		List<Json> libraries = NeoFabricReleaseProvider.createClientProfile(loaderJar, gameVersion).at("libraries").asJsonList();
		Json loader = libraries.get(0);
		Assert.assertEquals("io.github.vyskoyenebo:neofabric-loader:1.2.0", loader.at("name").asString());
		Json artifact = loader.at("downloads").at("artifact");
		Assert.assertEquals("io/github/vyskoyenebo/neofabric-loader/1.2.0/neofabric-loader-1.2.0.jar", artifact.at("path").asString());
		Assert.assertEquals("https://github.com/VyskoyeNebo/NeoFabric/releases/download/v1.2.0/neofabric-loader-1.2.0.jar", artifact.at("url").asString());
		Assert.assertEquals(NeoFabricLoaderFixture.digest(loaderJar, "SHA-1"), artifact.at("sha1").asString());
		Assert.assertEquals(Files.size(loaderJar), artifact.at("size").asLong());
		Assert.assertEquals("net.fabricmc:intermediary:" + gameVersion, libraries.get(1).at("name").asString());
		Assert.assertEquals("https://maven.fabricmc.net/", libraries.get(1).at("url").asString());
		int dependencyIndex = 2;

		for (String side : new String[] {"common", "client"}) {
			for (Json source : installerMetadata.at("libraries").at(side).asJsonList()) {
				Library expected = new Library(source);
				Json library = libraries.get(dependencyIndex++);
				Assert.assertEquals(expected.name, library.at("name").asString());
				Json downloaded = library.at("downloads").at("artifact");
				Assert.assertEquals(expected.getPath().replace('\\', '/'), downloaded.at("path").asString());
				Assert.assertEquals(expected.getURL(), downloaded.at("url").asString());

				if (source.has("sha1")) Assert.assertEquals(source.at("sha1"), downloaded.at("sha1"));
				if (source.has("size")) Assert.assertEquals(source.at("size"), downloaded.at("size"));
			}
		}

		Assert.assertEquals(dependencyIndex, libraries.size());
		Assert.assertEquals(1, libraries.stream().filter(library -> library.at("name").asString().contains(":neofabric-loader:")).count());
		Assert.assertFalse(libraries.stream().anyMatch(library -> library.at("name").asString().startsWith("net.fabricmc:fabric-loader:")));
	}
}
