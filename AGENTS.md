# 仓库指南（Repository Guidelines）

## 分支与工作区
- 本文档适用于 **Forge 1.20.1** 分支 `1.20.1`；NeoForge 1.21.1 版本在 `main` 分支维护。
- 在对应版本的工作区内修改代码，不要在 Forge 工作区切换到 `main`。需要处理另一版本时，使用对应工作区。
- 下文文件路径均相对于仓库根目录。

## 项目概述
ToTheSky（`tothesky`）是作者 RiaAED 为 RiaFST 服务器开发的核心模组——「一点点混沌，很多的乐趣」。本分支基于 Minecraft 1.20.1 和 Forge，包含自定义物品、方块、配方、交互功能及其他模组的集成。


## 构建与依赖
- **版本**：Minecraft 1.20.1、Forge 47.4.10、Create 6.0.8；具体配置以 `gradle.properties` 与 `build.gradle` 为准。
- **构建工具**：ForgeGradle 6.0.54 + Gradle 8.4 wrapper；Java 编译目标为 17（`options.release = 17`），使用官方 Mojang 映射。CI 使用 Temurin 21 构建，产物仍面向 Java 17。
- **依赖来源**：多数模组依赖通过 Modrinth Maven 解析；部分依赖由 `libs/` 下的本地 jar 提供。`libs/` 被 git 忽略，本地依赖的文件名须符合 `build.gradle` 中的坐标；准备方式可参考 `.github/workflows/build.yml`。
- **Create 依赖**：使用去掉 `META-INF/jarjar/` 的 `create-1.20.1-6.0.8-slim.jar`，并单独提供 Ponder、Flywheel、Registrate、MixinExtras。依赖版本与声明见构建文件。
- **其他本地依赖**：包括 `ultramarine-1.20.1-0.5.1.jar`、`chinjufumod-7.0.1.jar`。注意 flatDir 文件命名，例如镇守府 jar 需使用不含方括号的文件名。Builders Crafts & Additions 通过 Modrinth Maven 解析。
- **JEI 集成**：JEI 通过 `compileOnly` 提供客户端集成 API，并通过 `runtimeOnly` 加入开发运行环境，不打入本模组 jar。
- **开发运行依赖**：AppleSkin、Jade 仅通过 `runtimeOnly` 加入开发运行环境，不进入编译依赖，也不声明为模组运行必需依赖。
- **可选集成**：编译依赖与正式运行依赖不是同一概念；运行时依赖关系以 `src/main/resources/META-INF/mods.toml` 为准。新增集成应正确处理目标模组未安装的情况。

## 架构与数据流
- 单模块 Forge 模组。入口点是 `src/main/java/com/fst/tothesky/ToTheSky.java` 中的 `@Mod` 类 `ToTheSky`。
- 构造函数通过 `FMLJavaModLoadingContext` 获取 mod 事件总线，注册配置、各类 `DeferredRegister` 与网络消息。
- 元数据流：`gradle.properties` 的值 → `processResources` 展开 `src/main/resources/META-INF/mods.toml` 和 `pack.mcmeta` 中的 `${...}` 占位符。编辑源文件，不修改 `build/` 下的生成产物；本分支不使用 `src/main/templates/`。
- 数据生成流：`runData` 写入 `src/generated/resources/`，该目录已挂入主资源集，生成内容随模组资源一起打包。
- Mixin 注解处理器生成 `build/mixin/tothesky.refmap.json`，由资源处理任务打包。Mixin 配置通过开发目录和正式 jar 的清单 `MixinConfigs` 加载。
- `jar` 任务完成后执行 `reobfJar`，将产物转换为正式运行环境使用的 SRG 名称。正式使用构建产物前应确认整个构建成功，不能使用重混淆失败后留下的开发 jar。

