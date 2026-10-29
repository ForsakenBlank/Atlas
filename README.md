<div align="center">

# Atlas

**One app for your notes, trackers, money, plans and everything in between. On your phone and your PC.**

[![Build](https://github.com/ForsakenBlank/Atlas/actions/workflows/build.yml/badge.svg)](https://github.com/ForsakenBlank/Atlas/actions/workflows/build.yml)
![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Windows](https://img.shields.io/badge/Windows-10%2B-0078D4?logo=windows&logoColor=white)
![Linux](https://img.shields.io/badge/Linux-Hyprland%20ready-FCC624?logo=linux&logoColor=black)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)

[Download](#download-and-install) · [Features](#features) · [Sync](#wi-fi-sync) · [Linux and Hyprland](#linux-and-hyprland) · [Building](#building-it-yourself)

</div>

> [!WARNING]
> **This app is vibe coded.** It was built mostly by chatting with an AI coding assistant, not written line by
> line by an experienced developer. It works, but expect rough edges and bugs.
> **Keep backups of your data:** Settings → Backup and data → **Back up now** saves everything to a file you can restore later.

---

Atlas swaps a dozen separate apps for one. Notes, a journal, habits, time trackers, a calendar, your school timetable, tasks, countdowns, spreadsheets, money and a toolbox of calculators, all sharing one database so they can talk to each other. A tracker log shows up on the calendar, a task can belong to a subject, and Home pulls today's bits from everywhere.

The same app runs on **Android**, **Windows** and **Linux**, and your devices sync straight to each other over Wi-Fi. There are no accounts, no ads and no cloud.

## Download and install

Get the newest version from the [**Releases**](https://github.com/ForsakenBlank/Atlas/releases/latest) page. Under
**Assets** at the bottom, download the file for your device.

### Android
1. Download `atlas.apk` on your phone.
2. Open it. The first time, Android asks you to allow installing apps from your browser or file manager: allow it.
3. Tap **Install**. To update later, install the new `atlas.apk` over the old one; your data stays.

### Windows
1. Download `Atlas-windows-x64.zip`.
2. Right-click it → **Extract All…** and pick a folder (for example `Documents\Atlas`).
3. Open the folder and double-click `Atlas.exe`. If Windows SmartScreen warns you, press **More info** → **Run anyway**.
4. Want it in the Start menu? Right-click `Atlas.exe` → **Show more options** → **Pin to Start**.

### Linux
1. Download `Atlas-linux-x64.tar.gz`.
2. Unpack it and add it to your app launcher:
   ```
   tar -xzf Atlas-linux-x64.tar.gz
   ./Atlas/install.sh
   ```
3. Atlas is now in your launcher (rofi, wofi, fuzzel, walker…) and `atlas` starts it from a terminal. To try it without
   installing, run `./Atlas/bin/Atlas` instead.
4. To update, unpack the new version and run its `install.sh` again.

On Hyprland the window class is `atlas`, for window rules.

**Syncing with your phone?** If the phone can't reach the PC, the PC's firewall is blocking Atlas (CachyOS and some
other distros turn one on by default). Allow its port once: `sudo ufw allow 47830/tcp` (ufw), or
`sudo firewall-cmd --permanent --add-port=47830/tcp && sudo firewall-cmd --reload` (firewalld).

### Your data
Updating never touches your data. The PC version keeps it in `%LOCALAPPDATA%\Atlas` (Windows) and
`~/.local/share/atlas` (Linux); phone builds are all signed with the same key, so a new version installs over the old
one.


## Features

| | Section | What it does |
|---|---|---|
| 🏠 | **Home** | Greeting with a ring for today's trackers, a one tap mood, now and next, countdowns, today's agenda, pinned notes and tracker and money shortcuts |
| 📝 | **Notes** | Tags, pins, colours, autosave, list, grid or compact layouts, sorting, sharing |
| 📖 | **Journal** | A few lines and a mood for every day, with the last month as a strip of coloured dots |
| 📊 | **Sheets** | Simple spreadsheets with formulas like `=SUM(A1:A5)`, `=AVERAGE` and `=IF` |
| 📅 | **Calendar** | Month, week and agenda views with events, tasks, classes, birthdays, tracker logs, day notes and a full day popup |
| 🏫 | **Timetable** | Weekly grid, subjects with teachers and rooms, week A and B, term dates |
| ✅ | **Tasks** | Quick add that understands "tomorrow", "friday" and "!!", due dates, priorities and repeats |
| 🔁 | **Habits** | Every yes or no and counter tracker as a week of ticks, with streaks |
| ⏳ | **Countdowns** | Birthdays, anniversaries, holidays, count ups, ages and milestones |
| 💷 | **Money** | Accounts, transfers, past records, quick adds, charts by category and a UK tax, NI and student loan calculator |
| 📈 | **Track** | Counter, yes or no, timer, number and rating trackers with goals, streaks, charts and back dated logs |
| 💡 | **Insights** | This week against last for every tracker, streaks, tasks, mood, money for the month and your year so far |
| 🎯 | **Focus** | Pomodoro timer that can log finished sessions to a tracker |
| 🧰 | **Tools** | Dice, coin, random picker, odds converter, percentages, tip splitter, unit converter, date maths, tally and stopwatch |
| 🗂️ | **Explorer** | One folder tree for notes, trackers and sheets, with a trash |
| 🔍 | **Search** | One search across notes, tasks, events, countdowns, trackers and sheets |
| 🔔 | **Reminders** | Morning nudge for birthdays and tasks due today, plus a heads up before timed events |

### On your phone

- **Home screen widget** with today's tasks, how many habits are done, and buttons for a new note, focus and the journal
- **Long press the app icon** for New note, Tasks, Start focus and Journal
- **Quick settings tile** that starts and pauses the focus timer without opening the app

### On your PC

- Every section in a **sidebar** instead of the phone's bottom bar, the window remembers its size
- **Esc** and the mouse back button go back a page
- Reminders show as desktop notifications while Atlas is open

### Make it yours

- **25 built in themes** (Caelestia, Catppuccin, Tokyo Night, Gruvbox, Rosé Pine, Everforest, Material You and more) and a theme creator with a full colour picker
- **Caelestia look** in one tap: glass cards, round corners, a floating bar and smooth push transitions
- **Icon style**: outlined, rounded, sharp, filled or two tone icons everywhere
- **Card style**: filled, outlined, raised or glass
- **Around 95 settings** across 15 categories, all searchable
- **A new app icon in four colours** (Dark, Ink, Light, Sand) picked in Settings, page and tab transitions, and a bottom bar you choose the tabs for
- **Starter packs** (study, fitness, wellbeing, habits, work, money) so a fresh install is not a blank page
- **Backups** to a single file you can restore on any device

## Wi-Fi sync

Your phone and PC swap changes directly over your Wi-Fi, the same way Prism does.

1. On one device open **Sync** (the ⇄ button) and turn on **Let another device connect**. It shows an address and a 6 digit code.
2. On the other device type that address and code and press **Connect**.
3. Atlas lists what changed on each side since you last synced. Untick anything you want to leave, then press **Sync**.

What makes it nice to use:

- **Stats add up instead of overwriting.** An hour on the PC timer and 20 minutes on the phone shows 1h 20m on both. Every tap, log and money entry from both sides is kept.
- **Clashes are shown to you.** If the same note was edited on both, you pick this one, theirs, or keep both.
- **Deletes travel**, so something you removed on one device goes from the other too.
- **Starter packs and the inbox are joined up** on the first sync instead of doubled.
- **Any device can host**, so phone to phone and PC to PC work too.
- Everything sent is **locked with the code** (AES-GCM), and ten wrong codes stop hosting.

[Luminescent](https://github.com/ForsakenBlank/Luminescent) can sync with Atlas too. Its notes and timer time show up on your phone, your Atlas events and tasks show up on its Calendar page with notifications on the PC, and themes go both ways. Turn on **Settings → Appearance → Follow Luminescent's theme** and Atlas switches to whatever theme Luminescent is using after every sync.

## Linux and Hyprland

Atlas is tested with Hyprland on CachyOS.

- Run `install.sh` from the unpacked folder to add Atlas to rofi, wofi, fuzzel or whatever launcher you use. It also adds an `atlas` command.
- The window class is `atlas`, so you can write rules like `windowrulev2 = float, class:^(atlas)$`.
- Atlas runs through XWayland. If text looks small with `xwayland { force_zero_scaling = true }`, raise **Settings → Appearance → Interface size**.
- File pickers use zenity or kdialog when either is installed, and notifications go through `notify-send` (mako, dunst and swaync all work).

## Building it yourself

You need JDK 17. The Android app also needs the Android SDK (Android Studio sets both up).

```bash
git clone https://github.com/ForsakenBlank/Atlas.git
cd Atlas
./gradlew :app:assembleDebug            # the phone app
./gradlew :desktop:run                  # the pc app, straight from source
./gradlew :desktop:createDistributable  # the pc app as a folder you can copy anywhere
./gradlew :shared:desktopTest           # the tests
```

Every push is also built by GitHub Actions. The **Build** workflow makes the APK, the Windows zip and the Linux tar.gz, runs the tests and publishes all three to a release for that branch.

## Project layout

```
shared/   almost everything, compiled for the phone and the pc
  src/shared/kotlin/.../data/       database, settings, backups
  src/shared/kotlin/.../sync/       wi-fi sync: records, merge planner, encrypted wire
  src/shared/kotlin/.../ui/         one folder per section
  src/shared/kotlin/.../util/       pure logic: dates, recurrence, formulas, tax, insights
  src/desktopTest/                  unit tests, including two Atlas copies syncing for real
app/      the android shell: notifications, widget, shortcuts, quick settings tile
desktop/  the windows and linux shell: window, tray, file pickers, notifications
```

## Built with

- **Kotlin** and **Compose Multiplatform** with Material 3
- **Room** for the database, with the bundled SQLite on the PC
- **DataStore** for settings
- **kotlinx.serialization** for backups and sync
- **JUnit** and **Robolectric** for tests

## Privacy

Nothing leaves your devices. Atlas only uses the network to talk to your own devices on your Wi-Fi while you sync, and everything it sends is encrypted with the code you type. There are no accounts, analytics or servers. Besides the network, the phone app asks for notifications, vibration and starting reminders again after a reboot.
