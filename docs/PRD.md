# PRD — WA Broadcast Android (native APK)

Versi dokumen: 1.0 · Tanggal: 2026-11-14 · Status: siap dikerjakan
Repositori target: `E:\CODING\ANDROID`
Repositori sumber (webapp, source of truth perilaku): `E:\CODING\broadcast_reverse_whatsapp`
Dokumen ini ditulis untuk dieksekusi bertahap oleh agen (Antigravity) per **Work Package (WP)**.

---

## 1. Ringkasan

Port **native Android** dari webapp *WA Broadcast*. Aplikasi menyusun pesan WhatsApp bertemplate dengan `{{variabel}}`, mengisi nilai per kontak, lalu membuka chat WhatsApp yang sudah terisi lewat tautan `https://api.whatsapp.com/send?phone=...&text=...`. **Tidak ada backend. Tidak ada pengiriman otomatis.** User tetap menekan Send di WhatsApp.

Tujuan akhir: APK/AAB yang lolos review Google Play dan dipublikasikan, dengan perilaku + format data identik dengan versi web sehingga backup (JSON `schemaVersion: 5`) bisa saling ditukar antara web dan Android.

### 1.1 Sasaran terukur
| # | Sasaran | Bukti |
|---|---|---|
| G1 | Paritas fitur penuh dengan webapp | Semua item §6 + §7 terimplementasi, checklist lulus |
| G2 | Paritas data | Backup JSON Android bisa diimpor webapp dan sebaliknya, tanpa kehilangan field |
| G3 | Tanpa izin sensitif | `AndroidManifest.xml` tidak punya `<uses-permission>` sama sekali |
| G4 | 100% offline | Semua fitur jalan dengan mode pesawat; hanya buka WhatsApp butuh app WhatsApp |
| G5 | Publikasi Play Store | AAB ter-upload, melewati review, listing lengkap |
| G6 | Tes hijau | Unit test core (JVM) + smoke UI test lulus di CI lokal |

### 1.2 Non-goal (jangan dikerjakan)
- Tidak ada auto-send, pengiriman terjadwal, atau broadcast tanpa interaksi user.
- Tidak ada akun, login, cloud sync, statistik server, atau analytics.
- Tidak ada lampiran gambar/file (batasan link WhatsApp `text=`).
- Tidak ada pembacaan kontak HP otomatis (`READ_CONTACTS` tidak dipakai). Impor hanya lewat paste/CSV/VCF/pilih file.
- Tidak ada backend, Firebase, API remote, iklan.
- Tidak ada Jetpack Compose. Semua UI = **XML views**.

---

## 2. Keputusan teknis (terkunci)

| Area | Keputusan | Alasan |
|---|---|---|
| Nama app | **WhatsApp Cast** | Dipilih user |
| applicationId | **`net.myryzz.wacast`** | Dipilih user; permanen setelah publish |
| Bahasa | Kotlin | Sudah dipakai di repo |
| UI | XML layout + ViewBinding | Diminta user; hindari Compose |
| `minSdk` | **21** | Sudah di `app/build.gradle.kts`; Material Components + DayNight jalan di 21 |
| `targetSdk`/`compileSdk` | 36 | Syarat Play Store |
| AGP / Gradle | 9.2.1 / 9.9.0 | Sudah terbukti `BUILD SUCCESSFUL` |
| JDK | 21 (`Android Studio jbr`) | Sudah dipakai |
| Navigasi | **1 Activity + 5 Fragment** (add-once lalu show/hide, bukan replace) | Isolasi per WP, tidak ada dep baru; `androidx.fragment` sudah ikut `appcompat` |
| Modal | `DialogFragment` + `MaterialAlertDialogBuilder` bila cocok | Native, hemat kode |
| State bersama | Singleton `AppRepository` (pegang `AppState` + `StateStore`) + daftar listener (`addListener`/`notifyChanged`) | Tanpa dep `LiveData`; cukup beberapa baris |
| Persistensi data | 1 file JSON di `filesDir/wa-broadcast.json` | Menggantikan `localStorage`; bentuk JSON identik web (§4) |
| Preferensi | `SharedPreferences("wa-broadcast")` untuk bahasa, tema, emoji terakhir | Per-perangkat, seperti web |
| Serialisasi | `org.json` (bawaan platform) | Sudah dipakai; hanya `testImplementation` tambahan |
| i18n | Kamus in-app `core/I18n.kt` (EN + ID, 275 kunci) | Web ganti bahasa tanpa restart Activity; ikut bahasa app, bukan locale sistem |
| Tema | AppCompat DayNight + tema gelap manual | Paritas tombol tema web |
| Grafik | Resource vector drawable | Ikon web (`Icon.svelte`) dipetakan ke vector |

