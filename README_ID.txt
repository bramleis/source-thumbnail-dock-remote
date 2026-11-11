KOMSOS THUMBNAIL REMOTE — VERSI 2.1.0

Pembaruan berdasarkan pengujian:
1. Tombol dan kontrol diperkecil agar tampilan lebih ringkas.
2. Area gambar thumbnail sumber dipadatkan; nama sumber tetap terlihat.
3. Monitor Preview/Program diprioritaskan dalam antrean screenshot.
4. Refresh monitor dipercepat; thumbnail sumber diperbarui bergiliran agar beban OBS tidak menumpuk.
5. Ditambahkan keep-alive ringan melalui GetVersion berkala untuk membantu mencegah sesi WebSocket idle terputus.
6. Pembacaan level meter audio dibuat lebih toleran terhadap bentuk data level OBS.

PENTING:
- Thumbnail adalah screenshot berkala, bukan video live kontinu.
- Koneksi langsung ke OBS WebSocket v5, default port 4455.
- Tidak memerlukan Node.js, halaman HTML operator, atau SOURCE THUMBNAIL DOCK.
- Jangan menguji saat siaran penting. Uji dahulu ketika OBS tidak sedang dipakai untuk kegiatan.
- Jika OBS menampilkan pesan error saat mulai Record/Stream, jangan tekan tombol berulang kali. Periksa pesan OBS dan status output terlebih dahulu.
- Keep-alive membantu sesi idle, tetapi kestabilan Wi-Fi, pergantian access point, firewall, dan pengaturan hemat baterai Android tetap dapat memutus koneksi.

BUILD:
GitHub Actions akan membangun APK debug ketika file proyek ini diunggah ke repositori.
Artifact: KOMSOS-THUMBNAIL-REMOTE-APK
