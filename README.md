# MILK — Backup Gallery

A creamy, simple browser-based backup gallery.

## Run it
Open `index.html` in a modern browser, or host this repository with GitHub Pages / Vercel. For the installable PWA features, use an HTTPS-hosted version.

## Features
- Add multiple files to your gallery
- Stores backup copies in browser IndexedDB on the current device
- Image and video previews
- Search and filter by file type
- Download a saved copy or delete a backup
- Optional notifications when files are added

## Important limitation
This is a web app, not a native Android app. Browsers do not let a normal website silently monitor every download or read the phone's Downloads folder automatically. Files must be added through the file picker. Phone-wide automatic backup would require a separate native Android implementation with clear user permission.

## Data
Backups stay in the browser storage on the device where they were added. Clearing site/browser data may remove them. This is not cloud storage.
