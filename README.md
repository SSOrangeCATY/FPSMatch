# FPSMatch

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/SSOrangeCATY/FPSMatch)
[中文文档](README_ZH-CN.md)

FPSMatch is a Forge library mod for Minecraft 1.20.1 that provides the foundation for team-based competitive FPS gameplay. It focuses on reusable match systems, map instances, economy hooks, shops, HUD utilities, stats, and integration points for tactical weapon mods instead of shipping a complete game mode by itself.

## Features

| Area | Description |
| --- | --- |
| Match framework | Team-based match lifecycle, map instances, spectator flow, and extensible mode logic |
| Teams | Team assignment, team state, match-side data, and round-oriented gameplay support |
| Economy | Custom money systems and team shop infrastructure for weapons, gear, and throwables |
| Map tools | Admin-facing map and shop editing utilities for custom FPS experiences |
| HUD and stats | HUD support plus player statistics such as KDA, headshots, and damage |
| Compatibility | Integrates with TaCZ, Modern UI, CounterStrikeGrenade, KubeJS, and related Forge gameplay mods |
| Commands | In-game command help is available with `/fpsm help` |

## Version Compatibility Matrix

Columns marked with `*` are required dependencies. Unmarked mod columns are compatibility integrations.

| FPSMatch | Distribution | Minecraft* | Forge* | Modern UI | TaCZ* | TaCZ Tweaks | LR Tactical | CounterStrikeGrenade | KubeJS |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1.3.0 | GitHub snapshot | 1.20.1 | 47.3.11 | 3.11.1.6 | 1.1.7-hotfix | 2.11.2 | 0.3.0 | 1.4.1 | 2001.6.5-build.14 |
| 1.2.5 | Modrinth / CurseForge | 1.20.1 | 47.3.11 | 3.11.1.6 | 1.1.7-hotfix | - | 0.3.0 | 1.4.1 | - |

## Download

| Platform | Link |
| --- | --- |
| GitHub Releases | [Releases](https://github.com/SSOrangeCATY/FPSMatch/releases) |
| Modrinth | [FPSMatch on Modrinth](https://modrinth.com/mod/fpsmatch) |
| CurseForge | [FPSMatch on CurseForge](https://www.curseforge.com/minecraft/mc-mods/fpsmatch) |

## How to Depend on FPSMatch

FPSMatch can be consumed from the public mod distribution Maven repositories. Pick one repository and one dependency coordinate that matches the platform you want to resolve from.

### CurseForge Maven

CurseForge Maven resolves artifacts by CurseForge project ID and file ID. The current confirmed CurseForge project ID is `1331710`, and the confirmed public file ID for `1.2.5` is `7109977`.

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

Modrinth Maven resolves artifacts by project slug and Modrinth version number. The confirmed project slug is `fpsmatch`.

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

For source builds published through the project's own Maven publishing configuration, the artifact coordinate is `com.phasetranscrystal:fpsmatch:<FPSMatch version>`.

## Community and Links

| Resource | Link |
| --- | --- |
| GitHub | [SSOrangeCATY/FPSMatch](https://github.com/SSOrangeCATY/FPSMatch) |
| Wiki | [Developer Wiki](https://github.com/SSOrangeCATY/FPSMatch/wiki) |
| BlockOffensive | [SSOrangeCATY/BlockOffensive](https://github.com/SSOrangeCATY/BlockOffensive) |
| Command helper | [CommandHelper_en-us.md](https://github.com/SSOrangeCATY/FPSMatch/blob/master/CommandHelper_en-us.md) |
| Bilibili | [Author page](https://space.bilibili.com/21254202) |
| QQ group | 771884981 |
| Feedback sheet | [Tencent Docs](https://docs.qq.com/sheet/DQnZtS2l6dmNsaHBw?tab=BB08J2) |

## License

By using FPSMatch 1.1.13 or later, you agree to the terms of GPL v3. The complete license is available in [LICENSE](LICENSE). Earlier versions up to 1.1.12 used the previous MIT license.
