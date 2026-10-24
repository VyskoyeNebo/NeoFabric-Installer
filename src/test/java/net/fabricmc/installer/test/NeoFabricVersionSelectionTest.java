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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.fabricmc.installer.InstallerGui;
import net.fabricmc.installer.Main;
import net.fabricmc.installer.NeoFabricReleaseProvider;
import net.fabricmc.installer.client.ClientHandler;
import net.fabricmc.installer.util.ArgumentParser;
import net.fabricmc.installer.util.MetaHandler;

public class NeoFabricVersionSelectionTest {
	private static final List<String> EXPECTED_VERSIONS = Arrays.asList("1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1");
	private MetaHandler previousGameMetadata;
	private MetaHandler previousLoaderMetadata;

	@Before
	public void prepareFixedMetadata() {
		previousGameMetadata = Main.GAME_VERSION_META;
		previousLoaderMetadata = Main.LOADER_META;
		Main.GAME_VERSION_META = MetaHandler.fixedVersions("game", NeoFabricReleaseProvider.MINECRAFT_VERSIONS);
		Main.LOADER_META = MetaHandler.fixedVersion("loader", NeoFabricReleaseProvider.LOADER_VERSION);
	}

	@After
	public void restoreMetadata() {
		Main.GAME_VERSION_META = previousGameMetadata;
		Main.LOADER_META = previousLoaderMetadata;
	}

	@Test
	public void fixedMetadataLoadsExactlySixStableVersionsWithoutNetwork() {
		Main.loadMetadata();
		Assert.assertEquals(EXPECTED_VERSIONS, Main.GAME_VERSION_META.getVersions().stream()
				.map(MetaHandler.GameVersion::getVersion).collect(Collectors.toList()));
		Assert.assertTrue(Main.GAME_VERSION_META.getVersions().stream().allMatch(MetaHandler.GameVersion::isStable));
		Assert.assertEquals("1.20.6", Main.GAME_VERSION_META.getLatestVersion(false).getVersion());
		Assert.assertEquals("1.0.0", Main.LOADER_META.getLatestVersion(false).getVersion());
	}

	@Test
	public void fixedMetadataDefensivelyCopiesItsVersionList() throws Exception {
		List<String> input = new ArrayList<>(EXPECTED_VERSIONS);
		MetaHandler metadata = MetaHandler.fixedVersions("game", input);
		input.clear();
		metadata.load();
		Assert.assertEquals(EXPECTED_VERSIONS, metadata.getVersions().stream()
				.map(MetaHandler.GameVersion::getVersion).collect(Collectors.toList()));
		Assert.assertThrows(UnsupportedOperationException.class, () -> metadata.getVersions().clear());
	}

	@Test
	public void commandLineAcceptsEveryExactReleaseAndDefaultsTo1206() {
		Main.loadMetadata();
		ClientCliProbe probe = new ClientCliProbe();

		for (String version : EXPECTED_VERSIONS) {
			Assert.assertEquals(version, probe.gameVersion(ArgumentParser.create(new String[] {"client", "-mcversion", version})));
		}

		Assert.assertEquals("1.20.6", probe.gameVersion(ArgumentParser.create(new String[] {"client"})));
		Assert.assertEquals("1.20.6", probe.gameVersion(ArgumentParser.create(new String[] {"client", "-mcversion", "latest"})));
	}

	@Test
	public void commandLineRejectsSnapshotsAndDisplayLabelsEvenWithSnapshotFlag() {
		Main.loadMetadata();
		ClientCliProbe probe = new ClientCliProbe();

		for (String invalid : Arrays.asList("1.20", "1.20.7", "1.21", "1.20.5-pre1", "1.20.6-rc1", "24w14a", "1.20.5 (Java 21)", " 1.20.5")) {
			Assert.assertNull(Main.GAME_VERSION_META.parseVersion(invalid, true));
			Assert.assertThrows(IllegalArgumentException.class, () -> probe.gameVersion(ArgumentParser.create(new String[] {
					"client", "-mcversion", invalid, "-snapshot"
			})));
		}
	}

	@Test
	public void guiUsesRawVersionsAndOnlyLabelsJava21Releases() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			ClientHandler handler = new ClientHandler();
			handler.makePanel(null);
			Main.loadMetadata();
			JComboBox<String> versions = handler.gameVersionComboBox;
			Assert.assertEquals(EXPECTED_VERSIONS.size(), versions.getItemCount());
			Assert.assertEquals("1.20.6", versions.getSelectedItem());
			Assert.assertFalse(handler.snapshotCheckBox.isEnabled());
			ListCellRenderer<? super String> renderer = versions.getRenderer();

			for (int i = 0; i < EXPECTED_VERSIONS.size(); i++) {
				String version = EXPECTED_VERSIONS.get(i);
				Assert.assertEquals(version, versions.getItemAt(i));
				versions.setSelectedItem(version);
				Assert.assertEquals(version, versions.getSelectedItem());
				JLabel rendered = (JLabel) renderer.getListCellRendererComponent(new JList<String>(), version, i, false, false);

				if (version.equals("1.20.5") || version.equals("1.20.6")) {
					Assert.assertEquals(version + " (Java 21)", rendered.getText());
				} else {
					Assert.assertEquals(version, rendered.getText());
				}
			}
		});
	}

	@Test
	public void windowTitleShowsTheEntireSupportedRange() {
		Assert.assertTrue(InstallerGui.windowTitle().contains("NeoFabric"));
		Assert.assertTrue(InstallerGui.windowTitle().contains("Minecraft 1.20.1–1.20.6"));
	}

	private static final class ClientCliProbe extends ClientHandler {
		String gameVersion(ArgumentParser args) {
			return getGameVersion(args);
		}
	}
}
