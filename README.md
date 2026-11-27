<div align="center">

# 🧪 Decant!

**A lightweight, 100% offline Water Sort puzzle crafted for smooth play—even on 512 MB RAM Android devices.**

[![Platform](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose%20%2B%20M3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Offline](https://img.shields.io/badge/100%25%20Offline-Zero%20Tracking-0EA5E9?style=for-the-badge)](#privacy--architecture)
[![License: MIT](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](LICENSE)

*Developed by **[znraznra](https://github.com/znraznra)** · Project **`decant`** · Package `com.znraznra.decant`*

[About](#-about-decant) · [Features](#-features) · [How to Play](#-how-to-play) · [Transfer Progress](#-how-to-transfer-progress) · [Installation & APK](#-installation--getting-a-clean-apk) · [Build from Source](#-build-from-source)

</div>

---

## 🎯 About Decant!

**Decant!** is a crisp, ad-free Water Sort puzzle built natively in Kotlin and Jetpack Compose. Designed from the ground up with tight memory budgets in mind, it requires **zero gameplay permissions**, **zero accounts**, and **zero internet access** to play—while still giving you seamless offline ways to move your progress to a new phone.

> [!NOTE]
> Gameplay requires **zero runtime permissions**. Nearby Wi-Fi or Bluetooth permissions are only requested on-demand if you explicitly open the **Direct Bluetooth / Wi-Fi Transfer** sheet in Settings.

---

## ✨ Features

| Category | What's Included |
| :--- | :--- |
| **Infinite Progression** | Deterministic, solver-verified puzzles with progressive scaling across **Warmup → Classic → Trickster → Master → Abyssal** tiers (color count and tube count grow smoothly with level). |
| **Daily Challenge** | A shared date-seeded daily board identical for all players on a given day, stored locally in Room with consecutive-day **streak tracking**. |
| **Gesture-First Onboarding** | Text-free animated tap/pour gesture guidance on Level 1 plus lightweight Compose **confetti celebration** upon clearing a stage. |
| **Tactile `SoundManager` & Haptics** | Synthesized low-RAM PCM audio clips (`AudioTrack.MODE_STATIC`, `< 90 KB` total) for tube selection, **liquid pouring**, **color mixing**, undo, invalid moves, and **level completion**—paired with calibrated haptics. |
| **Subtle Solve Timer** | Optional, lifecycle-aware solve timer (pauses automatically when leaving the board) that records **best times per level and per difficulty tier**. |
| **4 Studio Themes & Accessibility** | Switch or randomize across **Clean Glass**, **Cozy Pastel**, **Dark Neon**, and **Botanical Zen**, plus **Colorblind Symbols & Patterns** rendered inside every liquid layer. |
| **Adaptive Responsive UI** | Tailored layouts for compact phones, landscape screens, foldables, and tablets using Material 3 `WindowSizeClass` and edge-to-edge insets. |

---

## 🎮 How to Play

1. **Select a source tube:** Tap any non-empty tube to lift its top liquid layers and highlight valid destination tubes.
2. **Pour & sort:** Tap an empty tube or a tube whose top color matches and has free space. Only matching top colors can stack together.
3. **Use tactical tools sparingly:**
   - **Undo (`5` per stage):** Step back recent pours—recharged back to 5 on Restart or advancing to the next stage.
   - **Restart Level:** Resets the current board cleanly to its starting tubes without regenerating a different puzzle.
   - **+1 Tube:** Adds one temporary rescue tube once per stage if you get cornered.
4. **Win condition:** Fill every color into its own single-color 4-unit tube to clear the stage and trigger the celebration!

---

## 🔄 How to Transfer Progress

**Decant!** includes three privacy-first offline backup paths inside **Settings**:

1. **Android OS Auto Backup (Automatic):** Enabled out of the box (`allowBackup="true"`) so Android's built-in phone-to-phone setup migrates your local Room database and DataStore preferences automatically.
2. **JSON Backup & Import (Zero Permissions):**
   - **Export:** Tap **Settings → Backup** to generate `decant-backup.json` and save or share it via Android's Share Sheet (Drive, Nearby Share, email, local Files).
   - **Restore:** Tap **Settings → Import Backup**, pick any saved `decant-backup.json`, review the confirmation summary, and replace your local Room state cleanly.
3. **Direct Bluetooth or Local Wi-Fi Transfer:**
   - Open **Settings → Direct Bluetooth / Wi-Fi Transfer** on both phones, tap **Receive** on the new device, tap **Find Phone** on the old device, and tap the discovered peer to transfer progress directly.

---

## 📦 Installation & Getting a Clean APK

### Option 1 — Download from AI Studio

1. Open the **Settings (⚙️)** menu in the AI Studio workspace toolbar.
2. Choose **Generate APK / AAB** (or **Build APK**).
3. Wait for the cloud Gradle build to finish, then download the clean `.apk` file to your computer or phone.

### Option 2 — Install on Your Android Device (Sideload)

1. Copy the downloaded APK (`app-debug.apk` or your signed release APK) to your Android phone (USB, Quick Share, or cloud drive).
2. Open the **Files** app on your Android device and tap the `.apk` file.
3. If prompted, enable **Allow from this source** under Android's *Install unknown apps* prompt.
4. Tap **Install** — Android verifies **Decant!** (`com.znraznra.decant`) and adds it to your app drawer with zero permissions required.

### Option 3 — Download from GitHub Releases

Grab the latest pre-built APK from the [Releases](https://github.com/znraznra/decant/releases) page of [`znraznra/decant`](https://github.com/znraznra/decant) and follow Option 2 above.

---

## 🛠️ Build from Source

### Prerequisites

- **Android Studio** (Meerkat / Ladybug or newer) or **Android SDK (API 36)** + **JDK 17**

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/znraznra/decant.git
cd decant

# 2. Run all JVM & Robolectric unit tests
./gradlew :app:testDebugUnitTest

# 3. Assemble a clean debug APK
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# 4. (Optional) Assemble a signed release APK (with keystore env vars configured)
./gradlew :app:assembleRelease
```

---

## 🔒 Privacy & Architecture

- **Package ID:** `com.znraznra.decant`
- **Architecture:** MVVM (`GameBoardViewModel` + `StateFlow`) + `ProgressRepository` backed by **Room** (progress, daily challenges, active board, difficulty records) and **Preferences DataStore** (audio, haptics, timer, colorblind, theme settings).
- **Audio Engine:** Pure Kotlin procedural PCM synthesizer (`SoundManager`) avoiding heavyweight media asset overhead.
- **No Analytics / No Ads / No Account Lock-in.**

---

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for details.

<div align="center">
Made with care by <a href="https://github.com/znraznra"><strong>znraznra</strong></a>
</div>
