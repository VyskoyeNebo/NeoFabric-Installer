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
import java.nio.file.Paths;
import java.security.MessageDigest;

import org.junit.Assert;

import net.fabricmc.installer.NeoFabricReleaseProvider;

final class NeoFabricLoaderFixture {
	private NeoFabricLoaderFixture() {
	}

	static Path requireLoaderJar() throws Exception {
		String configured = System.getProperty("neofabric.test.loaderJar");
		Assert.assertNotNull("Set neofabric.test.loaderJar to the downloaded official NeoFabric 1.0.0 jar; tests never download fixtures or skip verification.", configured);
		Assert.assertFalse("neofabric.test.loaderJar must not be empty", configured.trim().isEmpty());
		Path fixture = Paths.get(configured);
		Assert.assertTrue("Missing official Loader fixture: " + fixture, Files.isRegularFile(fixture));
		Assert.assertEquals("Official Loader fixture SHA-256 mismatch", NeoFabricReleaseProvider.LOADER_SHA256, digest(fixture, "SHA-256"));
		return fixture;
	}

	static String digest(Path file, String algorithm) throws Exception {
		MessageDigest digest = MessageDigest.getInstance(algorithm);

		try (InputStream input = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			int count;

			while ((count = input.read(buffer)) != -1) {
				digest.update(buffer, 0, count);
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
