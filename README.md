# MILK — Backup Gallery

A creamy milk-carton themed browser backup gallery.

## Features
- Animated milk carton with a gently moving milk wave and droplets
- A user-triggered synthesized cow "moo" sound (no external audio files)
- Add multiple files and save copies in browser IndexedDB
- Image and video previews, search, and file-type filters
- Download a saved copy or delete a backup
- Optional browser notifications when files are added
- Responsive layout and reduced-motion support

## Run it
Host this repository with GitHub Pages or Vercel (HTTPS recommended), then open the site in a modern browser. The app can also be opened locally, but service-worker/offline installation features require HTTPS or localhost.

## Important limitations
This is a web app, not a native Android app. A normal website cannot silently monitor every Android download or automatically read the phone's Downloads folder. Files must be added through the file picker. Phone-wide automatic backup requires a native Android app and clear user permission.

Backups stay in browser storage on the device where they were added. Clearing browser/site data may remove them. This is not cloud storage.
