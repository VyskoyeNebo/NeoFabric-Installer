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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import mjson.Json;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import net.fabricmc.installer.NeoFabricReleaseProvider;
import net.fabricmc.installer.client.ProfileInstaller;
import net.fabricmc.installer.util.Utils;

@RunWith(Parameterized.class)
public class NeoFabricProfileIsolationTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	private final ProfileInstaller.LauncherType launcherType;

	@Parameterized.Parameters(name = "{0}")
	public static Collection<Object[]> launchers() {
		return Arrays.asList(new Object[][] {
				{ProfileInstaller.LauncherType.WIN32},
				{ProfileInstaller.LauncherType.MICROSOFT_STORE}
		});
	}

	public NeoFabricProfileIsolationTest(ProfileInstaller.LauncherType launcherType) {
		this.launcherType = launcherType;
	}

	@Test
	public void installsAllEighteenProfilesAlongsideFabricAndPreservesJavaCustomizations() throws Exception {
		Path gameDir = temporary.getRoot().toPath();
		Path launcherFile = gameDir.resolve(launcherType.profileJsonName);
		Json fabric = Json.object("name", "Fabric untouched", "lastVersionId", "fabric-loader-0.20.1-1.20.1", "icon", "Furnace");
		Json previousNeoFabric = Json.object("name", "old name", "lastVersionId", "neofabric-loader-1.0.0-1.20.1",
				"javaDir", "custom/java17/bin/java", "javaArgs", "-Xmx4G -Duser.preference=true");
		Json original = Json.object("profiles", Json.object("fabric-loader-1.20.1", fabric, "neofabric-loader-1.20.1", previousNeoFabric));
		Files.write(launcherFile, original.toString().getBytes(StandardCharsets.UTF_8));
		ProfileInstaller installer = new ProfileInstaller(gameDir);

		for (String version : NeoFabricReleaseProvider.MINECRAFT_VERSIONS) {
			installer.setupProfile(NeoFabricReleaseProvider.profileId(version), version, launcherType);
		}

		installer.setupProfile(NeoFabricReleaseProvider.profileId("1.20.6"), "1.20.6", launcherType);
		Json profiles = Json.read(Utils.readString(launcherFile)).at("profiles");
		Assert.assertEquals(19, profiles.asJsonMap().size());
		Assert.assertEquals(fabric, profiles.at("fabric-loader-1.20.1"));
		Set<String> installedVersions = new HashSet<>();

		for (String version : NeoFabricReleaseProvider.MINECRAFT_VERSIONS) {
			Json profile = profiles.at("neofabric-loader-" + version);
			Assert.assertEquals("NeoFabric " + version, profile.at("name").asString());
			Assert.assertEquals("neofabric-loader-1.2.0-" + version, profile.at("lastVersionId").asString());
			Assert.assertEquals(Utils.getProfileIcon(), profile.at("icon").asString());
			Assert.assertTrue(installedVersions.add(profile.at("lastVersionId").asString()));
		}

		Assert.assertEquals(18, installedVersions.size());
		Assert.assertEquals("custom/java17/bin/java", profiles.at("neofabric-loader-1.20.1").at("javaDir").asString());
		Assert.assertEquals("-Xmx4G -Duser.preference=true", profiles.at("neofabric-loader-1.20.1").at("javaArgs").asString());
	}
}
