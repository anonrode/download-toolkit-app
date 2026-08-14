# Anon Downloader Mobile App 📱

High-efficiency private Android Downloader app connecting to the Anonrode `downloadtoolkitserver` backend.

## 🚀 Features

* **Zero-Setup Search:** Pre-configured with server endpoint & API key; starts immediately on search.
* **True HTTP Range Pause & Resume:** Downloads write to `.part` files with byte offset streaming; resumes seamlessly on network drops without restarting from 0%.
* **Hotlink Header Resolution:** Automatically resolves direct CDN URLs and attaches required `Referer` and `User-Agent` headers to prevent 403 Forbidden errors.
* **Auto-Organized Storage:** Automatically creates and categorizes downloads under `/storage/emulated/0/Download/Anon/{Show_Name}/`.
* **Batch Downloader:** Queue individual episodes or use "Select All" / Range download to batch queue an entire season.
* **1-Tap VLC Launch:** Open completed downloads in VLC or any external player with a single tap.
* **Automated Cloud CI/CD:** GitHub Actions automatically compiles and releases standalone release `.apk` files on every push.

## 📦 Automated Cloud Builds (GitHub Actions)

Pushing changes to `master` or `main` automatically triggers `.github/workflows/build-apk.yml`, building `app-release.apk` on GitHub's cloud runners in ~90 seconds. You can download the latest APK directly from the **Releases** or **Actions** tab on GitHub.