**Jangan lakukan:** menambah library navigasi, Room, Hilt, Retrofit, Compose, Firebase, atau WorkManager. Tidak ada kebutuhan yang menuntut itu.

---

## 3. Arsitektur & struktur paket

```
app/src/main/java/com/example/myapp/
  MainActivity.kt            # shell: Toolbar, overflow menu, BottomNavigationView, host fragment
  AppRepository.kt           # singleton: AppState, StateStore, listener, helper mutasi
  core/                      # LAPISAN MURNI — SUDAH ADA, tes hijau. JANGAN port ulang.
    Wa.kt  Vars.kt  Dates.kt  WaFormat.kt
    Contacts.kt  Groups.kt  TemplateGroups.kt
    Storage.kt  Json.kt  StateStore.kt  Clock.kt  Ids.kt
    I18n.kt                  # BARU (WP13)
  model/Models.kt            # LAPISAN MODEL — SUDAH ADA
  ui/templates/   TemplatesFragment.kt + item/layout
  ui/compose/     ComposeFragment.kt
  ui/preview/     PreviewFragment.kt
  ui/contacts/    ContactsFragment.kt
  ui/send/        SendFragment.kt
  ui/dialog/      AddContactDialog.kt, PasteDialog.kt, ContactEditDialog.kt,
                  AutoDateDialog.kt, TemplateGroupManagerDialog.kt,
                  ContactImportDialog.kt, BackupImportDialog.kt,
                  HelpDialog.kt, AboutDialog.kt, EmojiPickerDialog.kt
  ui/common/      ViewUtils.kt (toast/notice), LabelChip.kt, FormatBar.kt
app/src/main/res/
  layout/  menu/  drawable/  values/  values-night/  values-in/
```

Aturan lapisan:
- `core/` **murni** — tanpa `android.*` kecuali yang sudah ada di `Wa.kt` (`Uri.encode`) dan `StateStore.kt` (`File`). Jangan tambah ketergantungan Android di `core/`.
- `ui/` tidak menyimpan logika bisnis. Semua hitungan lewat `core/`/`AppRepository`.
- Setiap mutasi `AppState` lewat `AppRepository`, lalu `notifyChanged()` → fragment yang terlihat me-render ulang.

### 3.1 Kontrak `AppRepository` (tentukan di WP1, dipakai semua WP)
```kotlin
object AppRepository {
  lateinit var state: AppState
  fun init(filesDir: File, prefs: SharedPreferences)
  fun addListener(l: () -> Unit); fun removeListener(l: () -> Unit)
  fun notifyChanged()
  fun save()                    // StateStore.save(state) + jadwal debounce 200ms
  val lang: Lang                // dari prefs
  val theme: Theme              // dari prefs
  fun setLang(l: Lang); fun setTheme(t: Theme)
  val recentEmojis: List<String>
  fun pushRecentEmoji(e: String)
}
```
Debounce simpan 200 ms (sama seperti web `scheduleSave`) supaya tidak menulis file tiap ketikan.

---

## 4. Model data & paritas backup (WAJIB identik)

`SCHEMA_VERSION = 5`. Bentuk JSON export **harus sama persis** dengan web.

```json
{
  "schemaVersion": 5,
  "updatedAt": "2026-11-14T12:00:00.000Z",
  "groups": [
    { "id": "grp_ab12cd34", "name": "VIP", "parentId": null },
    { "id": "grp_ef56gh78", "name": "Jatuh Tempo", "parentId": "grp_ab12cd34" }
  ],
  "templateGroups": [
    { "id": "tgrp_xy90zw12", "name": "Usaha Roti", "parentId": null }
  ],
  "templates": [
    {
      "id": "tpl_1234abcd",
      "name": "Pengingat Pembayaran",
      "body": "Halo *{{nama}}*, total *Rp {{total}}*",
      "globals": { "total": "0" },
      "auto": { "bulan_ini": "monthName" },
      "vars": [ { "key": "total", "label": "Total Tagihan" } ],
      "groupId": "tgrp_xy90zw12",
      "createdAt": "2026-11-10T02:00:00.000Z",
      "updatedAt": "2026-11-14T12:00:00.000Z"
    }
  ],
  "contacts": [
    {
      "id": "c_qq11ww22",
      "phone": "6289698129722",
      "name": "FENDY",
      "labels": ["VIP", "Jatuh Tempo"],
      "values": { "tpl_1234abcd": { "total": "55161" } },
      "sentAt": { "tpl_1234abcd": "2026-11-14T12:05:00.000Z" }
    }
  ]
}
```

