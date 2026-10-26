<div align="center">

# Atlas

**One app for your notes, trackers, money, plans and everything in between.**

[![Build APK](https://github.com/ForsakenBlank/Atlas/actions/workflows/build.yml/badge.svg)](https://github.com/ForsakenBlank/Atlas/actions/workflows/build.yml)
![Version](https://img.shields.io/badge/version-0.4.0-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Offline](https://img.shields.io/badge/works-fully%20offline-success)

[Download](#download) · [Features](#features) · [Building](#building-it-yourself) · [Roadmap](#roadmap)

</div>

---

Atlas is an all in one Android app that swaps a dozen separate apps for one. Notes, habit and time trackers, a calendar, your school timetable, tasks, countdowns, spreadsheets, money and a toolbox of handy calculators, all sharing one database so they can talk to each other. A tracker log shows up on the calendar, a task can belong to a subject, and the Home screen pulls today's bits from everywhere.

It is completely offline. There are no accounts, no ads and no internet permission. Your data stays on your phone and you can back it up to a file whenever you like.

## Download

The easiest way is straight from your phone:

1. Open the [**Releases**](https://github.com/ForsakenBlank/Atlas/releases) page
2. Pick **Atlas latest build** and tap `atlas.apk`
3. Allow "Install unknown apps" for your browser the first time it asks

Every build is signed with the same key, so new versions install over the old one and keep all your data.

> Want to try something still being worked on? Every branch gets its own release named **Atlas build from &lt;branch&gt;**.

## Features

| | Section | What it does |
|---|---|---|
| 🏠 | **Home** | Greeting, now and next, upcoming countdowns, today's agenda, pinned notes and one tap tracker and money shortcuts with undo |
| 📝 | **Notes** | Tags, pins, colours, autosave, list, grid or compact layouts, sorting, sharing |
| 📊 | **Sheets** | Simple spreadsheets with formulas like `=SUM(A1:A5)`, `=AVERAGE` and `=IF` |
| 📅 | **Calendar** | Month, week and agenda views with events, tasks, classes, birthdays, tracker logs, day notes and a full day popup |
| 🏫 | **Timetable** | Weekly grid, subjects with teachers and rooms, week A and B, term dates |
| ✅ | **Tasks** | Quick add that understands "tomorrow", "friday" and "!!", due dates, priorities and repeats |
| ⏳ | **Countdowns** | Birthdays, anniversaries, holidays, count ups, ages and milestones |
| 💷 | **Money** | Accounts, transfers, past records, quick adds, charts by category and a UK tax, NI and student loan calculator |
| 📈 | **Track** | Counter, yes or no, timer, number and rating trackers with goals, streaks, charts and back dated logs |
| 🎯 | **Focus** | Pomodoro timer that can log finished sessions to a tracker |
| 🧰 | **Tools** | Dice, coin, random picker, odds converter, percentages, tip splitter, unit converter, date maths, tally and stopwatch |
| 🗂️ | **Explorer** | One folder tree for notes, trackers and sheets, with a trash |
| 🔍 | **Search** | One search across notes, tasks, events, countdowns, trackers and sheets |
| 🔔 | **Reminders** | Morning nudge for birthdays and tasks due today, plus a heads up before timed events |

### Make it yours

- **17 built in themes** and a theme creator with a full colour picker
- **Around 90 settings** across 15 categories, all searchable
- **Five app icons**, page and tab transitions, and a bottom bar you choose the tabs for
- **Starter packs** (study, fitness, wellbeing, habits, work, money) so a fresh install is not a blank page
- **Backups** to a single file you can restore on any phone

## Building it yourself

You need JDK 17 and the Android SDK. Android Studio sets both up for you.

```bash
git clone https://github.com/ForsakenBlank/Atlas.git
cd Atlas
./gradlew assembleDebug       # builds the apk
./gradlew testDebugUnitTest   # runs the unit tests
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

Every push is also built by GitHub Actions. The **Build APK** workflow compiles the app, runs the tests and publishes the APK to a release for that branch.

## Project layout

```
app/src/main/java/com/forsakenblank/atlas/
├── data/       Room database, entities, DAOs, settings, backups
├── notify/     reminder scheduling and notifications
├── ui/         one folder per section (notes, track, money, calendar...)
└── util/       pure logic: dates, recurrence, sheet formulas, tax maths
app/src/test/   unit tests for the logic in util/ and database migrations
```

## Built with

- **Kotlin** and **Jetpack Compose** with Material 3
- **Room** for the database
- **DataStore** for settings
- **kotlinx.serialization** for backups
- **Robolectric** and JUnit for tests

## Roadmap

- [x] Notes, trackers, explorer, themes and backups
- [x] Calendar, timetable, tasks and tools
- [x] Sheets, countdowns, reminders and search
- [x] Money section with UK tax calculator
- [ ] Desktop version for Windows and Linux
- [ ] Two way sync between phone and PC over your local network, with smart merging so tracker time adds together instead of overwriting

## Privacy

Atlas does not ask for internet access, so nothing you put in it can leave your phone unless you export a backup yourself. The only permissions it uses are notifications, vibration and starting reminders again after a reboot.
