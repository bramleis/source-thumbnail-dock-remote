KOMSOS THUMBNAIL REMOTE — ANDROID PROJECT
==========================================

Aplikasi Android native untuk mengendalikan OBS Studio secara langsung melalui
OBS WebSocket v5. Aplikasi tidak memerlukan Node.js, operator.html, remote.html,
SOURCE THUMBNAIL DOCK, atau server perantara pada laptop.

FITUR
- Koneksi langsung ke alamat IP laptop, port OBS WebSocket (default 4455),
  dan password OBS.
- Pemindai QR; mendukung JSON host/port/password serta pola ws:// atau obsws://
  sederhana.
- Autentikasi OBS WebSocket v5.
- Scene dan Source ditampilkan per kelompok Scene.
- Preview dan Program berdampingan dengan gambar screenshot berkala.
- Source visibility ON/OFF.
- Tombol PREVIEW, PROGRAM, Studio Mode, dan TAKE/Transition.
- Audio meter dari event OBS InputVolumeMeters.
- Mute per input, Mute All, serta pengaturan volume melalui slider.
- Status Stream/Record dibaca dari OBS lalu tombol Start/Stop dikirim.
- Gambar monitor dapat diperbesar melalui tampilan fullscreen.

CATATAN PENTING
- Gambar Preview, Program, dan Source adalah screenshot berkala melalui
  GetSourceScreenshot; bukan video live kontinu.
- Meter audio mengikuti event InputVolumeMeters dari OBS WebSocket.
- Sumber audio yang tampil mengikuti input audio yang terdeteksi dari OBS.
- Kompatibilitas runtime tetap harus diuji dengan versi OBS dan perangkat Android
  yang digunakan. Build berhasil tidak menggantikan uji koneksi langsung.

PENGATURAN OBS
1. OBS Studio > Tools > WebSocket Server Settings.
2. Aktifkan Enable WebSocket server.
3. Gunakan port 4455 dan password yang ditetapkan di OBS.
4. Pastikan HP dan laptop berada pada jaringan Wi-Fi/LAN yang sama.
5. Izinkan OBS Studio melalui Windows Firewall untuk jaringan Private.

BUILD APK MELALUI GITHUB
Workflow .github/workflows/build-apk.yml akan membangun APK debug saat push
ke branch main atau dijalankan melalui Actions > Build Android APK.
File hasil build:
app/build/outputs/apk/debug/app-debug.apk

Versi aplikasi: 2.0.0
Application ID: id.kominfo.sorong.sourcethumbnailremote
