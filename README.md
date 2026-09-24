# Ruang Angklung

Aplikasi Android native berbahasa Indonesia, ditulis dengan **Java**. Desainnya terinspirasi gambar angklung bambu; pilihan nadanya mengikuti 15 rekaman angklung Bandung yang dilampirkan.

## Fitur

- **Main sendiri:** pilih 1–10 nada berbeda. Seluruh pilihan muncul di panel yang selalu terlihat di bagian bawah layar bermain; ketuk sebuah kotak untuk memilih nada aktif tanpa mengeluarkan suara. Goyangkan ponsel atau ketuk gambar angklung untuk membunyikan nada aktif.
- **Main bersama:** menu menampilkan kartu speaker dan pemain, pemilihan 1–3 nada lewat dialog, serta konfirmasi sebelum membuat/bergabung ke ruang. Satu ponsel menjadi **speaker ruang** tanpa memilih nada. Pemain mengirim peristiwa nada melalui Wi-Fi yang sama; seluruh suara digabung dan diputar **hanya pada ponsel speaker**. Koneksi pemain memeriksa speaker secara berkala dan mencoba menyambung ulang jika jaringan terputus sesaat. Jika gagal, ada pesan alasan dan tombol coba lagi. Ini tidak mengirim aliran audio/mikrofon.
- **Gerakan dan tempo:** giroskop dan sensor percepatan dipakai bersama bila tersedia, dengan pembacaan sekitar 100 Hz dan sampel suara dimuat sejak splash screen agar bunyi pertama lebih cepat terdengar. Laju sensor tidak memerlukan izin khusus untuk frekuensi di atas 200 Hz; bila ponsel menolak pendaftaran sensor, layar solo tetap terbuka dan gambar angklung masih bisa diketuk. Sensitivitas **0% mematikan bunyi otomatis dari gerakan**. Pada 1–100%, pengaturan rendah memerlukan goyangan lebih tegas dan membatasi *pengulangan* sekitar 320 ms, sedangkan 100% lebih peka dan bisa berbunyi lagi setelah 90 ms. Tidak ada jeda buatan sebelum bunyi pertama; latensi sebenarnya masih bergantung pada sensor, audio, dan Wi-Fi perangkat. Saat membuka Pengaturan, sensor berhenti sementara agar mengatur slider tidak membunyikan angklung.
- **Volume:** slider 0–100% hanya di menu Pengaturan (ikon roda gigi); berlaku untuk mode solo dan ponsel speaker. Volume media sistem ponsel juga memengaruhi keras suara akhirnya.
- **Audio offline:** 15 sampel nada angklung dari rekaman yang dilampirkan, dikonversi ke WAV mono dengan level relatif seragam. Nada 6 / La (A4) kini memakai pukulan G4 yang lebih bersih dari rekaman 74295, dinaikkan dua semiton. Rekaman asli A4 (74284) tetap disertakan sebagai sumber namun tidak diputar. Sampel WAV dapat dibuat ulang dari MP3 di `tools/recordings/` dengan `tools/prepare_recordings.py` (memerlukan FFmpeg). Perubahan sumber dan kredit ada di [ATTRIBUTION.md](ATTRIBUTION.md).
- **Tampilan ramah digunakan berbagai usia:** tema krem hangat, kartu permainan besar, panel nada tanpa geser samping saat bermain, animasi sentuh, ilustrasi angklung dan bambu, ornamen bambu dan anyaman, ikon angklung, dan splash screen beranimasi. Pilihan nada saat menyusun permainan tetap dalam tiga kolom yang mudah dibaca.
- Di beranda, nada aktif Do, Re, Mi, Fa, Sol, La, Si, Do↑ berganti tiap menit dalam label terpisah **di bawah ilustrasi angklung**, dengan jarak agar tulisan tidak menempel pada alat musik; gambarnya bergoyang saat nada berubah. **Hanya ketuk gambar angklung** untuk memainkan nada yang sedang tertulis: menggoyangkan ponsel tidak membunyikan angklung di beranda. Pergantian menit tidak membunyikan nada dengan sendirinya. Goyangan tetap bisa membunyikan nada saat bermain solo atau sebagai pemain ruang. Kotak pilihan nada tidak mengeluarkan suara; ponsel pemain di dalam ruang bersama tetap senyap.
- Tombol **Tentang Kami** berada di bagian bawah beranda. Halamannya berisi penjelasan teks tentang aplikasi, foto dan profil pengembang Ardra Priya Abyudaya, serta tombol YouTube, TikTok, dan GitHub. Gunakan tombol kembali pada header untuk kembali ke menu.
- **Nama nada dan not angka:** setiap nada menampilkan nama, angka, dan suku kata solmisasi. Do = C: `C4 (1 · Do)`, `D4 (2 · Re)`, `F#4 (4♯ · Fa♯)`, `A3 (6↓ · La↓)`, `C5 (1↑ · Do↑)`. Tanda ↓ berarti oktaf lebih rendah dari C4 dan ↑ berarti lebih tinggi. Label tampak di pilihan solo, pilihan pemain, layar bermain, dan speaker. Mode solo tetap maksimal 10 nada, pemain room 1–3 nada.
- Pilihan nada, nama perangkat, IP terakhir, sensitivitas, dan volume disimpan di perangkat. Tidak ada akun atau server cloud.

