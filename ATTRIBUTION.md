# Kredit rekaman angklung

Rekaman sumber berasal dari paket [angklung oleh Parking Sun di Freesound](https://freesound.org/people/Parking%20Sun/packs/4799/), yang direkam di Bandung. Setiap rekaman berikut dilisensikan sebagai [Creative Commons Attribution 3.0 Unported (CC BY 3.0)](https://creativecommons.org/licenses/by/3.0/). Kredit kepada Parking Sun berlaku untuk **rekaman audio**, bukan untuk aplikasi secara keseluruhan.

MP3 sumber dari ZIP pengguna disimpan di `tools/recordings/`. Untuk pemakaian di aplikasi, MP3 tersebut dikonversi menjadi WAV mono 22.050 Hz / 16 bit, level puncak diseragamkan, dan diberi fade pendek pada awal/akhir. Sumber A4 (74284) memiliki bunyi gemeretak dan beberapa pukulan dalam satu rekaman; agar nada 6 terdengar seragam, A4 dalam aplikasi memakai rekaman G4 (74295) yang dinaikkan dua semiton. MP3 asli A4 tetap tersedia sebagai sumber, tetapi tidak dipakai untuk pemutaran A4. Beberapa nama MP3 dari ZIP tidak memuat karakter `#`; sumber Freesound berikut menetapkan nada yang benar. Tidak ada nada sintetis.

| Nada aplikasi | Not angka (Do = C) | Solmisasi | Rekaman asli |
| --- | --- | --- | --- |
| G3 | 5↓ | Sol↓ | [g3-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74294/) |
| A3 | 6↓ | La↓ | [a3-bandung-angklung vib F4-6](https://freesound.org/people/Parking%20Sun/sounds/74283/) |
| A#3 | 6♯↓ | La♯↓ | [a#3-bandung-angklung vib-1](https://freesound.org/people/Parking%20Sun/sounds/74282/) |
| B3 | 7↓ | Si↓ | [b3-bandung-angklung vib-1](https://freesound.org/people/Parking%20Sun/sounds/74285/) |
| C4 | 1 | Do | [c4-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74287/) |
| D4 | 2 | Re | [d4-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74289/) |
| E4 | 3 | Mi | [e4-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74291/) |
| F4 | 4 | Fa | [f4-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74293/) |
| F#4 | 4♯ | Fa♯ | [f#4-bandung-angklung vib-1](https://freesound.org/people/Parking%20Sun/sounds/74292/) |
| G4 | 5 | Sol | [g4-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74295/) |
| A4 | 6 | La | [G4, rekaman 74295, dinaikkan dua semiton](https://freesound.org/people/Parking%20Sun/sounds/74295/); [rekaman A4 asli 74284](https://freesound.org/people/Parking%20Sun/sounds/74284/) tetap di folder sumber |
| A#4 | 6♯ | La♯ | [a#4-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74281/) |
| B4 | 7 | Si | [b4-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74286/) |
| C5 | 1↑ | Do↑ | [c5-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74288/) |
| D5 | 2↑ | Re↑ | [d5-bandung-angklung vib](https://freesound.org/people/Parking%20Sun/sounds/74290/) |

Jalankan `python3 tools/prepare_recordings.py` dengan FFmpeg tersedia di PATH untuk membuat ulang 15 berkas WAV di `app/src/main/res/raw/`. Hak dan ketentuan lisensi rekaman asli tetap berlaku pada WAV yang sudah diproses.
