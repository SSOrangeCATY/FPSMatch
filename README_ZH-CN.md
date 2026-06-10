# FPSMatch

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/SSOrangeCATY/FPSMatch)
[English Documentation](README.md)

FPSMatch 是面向 Minecraft 1.20.1 Forge 的库模组，为团队制竞技 FPS 玩法提供基础框架。它专注于可复用的对局系统、地图实例、经济接口、商店、HUD 工具、统计数据与战术武器模组集成点，本身不直接提供完整游戏模式。

## 功能概览

| 模块 | 说明 |
| --- | --- |
| 对局框架 | 团队制比赛生命周期、地图实例、旁观流程与可扩展模式逻辑 |
| 队伍系统 | 队伍分配、队伍状态、对局侧数据与回合制玩法支持 |
| 经济系统 | 自定义金钱系统与队伍商店基础设施，可用于武器、装备和投掷物购买 |
| 地图工具 | 面向管理员的地图与商店编辑工具，便于制作自定义 FPS 玩法 |
| HUD 与统计 | 支持自定义 HUD，并记录 KDA、爆头、伤害等玩家统计数据 |
| 兼容集成 | 围绕 TaCZ、Modern UI、CounterStrikeGrenade、KubeJS 及相关 Forge 玩法模组集成 |
| 指令帮助 | 游戏内可通过 `/fpsm help` 查看指令帮助 |

## 版本兼容矩阵

带 `*` 的列为必须依赖，未标注的模组列为兼容集成项。

| FPSMatch | 分发来源 | Minecraft* | Forge* | Modern UI | TaCZ* | LR Tactical | CounterStrikeGrenade | KubeJS |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1.3.0 | GitHub snapshot | 1.20.1 | 47.3.11 | 3.11.1.6 | 1.1.7-hotfix | 0.3.0 | 1.4.1 | 2001.6.5-build.14 |
| 1.2.5 | Modrinth / CurseForge | 1.20.1 | 47.3.11 | 3.11.1.6 | 1.1.7-hotfix | 0.3.0 | 1.4.1 | 2001.6.5-build.14 |

## 下载

| 平台 | 链接 |
| --- | --- |
| GitHub Releases | [Releases](https://github.com/SSOrangeCATY/FPSMatch/releases) |
| Modrinth | [Modrinth 上的 FPSMatch](https://modrinth.com/mod/fpsmatch) |
| CurseForge | [CurseForge 上的 FPSMatch](https://www.curseforge.com/minecraft/mc-mods/fpsmatch) |

## 如何依赖 FPSMatch

FPSMatch 可以从公开的模组分发 Maven 仓库中拉取。根据你希望使用的分发平台，在 Gradle 中选择对应仓库和依赖坐标即可。

### CurseForge Maven

CurseForge Maven 通过 CurseForge 项目 ID 与文件 ID 解析产物。当前已确认的 CurseForge 项目 ID 为 `1331710`，`1.2.5` 对应的公开文件 ID 为 `7109977`。

```gradle
repositories {
    maven {
        name = "CurseMaven"
        url = "https://www.cursemaven.com"
    }
}

dependencies {
    modImplementation "curse.maven:fpsmatch-1331710:7109977"
}
```

### Modrinth Maven

Modrinth Maven 通过项目 slug 与 Modrinth 版本号解析产物。当前已确认的项目 slug 为 `fpsmatch`。

```gradle
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}

dependencies {
    modImplementation "maven.modrinth:fpsmatch:1.2.5"
}
```

如果使用项目自身 Maven 发布配置生成的源码构建产物，依赖坐标为 `com.phasetranscrystal:fpsmatch:<FPSMatch 版本>`。

## 社区与链接

| 资源 | 链接 |
| --- | --- |
| GitHub | [SSOrangeCATY/FPSMatch](https://github.com/SSOrangeCATY/FPSMatch) |
| BlockOffensive | [SSOrangeCATY/BlockOffensive](https://github.com/SSOrangeCATY/BlockOffensive) |
| 指令帮助 | [CommandHelper_en-us.md](https://github.com/SSOrangeCATY/FPSMatch/blob/master/CommandHelper_en-us.md) |
| Bilibili | [作者主页](https://space.bilibili.com/21254202) |
| QQ 群 | 771884981 |
| 反馈表 | [腾讯文档](https://docs.qq.com/sheet/DQnZtS2l6dmNsaHBw?tab=BB08J2) |

## 许可证

使用 FPSMatch 1.1.13 或更高版本即表示你接受 GPL v3 条款。完整许可证见 [LICENSE](LICENSE)。1.1.12 及更早版本使用此前的 MIT 许可证。