## Buka di Android Studio

1. Ekstrak ZIP, lalu pilih **File → Open** dan arahkan ke folder hasil ekstraksi yang **langsung berisi `settings.gradle`, `app`, dan `gradlew.bat`**.
2. Tunggu sinkronisasi Gradle. Gunakan **JDK 17** dan pasang **Android SDK Platform 34** bila Android Studio memintanya. Proyek memakai Android Gradle Plugin 8.5.2 dan Gradle 8.7.
3. Jalankan modul `app` pada ponsel Android 8.0/API 26 atau lebih baru. Emulator cocok untuk tombol sentuh; pengujian giroskop dan suara bersama paling baik pada ponsel fisik.
4. Untuk membuat APK dari Terminal Android Studio di Windows, jalankan `gradlew.bat :app:assembleDebug` di Command Prompt atau `.\gradlew.bat :app:assembleDebug` di PowerShell. Setelah **BUILD SUCCESSFUL**, APK berada di `app/build/outputs/apk/debug/app-debug.apk`.

`gradlew` dan `gradlew.bat` sudah tersedia. `gradle/wrapper/gradle-wrapper.jar` adalah bootstrap kecil dengan kode sumber di sebelahnya; saat pertama kali dipakai ia mengunduh distribusi Gradle 8.7 dan memeriksa SHA-256 distribusinya sebelum menjalankan Gradle. Sinkronisasi pertama memerlukan koneksi internet untuk Gradle, plugin Android, dan SDK.

## Cara mencoba ansambel

1. Pasang aplikasi pada **minimal dua ponsel** dan sambungkan ke **Wi-Fi yang sama**. Jaringan Wi-Fi publik yang memisahkan perangkat dapat menghalangi mode ini.
2. Pada ponsel yang akan berbunyi, pilih **Main bersama → Buat ruang speaker**. Ponsel ini tidak perlu memilih nada. Atur volume melalui ikon roda gigi di atas layar.
3. Di setiap ponsel pemain, ketuk **Pilih nada pemain** untuk menentukan 1–3 nada, isi alamat IP speaker, lalu tekan **Gabung sebagai pemain** dan konfirmasi. Port TCP dipasang otomatis pada **38245**.
4. Pemain memilih nada pada panel bawah tanpa mengirim bunyi. Goyangkan ponsel sambil menggenggamnya atau ketuk gambar angklung untuk mengirim nada aktif ke speaker. Suara semua pemain menyatu pada ponsel speaker; ponsel pemain tetap senyap.
5. **Ganti nada pemain** mengganti nada saat ruang berjalan. Jika koneksi tersendat, pemain mencoba menyambung kembali secara otomatis. Jika speaker sengaja keluar, pemain menerima pesan bahwa ruang ditutup. **Semua perangkat dalam room harus memakai versi 1.6.0 atau lebih baru** karena protokol ruang diperbarui.

