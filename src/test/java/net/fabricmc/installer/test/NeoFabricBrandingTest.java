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

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.imageio.ImageIO;

import mjson.Json;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.fabricmc.installer.NeoFabricReleaseProvider;
import net.fabricmc.installer.client.ProfileInstaller;
import net.fabricmc.installer.util.Utils;

public class NeoFabricBrandingTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void launcherProfilePreservesFabricAndRefreshesNeoFabric() throws Exception {
		Path directory = temporary.getRoot().toPath();
		Path file = directory.resolve("launcher_profiles.json");
		Json fabric = Json.object("name", "Original Fabric", "lastVersionId", "fabric-loader-0.20.1-1.20.1", "icon", "Furnace");
		Json original = Json.object("profiles", Json.object("fabric-loader-1.20.1", fabric,
				"neofabric-loader-1.20.1", Json.object("name", "old name", "icon", "old icon")));
		Files.write(file, original.toString().getBytes(StandardCharsets.UTF_8));
		ProfileInstaller installer = new ProfileInstaller(directory);
		installer.setupProfile(NeoFabricReleaseProvider.PROFILE_ID, "1.20.1", ProfileInstaller.LauncherType.WIN32);
		installer.setupProfile(NeoFabricReleaseProvider.PROFILE_ID, "1.20.1", ProfileInstaller.LauncherType.WIN32);
		Json profiles = Json.read(Utils.readString(file)).at("profiles");
		Assert.assertEquals(fabric, profiles.at("fabric-loader-1.20.1"));
		Assert.assertEquals(2, profiles.asJsonMap().size());
		Json neoFabric = profiles.at("neofabric-loader-1.20.1");
		Assert.assertEquals("NeoFabric 1.20.1", neoFabric.at("name").asString());
		Assert.assertEquals(NeoFabricReleaseProvider.PROFILE_ID, neoFabric.at("lastVersionId").asString());
		Assert.assertEquals(Utils.getProfileIcon(), neoFabric.at("icon").asString());
	}

	@Test
	public void launcherProfileCreatesMissingProfilesObject() throws Exception {
		Path directory = temporary.getRoot().toPath();
		Path file = directory.resolve("launcher_profiles.json");
		Files.write(file, "{}".getBytes(StandardCharsets.UTF_8));
		new ProfileInstaller(directory).setupProfile(NeoFabricReleaseProvider.PROFILE_ID, "1.20.1", ProfileInstaller.LauncherType.WIN32);
		Assert.assertEquals("NeoFabric 1.20.1", Json.read(Utils.readString(file)).at("profiles").at("neofabric-loader-1.20.1").at("name").asString());
	}

	@Test
	public void windowAndLauncherIconsAreMatchingSquarePngs() throws Exception {
		BufferedImage window;
		BufferedImage profile;

		try (InputStream input = getClass().getResourceAsStream("/icon.png")) {
			window = ImageIO.read(input);
		}

		try (InputStream input = getClass().getResourceAsStream("/profile_icon.png")) {
			profile = ImageIO.read(input);
		}

		Assert.assertNotNull(window);
		Assert.assertNotNull(profile);
		Assert.assertTrue(window.getWidth() >= 64);
		Assert.assertEquals(window.getWidth(), window.getHeight());
		Assert.assertEquals(window.getWidth(), profile.getWidth());
		Assert.assertEquals(window.getHeight(), profile.getHeight());
		Assert.assertArrayEquals(window.getRGB(0, 0, window.getWidth(), window.getHeight(), null, 0, window.getWidth()),
				profile.getRGB(0, 0, profile.getWidth(), profile.getHeight(), null, 0, profile.getWidth()));
	}

	@Test
	public void successMessageExplainsBundledApiAndModGate() throws Exception {
		String expected = "安装成功！NeoFabric 已内置 Fabric API，请勿放入普通 Fabric 模组，仅支持声明 NeoFabric 兼容标识的定制模组。";

		for (String resource : new String[] {"/lang/installer.properties", "/lang/installer_zh_cn.properties"}) {
			Properties language = new Properties();

			try (InputStream input = getClass().getResourceAsStream(resource)) {
				language.load(new InputStreamReader(input, StandardCharsets.UTF_8));
			}

			Assert.assertTrue(language.getProperty("installer.title").startsWith("NeoFabric"));
			Assert.assertEquals(expected, language.getProperty("prompt.install.successful"));
		}
	}
}
