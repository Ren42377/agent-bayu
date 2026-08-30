# Agent Bayu

Asisten AI pribadi untuk mengelola aktivitas digital: tugas, kalender, pesan, dan integrasi lain yang kamu izinkan.

## Status

Fondasi Android Kotlin + Jetpack Compose sudah tersedia, termasuk registrasi sebagai asisten digital Android dan panel mengambang bergaya Gemini. Integrasi Google Tasks, Calendar, WhatsApp, Instagram, dan backend AI akan ditambahkan bertahap.

## Menjadi asisten default

1. Pasang aplikasi, buka tab Persiapan.
2. Tekan "Buka Pengaturan" lalu pilih Agent Bayu pada daftar Aplikasi asisten digital.
3. Panggil panel dengan geser dari sudut bawah layar (gesture navigation) atau menahan tombol home (navigasi tiga tombol).

Panel muncul di atas aplikasi yang sedang aktif tanpa izin overlay karena memakai jendela sesi asisten milik sistem (`VoiceInteractionService` + `VoiceInteractionSession`).

## Struktur

- `app/src/main/java/dev/agentbayu/app/assistant` registrasi asisten: voice interaction service, session service, session, recognition service stub, dan fallback activity untuk `ACTION_ASSIST`.
- `app/src/main/java/dev/agentbayu/app/domain` percakapan: repository in-memory dan mesin agent stub.
- `app/src/main/java/dev/agentbayu/app/platform` deteksi peran asisten default dan pengaturan aplikasi.
- `app/src/main/java/dev/agentbayu/app/ui` tema Material 3, komponen bersama, serta layar Chat, Persiapan, Pengaturan.

## Build lokal

Build otomatis berjalan di GitHub Actions dengan Gradle 8.11.1 dan cache dependency. Tidak ada
Gradle wrapper di repo ini, jadi versi Gradle ditentukan oleh workflow.

## Ambil APK debug

Setiap push (dan setiap `workflow_dispatch`) membangun APK debug, mengunggahnya sebagai artefak
bernama `agent-bayu-debug.apk` (berkas APK apa adanya, bukan zip), lalu memperbarui rilis `debug`.
Lint dan unit test berjalan setelah APK terunggah, jadi check yang gagal tidak menghalangi unduhan.

Unduh dari halaman Releases repo, atau lewat terminal:

```
gh release download debug --pattern agent-bayu-debug.apk
adb install --user 0 agent-bayu-debug.apk
rm agent-bayu-debug.apk
```

Artefak workflow hanya disimpan satu hari dan artefak APK lama dihapus di awal setiap run, jadi yang
tersedia selalu APK dari run terakhir. Rilis `debug` adalah jalur unduh yang tidak butuh login.