Pilihan nada yang tersimpan dari versi sebelumnya menggunakan label dan rentang berbeda; aplikasi memulai pilihan baru dari `C4 (1 · Do)` apabila daftar lama tidak cocok.

## Jika muncul `prepareKotlinBuildScriptModel not found`

Kesalahan ini terjadi ketika Android Studio meminta tugas sinkronisasi Kotlin pada proyek Java. Proyek hanya menambahkan tugas kompatibilitas pada modul yang belum memilikinya; tugas tersebut tidak mengubah pembuatan APK.

1. Tutup proyek lama di Android Studio. Ekstrak ZIP ini ke **folder baru yang kosong**, jangan gabungkan dengan proyek lama atau folder `.idea` lama.
2. Pilih **File → Open** dan buka folder hasil ekstraksi yang berisi `settings.gradle` dan `gradlew.bat`. Jangan buka folder `app`, `build.gradle`, atau folder induk yang memiliki proyek Gradle lain.
3. Setelah sinkronisasi selesai, jalankan `.\gradlew.bat :app:assembleDebug` di PowerShell dari folder proyek. Periksa `app\build\outputs\apk\debug\app-debug.apk`.
4. Jika proyek lama masih tampak dua kali di panel Gradle, putuskan tautan proyek Gradle tambahan pada folder `app` dan buka kembali folder proyek utama.

Koneksi antarpemain berlangsung hanya selama aplikasi terbuka dan berada pada Wi-Fi lokal. Mode bersama lintas internet memerlukan layanan server tambahan; proyek ini tidak menyertakan server tersebut. Ponsel yang menggunakan VPN, hotspot, atau beberapa jaringan dapat menampilkan lebih dari satu IP; gunakan alamat jaringan yang dipakai bersama.

## Struktur

| Berkas | Peran |
| --- | --- |
| `app/src/main/java/id/ruangangklung/app/MainActivity.java` | Semua layar, pemilihan nada, alur bermain, animasi antarmuka |
| `SplashActivity.java`, `res/drawable/ic_angklung.xml` | Halaman pembuka animasi dan ikon aplikasi |
| `AngklungView.java` | Ilustrasi angklung responsif dan animasi goyang |
| `BambooMotifView.java` | Ornamen bambu dan pola anyaman di halaman utama serta header |
| `ShakeDetector.java`, `ShakeLogic.java`, `MotionTuning.java` | Giroskop, fallback sensor, sensitivitas, dan jeda antarbunyi |
| `LanRoom.java` | Speaker dan pemain TCP lokal, daftar peserta, pembatasan nada, dan peristiwa nada menuju speaker |
| `RuangAngklungApp.java`, `SoundEngine.java`, `res/raw/angklung_*.wav` | Sampel dimuat sejak splash dan diputar dengan SoundPool |
| `tools/recordings/`, `tools/prepare_recordings.py`, `ATTRIBUTION.md` | MP3 sumber, pengolah WAV, atribusi dan lisensi |
| `tests/RoomSmokeTest.java` | Uji logika dan ansambel dengan tiga koneksi lokal |

Untuk menjalankan uji bagian Java murni dengan JDK 17 dari folder proyek:

```bash
mkdir -p /tmp/ruang-angklung-test
javac -d /tmp/ruang-angklung-test \
  app/src/main/java/id/ruangangklung/app/NoteCatalog.java \
  app/src/main/java/id/ruangangklung/app/MotionTuning.java \
  app/src/main/java/id/ruangangklung/app/ShakeLogic.java \
  app/src/main/java/id/ruangangklung/app/LanRoom.java \
  tests/RoomSmokeTest.java
java -cp /tmp/ruang-angklung-test RoomSmokeTest
```

Android [motion sensor documentation](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion) describes gyroscope data in rad/s. Android [SoundPool documentation](https://developer.android.com/reference/android/media/SoundPool) covers short sound samples. Android's [local network guide](https://developer.android.com/privacy-and-security/local-network-permission) explains the LAN permission behavior for this project's target SDK.