Aturan bentuk:
- `id` nominal: prefix `tpl_`, `c_`, `grp_`, `tgrp_` + 8 char base36 acak (`Ids.uid`).
- `groupId` template: id `templateGroups` atau `null`. Id menggantung → dinormalkan ke `null` saat `migrate`.
- `values` = peta `templateId → (key → String)`; `sentAt` = peta `templateId → ISO8601`.
- `auto[key]` hanya nilai dari daftar preset: `dateMonthYear`, `monthName`, `year`, `monthNum`, `monthYear`, `dayName`, `numericDate`.
- `phone` selalu hasil normalisasi (`62` + digit; `0…` → `62…`) dan harus lolos `/^62\d{6,13}$/`; kalau tidak, kontak dibuang saat `migrate`.
- Key `schemaVersion` > 5 → **tolak** (`warning: newer`).

### 4.1 Pemetaan storage web → Android
| Web (`localStorage`) | Android |
|---|---|
| `wa-broadcast:v1` (data) | `filesDir/wa-broadcast.json` |
| `wa-broadcast:v1:corrupt` | `filesDir/wa-broadcast.json.corrupt` |
| `wa-broadcast:lang` | `SharedPreferences` key `lang` (default `en`) |
| `wa-broadcast:theme` | `SharedPreferences` key `theme` (default `light`) |
| `wa-broadcast:recent-emojis` | `SharedPreferences` key `recent-emojis` (maks 16) |

Nama berkas export: `wa-broadcast-backup-YYYY-MM-DD.json`.

### 4.2 Migrasi `schemaVersion 1…4 → 5`
Wajib lulus, sama seperti web `migrate()`:
- `vars` → `[]`, `auto` → `{}` bila tidak ada; `labels` → `[]`; `groups` disinkronkan dari label kontak (`syncGroups`); `templateGroups` → `[]`, `groupId` → `null`.

### 4.3 Aturan merge (impor JSON, mode Merge)
Persis `mergeStates` di `core/Storage.kt`:
- Grup template & grup kontak: digabung **per nama**; grup baru mempertahankan induknya bila nama induk ketemu.
- Template: dicocokkan `id` lalu nama (case-insensitive, `trim`). Lokal menang; `vars` digabung per key (isi `label` kosong, tambah key baru); `globals` hanya key yang belum ada; `auto` hanya key yang belum ada.
- Kontak: dicocokkan nomor. Lokal menang; `name`/nilai kosong diisi dari file; `labels` union; `sentAt` ambil terbaru.
- Setelah merge: `syncGroups()`. `replaceAll` = ganti total.

---

## 5. Aturan bisnis inti (sudah ada di `core/`, jangan tulis ulang)

| Fungsi | Aturan |
|---|---|
| `Wa.normalizePhone` | buang non-digit; awalan `0` → `62` |
| `Wa.isValidPhone` | `^62\d{6,13}$` |
| `Wa.buildWaUrl` | `https://api.whatsapp.com/send?phone={phone}&text={encodeURIComponent(text)}` (encode ala JS, bukan `URLEncoder`!) |
| `Vars.detectVariables` | regex `\{\{\s*([\w.-]+)\s*\}\}`, unik, urut kemunculan |
| `Vars.resolve(key,…)` | urutan menang: nilai per-kontak → `contact.name` (khusus `nama`) → preset tanggal otomatis → `globals` → `""` |
| `Vars.templateVarKeys` | `vars[]` terdaftar dulu, lalu token di body, unik |
| `Dates.formatAutoDate` | locale `id-ID` / `en-US`; `monthNum` & `numericDate` selalu `dd`/`MM/yyyy` |
| `Contacts.parseCsv` | RFC 4180 (kutip), delimiter `,` `;` `\t` |
| `Contacts.parseVcf` | `TEL`, `FN`/`N`, baris terlipat |
| `Contacts.guessMapping` | header `phone/nomor/hp/wa`→Phone, `name/nama/fn`→Nama, `label/tag/grup/kategori`→Label, nama/key variabel→variabel |
| `Contacts.parsePasteRows` | pemisah `\|`, tab, atau `,`; kolom 1 nomor, 2 nama, sisanya variabel urut kemunculan di body (kecuali `nama`) |
| Multi-label dalam 1 sel | pisah `;` atau `\|` (bukan `,`) |
| `Groups.filterByLabels` | mode `and` (semua) / `or` (salah satu) |
| `labelHue(name)` | hash `h = (h*31 + charCode) % 360` → warna chip deterministik |
| Batas panjang URL | > 8000 char → peringatan |
| Batas ukuran file impor JSON | > 5 MB → tolak |

---

## 6. Peta layar (UI)

Adaptasi Android = **layout mobile web** saja (lebar ≤ 760 px). **Layout desktop web tidak diimplementasi.** Navigasi: `MaterialToolbar` + menu overflow + `BottomNavigationView` 5 tab (Template, Susun, Pratinjau, Kontak, Kirim). Urutan = sama web. Ikon Kirim tampilkan badge jumlah kontak.

