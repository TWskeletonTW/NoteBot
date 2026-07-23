# NoteBot v1.0

## 中文說明

NoteBot 是從 BleachHack 1.2.6 的 Notebot 功能獨立移植而成的 Fabric 客戶端模組，支援 Minecraft Java Edition 26.2。

### 環境需求

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 或更新版本
- Fabric API 0.155.2+26.2
- Java 25

### 功能

- 在生存或冒險模式中自動調整並演奏附近的音符盒。
- Minecraft 26.2 原生歌曲清單、設定與預覽介面。
- 支援 `.nbs`、`.mid`、`.midi`、`.txt` 與 `.notelist`。
- 將聽到的音符盒聲音錄製成 Notelist 歌曲。
- 一般、等待 1 拍、等待 2 拍、每批 5 次及一次完成等調音模式。
- 循環播放、自動續播與忽略樂器模式。
- 以綠色／紅色粒子標示已調準／尚未調準的音符盒。
- 可選擇下載 BleachHack 舊版歌曲包，並包含 ZIP 路徑安全檢查。
- 完整繁體中文遊戲介面。

### 使用方式

將歌曲放入：

```text
.minecraft/config/notebot/songs/
```

預設快捷鍵：

- `N`：開啟 NoteBot 介面
- `P`：播放或停止
- `O`：開始或停止錄製

客戶端指令：

```text
/notebot
/notebot toggle
/notebot record
/notebot folder
```

請將需要的音符盒放在玩家四格範圍內，音符盒上方必須保持空氣。先在介面選擇歌曲並按下「選取」，再按「播放」。

### 建置

Windows：

```text
gradlew.bat build
```

成品位於 `build/libs/NoteBot-1.0.jar`。

---

## English

NoteBot is a standalone Fabric client mod ported from BleachHack 1.2.6's Notebot for Minecraft Java Edition 26.2.

### Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.155.2+26.2
- Java 25

### Features

- Automatically tune and play nearby note blocks in Survival or Adventure mode.
- Native Minecraft 26.2 song browser, settings, and preview GUI.
- Supports `.nbs`, `.mid`, `.midi`, `.txt`, and `.notelist` files.
- Record note-block sounds into Notelist songs.
- Normal, Wait-1, Wait-2, Batch-5, and All tuning modes.
- Loop, autoplay, and ignore-instrument modes.
- Green/red particles indicate tuned and untuned note blocks.
- Optional BleachHack legacy song-pack download with ZIP path validation.
- English and Traditional Chinese interfaces.

### Usage

Copy songs into:

```text
.minecraft/config/notebot/songs/
```

Default keys:

- `N`: open the NoteBot GUI
- `P`: play or stop
- `O`: start or stop recording

Client commands:

```text
/notebot
/notebot toggle
/notebot record
/notebot folder
```

Place the required note blocks within four blocks of the player with air above them. Select a song in the GUI, press **Select**, then press **Play**.

### Build

On Windows:

```text
gradlew.bat build
```

The distributable JAR is written to `build/libs/NoteBot-1.0.jar`.

## License and attribution

This project contains code derived from BleachHack by BleachHack contributors and is licensed under the GNU General Public License version 3 or later. The modern GUI and Fabric 26.2 integration are distributed under the same license.
