SOURCE THUMBNAIL DOCK REMOTE - PROYEK APK ANDROID

Paket ini adalah proyek Android dengan workflow GitHub Actions untuk membangun APK debug secara otomatis di cloud. Belum merupakan APK siap instal.

FITUR PROTOTIPE:
- Koneksi langsung ke OBS WebSocket v5.
- Input alamat IP, port, password.
- Pemindai QR Code dengan dukungan format JSON {"host":"...","port":4455,"password":"..."} dan ws://host:port.
- Daftar Scene; tombol set Program dan Preview.
- Daftar Source pada Scene yang dipilih dan toggle aktif/nonaktif.
- Status koneksi.

BATASAN YANG WAJIB DIKETAHUI:
- Prototipe ini belum menampilkan thumbnail/video live Preview dan Program.
- QR Code dari DeckPilot belum dapat dijamin kompatibel karena format QR-nya belum dianalisis. Jika QR hanya berisi host/port, password perlu diisi manual.
- Koneksi membutuhkan HP dan komputer OBS pada jaringan LAN/Wi-Fi yang sama, OBS WebSocket aktif, serta Windows Firewall mengizinkan port tersebut.
- Source toggle pada prototipe menggunakan nama Scene Program aktif untuk permintaan SetSceneItemEnabled; untuk source di scene lain, pilih Scene tersebut dulu dengan tombol Source lalu perlu perbaikan pengikatan scene pada versi berikutnya.
- Aplikasi belum diuji terhadap OBS fisik dan belum layak disebut versi operasional.

BUILD APK TANPA ANDROID STUDIO:
1. Buat repository GitHub baru.
2. Upload seluruh isi folder proyek ini ke repository.
3. Buka tab Actions dan jalankan workflow "Build Android APK" (atau push ke branch main).
4. Setelah selesai, buka hasil workflow dan unduh artifact SOURCE-THUMBNAIL-DOCK-REMOTE-APK.
5. Ekstrak artifact; file app-debug.apk dapat dipasang di Android dengan mengizinkan instalasi dari sumber tersebut.

Paket APK debug ini untuk pengujian internal. Untuk distribusi resmi diperlukan proses signing release.


PERBAIKAN V1.0.1
- Menambahkan eventSubscriptions OBS WebSocket agar pembaruan status Scene diterima.
- Menambahkan pemeriksaan requestStatus dari setiap perintah OBS.
- Menampilkan pesan jika OBS menolak perintah atau tidak memberi respons.
- Menambahkan batas waktu respons 7 detik agar kegagalan tidak terlihat seperti tombol tidak berfungsi.
- Fungsi lama dan tampilan utama dipertahankan.

CATATAN
Build APK harus dilakukan melalui GitHub Actions workflow "Build Android APK".
File APK lama tidak berubah otomatis saat kode sumber diperbarui.


PERBAIKAN V1.0.2 - SOURCE SCENE BINDING
- Setiap permintaan GetSceneItemList sekarang membawa identitas Scene yang diminta.
- Setiap tombol ON/OFF menggunakan nama Scene yang sama dengan daftar Source yang ditampilkan, bukan mengandalkan pilihan Scene global yang dapat berubah.
- Nama Scene ditampilkan di atas daftar Source agar pengguna dapat memeriksa Scene yang sedang dikendalikan.
- Fungsi Program dan Preview tetap dipertahankan.
- Ini adalah perbaikan kode sumber; APK harus dibangun ulang melalui GitHub Actions.
- Belum dapat dinyatakan lulus uji OBS fisik sebelum diuji pada komputer OBS pengguna.