Bilah **notice** (pesan error/sukses) = `Snackbar` atau banner di atas konten; auto-hilang 6 detik; bisa ditutup.

### 6.1 Shell (WP1)
- Toolbar: judul app + tagline; menu overflow: **Bahasa (EN/ID)**, **Tema (Terang/Gelap)**, **Ekspor**, **Impor**, **Impor CSV/VCF**, **Kontak**, **Bantuan**, **Tentang**. (Web punya toggle tema ikon di header; Android cukup di menu + state.)
- `BottomNavigationView`: 5 item; switch fragment show/hide ke-5 fragment yang sudah di-add; simpan `selectedTabId` di `savedInstanceState`.
- Menu Kontak membuka tab Kontak. Ekspor/Impor pakai SAF (§9).
- Izin: **tidak ada** `<uses-permission>`. Tidak ada `QUERY_ALL_PACKAGES`.

### 6.2 Layar Template (WP2)
- Daftar template dengan pohon grup (indentasi per `depth`), badge jumlah per grup, collapsible.
- Kotak cari (nama template atau nama grup) + filter grup: Semua grup / salah satu grup (+ subgrup) / Tanpa grup.
- **Template baru** dibuat di dalam grup yang sedang difilter. Delete = konfirmasi inline (Cancel/Delete), menghapus juga `values[id]` & `sentAt[id]` semua kontak.
- **Ganti drag-and-drop web** dengan menu `⋯ → Pindah ke…` (pilih grup) + `Jadikan tanpa grup`. Drag tidak wajib di Android; web sudah menyediakan jalur menu yang setara.
- Dibuat ulang: pengurutan grup A→Z (parents lalu descendants) = `groupOutline`.