## 关键目录与文件
- `src/main/java/com/fst/tothesky/` — 模组代码；`registry/` 为注册表，`mixin/` 为 Mixin 类。
- `src/main/resources/` — 静态资源、配方、标签、战利品表及 Mixin 配置。
- `src/main/resources/META-INF/mods.toml` — 模组元数据与运行依赖声明。
- `src/main/resources/tothesky.mixins.json` — 通用 Mixin 配置。
- `src/main/resources/tothesky.oelib.mixins.json` — OELib 集成的 Mixin 配置。
- `src/generated/resources/` — 数据生成输出。
- `libs/` — 本地依赖 jar，不纳入版本控制。
- `run/` — 开发运行目录，不纳入版本控制。
- `build.gradle` — ForgeGradle、依赖、开发运行配置、资源处理和打包配置。
- `gradle.properties` — 版本与模组身份属性。
- `.github/workflows/build.yml` — CI 依赖准备、构建和产物上传。
- `docs/serverTest_todo.md` — 游戏内测试清单。

## 开发命令
使用仓库自带的 Gradle 8.4 wrapper。以下命令以 POSIX 为例；Windows PowerShell 下将 `./gradlew` 替换为 `.\gradlew.bat`。

- `./gradlew build` — 编译、资源处理、打包与重混淆，产物为 `build/libs/tothesky-<mod_version>.jar`。
- `./gradlew runClient` — 启动开发客户端。
- `./gradlew runServer` — 启动开发服务端（`--nogui`）。
- `./gradlew runData` — 执行数据生成。
- `./gradlew publish` — 发布到仓库内的 `repo/` Maven 目录。
- `./gradlew --refresh-dependencies build` — 刷新依赖并构建。
- `./gradlew clean` — 清理构建输出。

## 代码约定与 API
- 模组 ID 使用 `ToTheSky.MODID`；日志使用 `ToTheSky.LOGGER`。
- 注册表使用 Forge 的 `DeferredRegister`，挂载到 mod 事件总线。
- Java 源码和编译编码使用 UTF-8，使用 Java 17 支持的语法与 API。
- Forge 1.20.1 无数据组件和 `AttachmentType`；物品数据使用 NBT，玩家持久化数据使用 `persistentData`。物品 NBT 工具位于 `registry/ModNbt`。
- 方块交互使用 `use()`；`MobEffect.applyEffectTick` 返回 `void`。
- Kitchen Karrot 1.20.1 的鸡尾酒使用 NBT 和数据资源：`CocktailItem.getCocktail/setCocktail`、配方 JSON 的 `content.effect`、`assets/<ns>/cocktail/list.json`。
- Create 6 的 `CustomUseEffectsItem.shouldTriggerUseEffects` 返回 catnip `TriState`；`AnimationTickHolder` 位于 `net.createmod.catnip.animation`。
- Mixin 类放入对应配置声明的包，并添加到相应列表。通用配置的 `defaultRequire: 1` 要求注入匹配目标；`overwrites.requireAnnotations: true` 要求覆盖方法带注解。

## 资源格式
- 数据目录使用 `data/<ns>/recipes/`、`data/<ns>/loot_tables/` 等复数名称。
- 有序合成配方结果使用 `"item"` 字段，通用标签使用 `forge:` 命名空间。
- Create 配方字段使用 `heatRequirement`、`transitionalItem`（驼峰）；流体 ingredient 使用扁平对象 `{ "fluid": "<namespace:id>", "amount": <数量> }`。

## 测试与验证
- CI 执行 `./gradlew build`。
- 当前没有 `src/test` 源码与 JUnit 依赖，Gradle 的 `test` 任务不提供功能验证。
- 当前构建未配置 `gameTestServer` run；不要将其作为已有验证命令。
- 游戏内交互、渲染和模组兼容性需在对应运行环境中验证，可参考 `docs/serverTest_todo.md`。报告验证结果时区分构建通过与实际游戏验证。

## Git 提交规范
- 提交标题以 `feat:`、`fix:`、`refactor:`、`docs:`、`chore:`、`style:`、`test:`、`build:` 等 Conventional Commits 前缀开头。
- 标题用中文描述，简洁清晰，例如 `fix: 修正模组名编码`、`docs: 更新测试待办`。
- 远端 `origin` 指向 `https://github.com/RIA-AED/ToTheSky`。
