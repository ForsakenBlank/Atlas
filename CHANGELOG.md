# Changelog

## 1.0.4

* **Sync fix**: syncing no longer fails with "couldn't save what the other device sent (Error code: 19...)" after
  something was deleted on one device and made again with the same name. Update both devices to get the fix, it
  applies on whichever one is hosting.
* If the device connecting can't save something it now says so in a full sentence, not just "Error code".

## 1.0.3

* **Tracker charts**: pick bars or a line, group by day, month or year, and tap the chart for one day's or month's
  number. Go back to any earlier week, month or year with the arrows. Month and Year now mean a calendar month and year
  instead of the last 30 and 365 days.
* **Timer averages**: trackers show the average a day, and over longer stretches a month and a year, next to the total.
* **Explorer**: hold an item (or press the tick button) to select several, then move, colour or trash them together.
  There is a search box for the folder, sort and list or tile buttons at the top, a ⋮ button on each item, and the
  move list shows each folder's full path.
* **Markdown notes**: choose plain text or markdown when you make a note, and turn a note either way from its menu.
  Markdown has a formatting bar (bold, italic, underline, strikethrough, highlight, headings, lists, checklists,
  quotes, code, links, lines and tables), shows the formatting as you type and has a preview with tickable checklists.
  Pick the font, size and line spacing from the Aa button on any note.
* **Calendar**: events over several days now show as one bar across those days. Deleting a repeating event asks whether
  you mean only this date, this one and later ones, or all of them, and a skipped date can be put back. You can also
  swipe between months, tap the month name to jump to any month and duplicate an event.

## 1.0.2

* **Sync says what went wrong**: when one device can't save what the other sent, both now show the reason instead
  of a bare "Error code", and the PC no longer stays stuck on "Saving what phone sent".
* **Fix**: the Home button works again when Sync, Settings or another page is open on top of Home.

## 1.0.1

* **Sync tells you why it can't connect**: no answer (the PC's firewall is probably blocking port 47830, with the
  command to allow it), refused (Atlas isn't hosting there) or not an address.
* When a Linux PC starts hosting with ufw or firewalld on, it shows the command to let the phone in.
* The releases page only has real versions now.

## 1.0.0

**On your phone tap `atlas.apk`. On Windows get the zip and run `Atlas.exe`. On Linux get the tar.gz, unpack it and run
`Atlas/bin/Atlas`, or run `install.sh` inside it to add Atlas to your app launcher.** Updating keeps your data.

New since the last build:

* **Updates inside Atlas**: Settings → About lists every version with what changed and a button to Update,
  Reinstall or Downgrade. On the PC it downloads the new version, swaps it in and restarts. On the phone it downloads
  the APK and opens Android's installer (Android can not go back to an older version without uninstalling, so the
  list explains how). A few seconds after opening, Atlas also tells you when a new version is out.
* **New app icon**: the route design in Dark, Ink, Light and Sand. Settings → Appearance → App icon shows all four;
  on the phone it changes the home screen icon, on the PC the window and taskbar icon.
* **Icon style**: outlined, rounded, sharp, filled or two tone icons across the whole app (Settings → Appearance).
* **Glass cards**: a see-through card style with a thin light edge.
* **Caelestia look**: one tap for the Caelestia theme, glass cards, round corners, a floating bar and push
  transitions (Settings → Appearance).
* **Eight new themes**: Caelestia, Catppuccin Mocha, Catppuccin Latte, Tokyo Night, Gruvbox, Rosé Pine, Everforest
  and Material You (25 built in now).
* **Follow Luminescent's theme**: after a sync with Luminescent, Atlas switches to the theme Luminescent is using.
  Luminescent 1.4 also shows your Atlas events and tasks on its Calendar page with notifications on the PC.

Also in 1.0 (from the 0.5 builds): the Windows and Linux app, Wi-Fi sync between your devices and with
Luminescent, Journal, Habits, Insights, the home screen widget, app shortcuts and the focus tile.
