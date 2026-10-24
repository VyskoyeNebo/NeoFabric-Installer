# NeoFabric Installer

NeoFabric Installer 1.1.0 为 Minecraft 1.20.1–1.20.6 安装 [NeoFabric Loader v1.0](https://github.com/VyskoyeNebo/NeoFabric/releases/tag/v1.0.0)。依然是图一乐，不过这次六个版本都能选了。

## 使用

- 下载 [Releases](https://github.com/VyskoyeNebo/NeoFabric-Installer/releases) 中的 Installer jar，运行 `java -jar neofabric-installer-1.1.0.jar`。
- 选择 Minecraft 版本和游戏目录，关闭正在运行的启动器后安装。每个版本会生成独立的 NeoFabric 启动配置，不会覆盖官方 Fabric 配置。
- Minecraft 1.20.1–1.20.4 使用 Java 17；1.20.5、1.20.6 使用 Java 21。下拉菜单会标明 Java 21 要求；游戏使用的 Java 由启动器管理，安装器不会替换系统 Java。
- 已为你准备好多版本内置 API，无需另装 Fabric API。仅支持在 `fabric.mod.json` 中声明 `"neofabric": true` 且满足当前游戏依赖的定制模组。
- 本次提供客户端安装。命令行示例：`java -jar neofabric-installer-1.1.0.jar client -dir <游戏目录> -mcversion 1.20.6`。

## 构建与测试

使用 JDK 21 构建；Installer 的 Java 8 编译目标保持不变。测试读取真实的 Loader 正式版 jar，并验证六版本启动配置、下载校验、菜单选项和配置隔离，不在 JUnit 中联网。

先准备测试资源（PowerShell）：

```powershell
New-Item -ItemType Directory -Force build/test-fixtures
Invoke-WebRequest https://github.com/VyskoyeNebo/NeoFabric/releases/download/v1.0.0/neofabric-loader-1.0.0.jar -OutFile build/test-fixtures/neofabric-loader-1.0.0.jar
.\gradlew.bat build
```

也可以通过 `-PneofabricTestLoaderJar=<已有 jar 的路径>` 指定测试资源。安装器和测试都会核验固定的 SHA-256：

```text
155b81273ac03d56c3ea6c67e7fd7a64be5e3d29aa24551deceb09e413c8673d
```

GitHub Actions 会自动下载并验证该资源，完成构建、测试和产物上传。成品位于 `build/libs/`。

## 许可证

基于 [FabricMC/fabric-installer](https://github.com/FabricMC/fabric-installer)，保留原 LICENSE、HEADER 和版权声明，沿用现有红石中继器图标。原项目和本项目修改均遵循 Apache-2.0。
