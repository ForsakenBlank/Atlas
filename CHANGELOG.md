# Changelog

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