### 6.3 Layar Susun (WP3)
- Picker template aktif (spinner/dialog), field **Nama**, picker **Grup template**, toolbar format (`*` `_` `~` `` ` ``) yang membungkus seleksi (`WaFormat.wrapMarker`), tombol emoji, `EditText` multiline untuk body.
- Panel **Variabel**: input tambah variabel (validasi `/^[\w.-]+$/`, tolak selain itu, `nama` tidak didaftarkan), chip `+ {{key}}` untuk sisip token di posisi kursor, baris per variabel: **key** (read-only), **Display name**, **Default** (`globals[key]`), tombol **Otomatis** (§6.4), **Hapus** (buang definisi + semua token di body).
- `{{nama}}` builtin: chip ada, tapi tidak masuk manajer variabel.

### 6.4 Modal Tanggal Otomatis (WP3)
Pilih 1 dari 7 preset + **Hapus** (kembali ke nilai manual). Tampilkan nilai terhitung saat ini, mengikuti bahasa app.

### 6.5 Layar Pratinjau (WP4)
- Teks hasil `render(body, contact, template, lang)` untuk kontak terpilih; picker kontak bila > 1.
- Peringatan bila ada variabel kosong (`emptyValueWarn`).
- Tombol **Copy** (clipboard) dan **Buka WhatsApp**.
- Pratinjau live mengikuti perubahan body/variabel/kontak.

### 6.6 Layar Kontak (WP5) — `ContactsView`
- Panel **Grup**: pohon grup/subgrup; buat grup, `⋯` untuk ganti nama / tambah subgrup / pindah / jadikan grup utama / pindah ke bawah / hapus. Hapus grup = buang label dari semua kontak, subgrup naik 1 tingkat. Ganti nama → ubah label semua kontak + bilah **Batalkan** (8 detik). Nama grup duplikat ditolak.
- Toolbar pilihan: **Tambah label** (subgrup otomatis menambah label induk via `expandLabels`), **Hapus label**, **Hapus terpilih**.
- Daftar kontak: cari (nama/nomor, highlight cocok), chip filter label (mode Semua/Salah satu, + jumlah + warna hue + breadcrumb induk), filter status (Semua / Belum terkirim / Sudah terkirim) relatif template aktif, mode pilih (checkbox) + Select all/Clear + bar "Terpilih saja".
- Baris kontak: badge `{n} kosong`, badge **Terkirim**, tombol **Atur**, tombol tandai/batalkan terkirim, hapus (dengan konfirmasi).
- **Ganti drag-and-drop** grup dengan menu pindah (sama seperti §6.2). Reuse `LabelFilter` logic.

### 6.7 Modal kelola per nomor (WP6) — `ContactEditDialog`
3 bagian: **Info kontak** (nomor divalidasi+dinormalkan saat simpan, nama), **Label** (chip, saran label, klik chip menambah; subgrup menambah induk), **Variabel template** (nilai khusus `values[templateId]`, placeholder = default/auto, tombol **Pakai bawaan**). Sisi pratinjau live + **Sebelumnya/Berikutnya** (nav dalam daftar scope) + **Buka WhatsApp**. Nomor tidak valid/duplikat ditolak saat simpan.

### 6.8 Modal Tambah Kontak (WP7)
Nomor + Nama + nilai variabel (hint = default/auto; placeholder `usingDefault`). Tombol **Tambah & lanjut** (tetap buka, reset form, badge jumlah) dan **Tambah**. Validasi nomor tidak valid/ganda → error pada field nomor.

### 6.9 Modal Tempel Baris (WP7)
Textarea multiline + tombol **Tambah kontak** (nonaktif bila kosong). Lewat `Contacts.parsePasteRows`. Ringkasan hasil `{ok}/{fail}`. Baris nomor tak valid digagalkan tanpa menghentikan sisanya.

### 6.10 Layar Kirim (WP8)
- Dua mode: **Daftar** (ringkas, klik baris = langsung buka WA; atau centang lalu "Terpilih saja") dan **Fokus** (satu per satu, pratinjau besar, kirim lalu lanjut).
- Toggle **Mode pilih**; Select all/Clear berlaku pada daftar yang sedang tampil.
- Filter label + status (per template aktif). Bagian **Sudah terkirim (n)** terlipat di bawah saat status = Semua.
- Membuka WA → `sentAt[templateId] = now`. Per baris ada toggle tandai/batalkan. Tombol **Atur** buka §6.7.
- Buka link: `Intent.ACTION_VIEW` ke URL `buildWaUrl(...)`. Tangani bila tak ada app penangan (toast). Peringatan bila URL > 8000 char.

### 6.11 Manajer Template Grup (WP11)
Modal: buat, ganti nama, tambah subgrup, pindah, hapus. Hapus grup → template di dalamnya pindah ke grup induk (subgrup naik 1 tingkat); tidak ada template terhapus. Reparent menolak self/descendant (`setTemplateGroupParent`).

### 6.12 Bantuan & Tentang (WP12)
- **Bantuan**: daftar item bantuan (dari i18n, paritas `helpItems`).
- **Tentang**: nama app, versi (dari `BuildConfig.VERSION_NAME`), tahun; ringkasan fitur; catatan **"bukan produk resmi WhatsApp/Meta"**; kredit pembuat (nama + link repo + email); tombol Tutup.

### 6.13 Emoji picker (WP3)
7 kategori (smileys, nature, food, travel, objects, symbols, flags), ~213 emoji, pencarian nama/kata kunci, riwayat 16 terakhir (prefs). Sisipkan di posisi kursor.

---

## 7. Bahasa & tema (WP13, WP14)

- 275 kunci, EN + ID, paritas 100% dengan `src/lib/i18n.svelte.js`. Ekstrak semua pasangan key→nilai dari file itu (jangan mengarang terjemahan).
- Interpolasi parameter: `{n}`, `{ok}`, `{fail}`, `{val}` → `I18n.t("pasteResult", "ok" to 3, "fail" to 1)`.
- Default **English** + tema **light**.
- Ganti bahasa/tema langsung tanpa restart Activity (`notifyChanged()`).
- Tanggal mengikuti bahasa app (bukan locale sistem): `id-ID` / `en-US`.
- `res/values/values-in/strings.xml` hanya untuk `app_name` bila perlu; seluruh teks UI dari `I18n.kt`.
- Tema gelap: `values-night/themes.xml` + atribut warna; pastikan kontras teks kedua tema.

---

## 8. Non-fungsional

| Aspek | Target |
|---|---|
| Offline | 100%, tanpa `INTERNET` permission |
| Izin | 0 runtime permission |
| Cold start | < 1,5 s di device menengah |
| Skala | 5.000 kontak tetap lancar → daftar pakai `RecyclerView`, jangan `ScrollView`+loop |
| Simpan | debounce 200 ms; gagal tulis → notice `storageFull` |
| Aksesibilitas | `contentDescription` semua tombol ikon, target sentuh ≥ 48 dp, fokus keyboard, `importantForAccessibility` untuk header |
| Robustness | File JSON korup → salin ke `.corrupt`, mulai state kosong, tampilkan `warnCorrupt`. `schemaVersion` lebih baru → `warnNewer`. Storage gagal baca → `warnStorage` |
| Lokalisasi angka | `tabular` untuk angka badge (monospace bila perlu) |

---

## 9. Berkas masuk/keluar (SAF, tanpa izin)

- **Ekspor JSON**: `ACTION_CREATE_DOCUMENT`, `type=application/json`, nama default `wa-broadcast-backup-YYYY-MM-DD.json`, isi = `JSON.stringify(state, null, 2)`.
- **Impor JSON**: `ACTION_OPEN_DOCUMENT` (`.json`). Validasi via `Storage.validateState` → tolak `newer`/`phone`/bukan JSON; > 5 MB tolak. Tampilkan dialog info jumlah (template/kontak/label/grup) + 2 tombol **Merge** / **Replace all** + **Ekspor dulu** + Batal. Replace all = konfirmasi tegas.
- **Impor CSV/VCF**: `ACTION_OPEN_DOCUMENT`, mime `text/csv`, `text/comma-separated-values`, `text/vcard`, `text/x-vcard`, `*/*`. Tampilkan layar pemetaan kolom + pratinjau 10 baris pertama + tombol Import (aktif hanya bila kolom Phone dipetakan) + ringkasan `{added}/{merged}/{bad}`.

---

## 10. Testing

- Unit test JVM untuk `core/` tetap dipertahankan (sekarang 77 tes hijau). Setiap WP yang menyentuh `core/` **wajib** menambah tes dan menjaga semuanya hijau.
- Setiap WP UI: minimal 1 instrumented smoke test (buka tab, aksi utama, cek state).
- Definisi selesai per WP: `gradlew :app:assembleDebug :app:testDebugUnitTest` sukses, `gradlew :app:connectedDebugAndroidTest` sukses pada emulator API 21 dan API 36 (bila ada device).
- Regresi wajib: round-trip export→import menghasilkan state setara; `normalizePhone` tabel di §5; `resolve` urutan prioritas.

---

## 11. Work Packages

Kerjakan berurutan; setiap WP harus berdiri sendiri (app tetap compile setelah WP selesai). Format tiap WP: **Scope · Deliverable · Selesai bila · Tes**.

> WP0–WP13 bergantung pada `core/` dan `model/` yang **sudah ada**. Jangan port ulang logika JS; panggil yang sudah ada.

### WP0 — Fondasi & rebranding
- Scope: nama app final, `applicationId` final, `versionName/Code`, ikon launcher (jangan pakai logo WhatsApp), warna tema (light+dark), `minSdk` final.
- Deliverable: `strings.xml`, `themes.xml`, `values-night/themes.xml`, `colors.xml`, aset ikon vector, `build.gradle.kts` final.
- Selesai bila: nama di launcher benar (`WhatsApp Cast`), aplikasi jalan 2 tema, tak ada teks "My APP"/"com.example.myapp" tersisa.
- Tes: smoke launch.

### WP1 — Shell
- Scope: `MainActivity` + toolbar + overflow menu + `BottomNavigationView` 5 tab; `AppRepository`; banner notice; tema & bahasa dari prefs; 5 fragment placeholder.
- Deliverable: §3.1 + §6.1; layout `activity_main.xml`.
- Selesai bila: 5 tab bisa dibuka, state tab bertahan saat rotasi, menu overflow lengkap (item yang belum ada menampilkan "segera"), ganti tema/bahasa langsung terlihat.
- Tes: instrumented — buka tiap tab, ganti bahasa, ganti tema.

### WP2 — Layar Template
- Scope: §6.2. Pakai pohon grup dari `TemplateGroups`, `filterTemplates`, search + filter grup, create/select/delete, menu pindah grup.
- Deliverable: `TemplatesFragment` + layout + item layout + `TemplateGroupManagerDialog` (§6.11, boleh jadi WP sendiri).
- Selesai bila: CRUD template + pengelompokan + pencarian + filter jalan; hapus template membersihkan `values`/`sentAt`.
- Tes: buat template baru di grup terfilter → tersimpan di file JSON dengan `groupId` benar.

### WP3 — Layar Susun
- Scope: §6.3, §6.4, §6.13. Editor + format bar + emoji picker + panel variabel + modal tanggal otomatis.
- Deliverable: `ComposeFragment`, `AutoDateDialog`, `EmojiPickerDialog`, `I18n` minimal untuk layar ini.
- Selesai bila: format membungkus seleksi, sisip variabel/emoji di kursor, `Hapus` variabel menghapus token di body, preset tanggal langsung mengubah placeholder di layar lain.
- Tes: `wrapMarker` lewat UI; tambah/hapus variabel.

### WP4 — Layar Pratinjau
- Scope: §6.5.
- Deliverable: `PreviewFragment`.
- Selesai bila: teks mengikuti kontak+bahasa; peringatan kosong tampil; Copy & Buka WhatsApp jalan.
- Tes: render kontak dengan `nama` + variabel terisi.

### WP5 — Layar Kontak
- Scope: §6.6.
- Deliverable: `ContactsFragment` + layout + `LabelChip`, list adapter, dialog buat/rename/hapus grup, dialog pilih label.
- Selesai bila: hierarki grup bisa dikelola, multi-select + tambah/hapus label, filter label (and/or) + cari + filter status, hapus terpilih.
- Tes: assign subgrup menambah label induk; hapus grup menaikkan subgrup.

### WP6 — Modal kelola per nomor
- Scope: §6.7.
- Deliverable: `ContactEditDialog`.
- Selesai bila: 3 bagian + pratinjau + prev/next + Buka WhatsApp + hapus; normalisasi nomor saat simpan; "Pakai bawaan" mengembalikan ke default.
- Tes: ubah nomor tidak valid ditolak; prev/next berpindah dalam scope.

### WP7 — Tambah & Tempel
- Scope: §6.8, §6.9.
- Deliverable: `AddContactDialog`, `PasteDialog`.
- Selesai bila: validasi nomor + dedup; "Tambah & lanjut" jalan; hasil paste `{ok}/{fail}`.
- Tes: paste 3 baris (1 ganda) → 2 ditambah, 1 gagal.

### WP8 — Layar Kirim
- Scope: §6.10.
- Deliverable: `SendFragment` (mode Daftar & Fokus).
- Selesai bila: mode pilih, terpilih saja, filter status, tandai/batal terkirim, bagian "Sudah terkirim" terlipat, buka WA menandai terkirim.
- Tes: buka WA → `sentAt[templateId]` terisi; toggle batal menghapus.

### WP9 — Impor CSV/VCF
- Scope: §9 (bagian CSV/VCF) + §6.6 tombol impor.
- Deliverable: `ContactImportDialog`.
- Selesai bila: pemetaan kolom + pratinjau 10 baris + Import nonaktif sampai Phone dipetakan + ringkasan hasil + merge sesuai aturan §4.3.
- Tes: CSV header tak dikenal → mapping di-guess; VCF multi `TEL`.

### WP10 — Ekspor/Impor JSON
- Scope: §9 (bagian JSON) + `BackupImportDialog`.
- Deliverable: handler SAF + dialog Merge/Replace + Ekspor dulu.
- Selesai bila: round-trip export→replace menghasilkan state sama; backup web bisa diimpor; backup Android bisa diimpor web.
- Tes: **uji paritas lintas platform** — ekspor dari webapp, impor di Android, cek jumlah & isi; lalu sebaliknya.

### WP11 — Manajer Template Grup
- Scope: §6.11.
- Deliverable: `TemplateGroupManagerDialog`.
- Selesai bila: CRUD + subgrup + pindah + hapus memindahkan template ke induk.
- Tes: hapus grup induk → template ke `null` grup, subgrup naik 1 tingkat.

### WP12 — Bantuan & Tentang
- Scope: §6.12.
- Deliverable: `HelpDialog`, `AboutDialog`, `helpItems` di `I18n.kt`.
- Selesai bila: paritas isi bantuan dengan web; versi dari `BuildConfig`; disclaimer non-afiliasi ada.
- Tes: buka dua dialog.

### WP13 — i18n lengkap (EN+ID)
- Scope: §7. Ekstrak **275** pasangan kunci dari `src/lib/i18n.svelte.js` ke `core/I18n.kt`.
- Deliverable: `I18n.kt` + penggantian semua string keras di UI.
- Selesai bila: hitung kunci = 275, tak ada string English/Indonesia keras di layout/kode, ganti bahasa mengubah seluruh layar.
- Tes: unit test — kunci EN dan ID sama himpunannya; `t()` mengembalikan kunci saat tidak ditemukan (fallback aman).

### WP14 — Tema gelap penuh
- Scope: §7 tema. Audit semua layar di dark.
- Deliverable: `values-night/themes.xml`, warna chip/badge/kartu untuk kedua tema.
- Selesai bila: tak ada teks tak terbaca; kontras AA.
- Tes: screenshot 5 tab di dua tema (manual/CI).

### WP15 — Polish, aksesibilitas, rilis
- Scope: §8, §10, §12.
- Deliverable: lint bersih, ikon final, keystore + AAB, listing Play.
- Selesai bila: §12 checklist lengkap; `bundleRelease` sukses & ter-upload.
- Tes: `lintDebug`, `bundleRelease`, uji internal di Play Console.

---

## 12. Kesiapan Google Play (checklist)

**Build & signing**
- [ ] `applicationId` final `net.myryzz.wacast`, `versionCode` naik, `versionName` sesuai.
- [ ] AAB release via **Play App Signing** (upload key lokal + app signing key Google); keystore upload disimpan aman (jangan commit).
- [ ] `targetSdk` 36; `minifyEnabled`/R8 hanya bila tes rilis lulus (saat ini `optimization.enable=false`; boleh tetap begitu).
- [ ] Tidak ada `uses-permission`; konfirmasi di AAB.

**Listing**
- [ ] Judul ≤ 30 char, deskripsi singkat ≤ 80, deskripsi penuh ≤ 4000, **dalam EN + ID**.
- [ ] Ikon 512×512, feature graphic 1024×500.
- [ ] Minimal 2 screenshot ponsel (saran 6), opsional tablet.
- [ ] Kategori: Tools/Productivity. Tanpa iklan.

**Kebijakan**
- [ ] **Privasi**: URL privacy policy wajib (app menangani nomor telepon + nama → data pribadi). Isi: data hanya di perangkat, tidak dikirim ke mana pun, tidak ada analytics.
- [ ] **Data safety**: "No data collected", "No data shared" (benar: hanya file lokal + buka tautan eksternal atas aksi user).
- [ ] **Content rating**: kuesioner → semua umur.
- [ ] **Konten sensitif/izin**: tidak ada.
- [ ] Tidak ada akun → tidak perlu alur hapus akun.

**Merek dagang (risiko tinggi — baca §13)**
- [ ] Ikon dan screenshot **tidak** memakai logo "WhatsApp"/Meta atau branding hijau yang menyiratkan afiliasi. Nama "WhatsApp Cast" dipakai atas keputusan user (risiko diterima); siap rename bila ditolak Play.
- [ ] Disclaimer "bukan produk resmi WhatsApp/Meta" ada di Tentang + deskripsi.
- [ ] Deskripsi menjelaskan app **tidak mengirim otomatis**; user menekan Send.

---

## 13. Risiko & keputusan yang dibutuhkan

**Risiko**
| Risiko | Dampak | Mitigasi |
|---|---|---|
| Merek "WhatsApp" di nama/ikon | Penolakan Play (Impersonation/IP) | Pakai nama netral; "WhatsApp" hanya di deskripsi sebagai kompatibilitas; jangan pakai logo hijau/telepon WA |
| Play anggap app "bulk messaging" otomatis | Penolakan / pembatasan | Jelaskan tanpa auto-send; tidak ada antrean/jadwal; wajib interaksi user |
| Backup web ↔ Android beda bentuk | Kehilangan data user | Uji paritas dua arah (WP10); jangan ubah `schemaVersion` |
| Izin `READ_CONTACTS` tak sengaja ditambah | Review ekstra + privasi | Larang; impor hanya manual |
| Data hilang saat update | Keluhan user | `migrate` defensif + salin `.corrupt` + ekspor manual |

**Keputusan terkunci (2026-11-14)**
| # | Keputusan | Nilai |
|---|---|---|
| 1 | Nama app | **WhatsApp Cast** (risiko merek diterima user; siap rename bila ditolak Play) |
| 2 | applicationId | **`net.myryzz.wacast`** |
| 3 | minSdk | **21** |
| 4 | Kredit Tentang | Ryan · `ryankosegeran2@gmail.com` · `https://github.com/ryankosegeran-byte/wa-cast.git` |
| 5 | Privacy policy | draf di `docs/privacy-policy.html` (EN+ID), user host sendiri |
| 6 | Signing | **Play App Signing** (upload key lokal + app signing key Google) |
| 7 | Share teks | **Tidak** (di luar paritas) |

Tidak ada blocker tersisa. WP0 siap mulai.

---

## 14. Status repo saat ini (jangan dikerjakan ulang)

Sudah selesai & terverifikasi di `E:\CODING\ANDROID`:

- `core/`: `Wa.kt`, `Vars.kt`, `Dates.kt`, `WaFormat.kt`, `Contacts.kt` (+ `parsePasteRows`), `Groups.kt`, `TemplateGroups.kt`, `Storage.kt`, `Json.kt`, `StateStore.kt`, `Clock.kt`, `Ids.kt`. **Port lengkap dari JS.**
- `model/Models.kt`: `TemplateVar`, `Template`, `Contact`, `Group`, `AppState`.
- 9 file unit test, **77 tes hijau**.
- `:app:assembleDebug` sukses; APK di `app/build/outputs/apk/debug/app-debug.apk`.
- Gradle wrapper 9.9.0, AGP 9.2.1, `viewBinding = true`, `minSdk 21`, `compileSdk` 36.1, `targetSdk 36`.
- Android Studio sync **berhasil** (log `idea.log` baris 10075: `Gradle sync finished in 7 m 42 s`).

Belum dikerjakan: seluruh `ui/` (WP0–WP15), `AppRepository`, `I18n.kt`, rebranding, rilis Play.

Artefak dokumen: `docs/PRD.md` (spesifikasi), `docs/privacy-policy.html` (draf EN+ID).
