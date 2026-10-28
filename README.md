# 293 Reader

An Android ebook reader and physical book tracker. Fully offline: no network permission, no
analytics, no crash reporting. Books and the database are encrypted at rest with keys held in
Android Keystore.

Distributed through GitHub releases only, self-signed.

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Scaffold, theme and components, navigation shell, encrypted database, data model | Done |
| 2 | Import, library UI, folders | Done |
| 3 | PDF and comic readers (CBZ, CB7) | Done |
| 4 | EPUB and TXT readers | |
| 5 | Physical book tracking, reading log UI | |
| 6 | Locked folders, biometric/PIN, passphrase | |
| 7 | Performance pass, baseline profiles, release config | |

## Formats

PDF, EPUB, CBZ, CB7, TXT. Each format sits behind `reader:api` (`BookFormat`, `PagedDocument`,
`FlowDocument`), so MOBI, AZW3 or FB2 can be added later as new modules. CBR is not supported:
the only non-GPL RAR reader for the JVM covers RAR4 only and has a non-OSI license.

## Library

- **Import:** the system file picker (Storage Access Framework), several files at once. Each
  file is copied into app storage, encrypted on the way in. The format comes from the file's
  first bytes, not its name. A file already in the library (same SHA-256) is skipped, and a
  RAR comic (CBR) is refused with a note to convert it to CBZ. Import runs in the background
  and survives leaving the screen.
- **Folders:** nested to any depth. Create, rename, move (never into themselves) and delete.
  Deleting a folder deletes everything in it, files included, after a confirmation that says
  how much will go.
- **Books:** cover grid, sorted by title, last opened or date added. Long press a cover or a
  folder for its actions; tap a cover for the book screen.
- **Search:** title and author, every word as a prefix, ignoring case and accents ("brucke"
  finds "Brücke"). Books in locked folders never appear in results.
- **Covers:** stored encrypted and decoded at grid size. PDF and comic covers come from the
  first page; books in formats without a reader yet show the title and format. When a format
  gains a reader, books imported before it get their covers and page counts at the next start,
  keeping any title or author edited by hand.

## Reading (PDF, CBZ, CB7)

- **Page mode:** swipe, or tap the outer thirds, to turn; tap the middle for the controls. Taps
  jump straight to the page. Controls: a page scrubber, direction and pages on screen. With the
  controls hidden the system bars are hidden too.
- **Right to left:** per book. A comic whose ComicInfo.xml marks it as right-to-left manga opens
  that way; the page order, tap sides, swipe and scrubber all follow.
- **Two-page spreads:** One, Two, or Auto (two in landscape). The cover stands alone, then
  facing pairs, as in print.
- **Zoom:** pinch or double-tap, up to 5x. The page re-renders sharp once the fingers lift.
- **Position:** saved half a second after each turn and when the reader closes, reopened on
  the same page. Each sitting of 30 seconds or more goes to the reading log.
- **PDF:** the platform PdfRenderer, fed from a memfd (anonymous memory): the decrypted PDF is
  never written to disk, and costs RAM equal to its size while open. Password-protected PDFs
  are refused with a note to remove the password.
- **Comics:** pages are the archive's images in natural order ("page2" before "page10");
  macOS resource forks and hidden files are skipped. Entries are read in place through the
  decrypting channel. Solid 7z decodes front to back, so paging forward is cheap and recently
  read pages are cached for paging back.
- **Memory:** page bitmaps come from a shared pool (48 MB of idle bitmaps at most, freed when
  the app is backgrounded); rendering runs off the main thread; the next and previous screen
  render ahead.

## Build

Requirements: JDK 17 or newer, Android SDK with platform 37.

```
./gradlew assembleDebug                 # debug APK
./gradlew testDebugUnitTest             # JVM unit tests (Android modules)
./gradlew :core:model:test              # JVM unit tests (pure Kotlin module)
./gradlew connectedDebugAndroidTest     # instrumented tests, needs a device or emulator
```

### Release signing

1. Create a keystore: `keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias [alias]`
2. Copy `keystore.properties.example` to `keystore.properties` and fill in the `[bracketed]` values.
   Both files are gitignored; never commit them.
3. `./gradlew assembleRelease`

Without `keystore.properties` the release build still runs and produces an unsigned APK.

## Modules

```
build-logic/        convention plugins (android, compose, feature, hilt, room, jvm)
app/                application, activity, navigation shell, signing
core/model          pure Kotlin domain types
core/designsystem   the only home for design tokens: colours, Jost type scale, spacing,
                    sizes, shapes, motion, icons, and the 293 components
core/security       Keystore-backed Tink keys, file encryption, database key
core/database       Room on SQLCipher: entities, DAOs, schema exports in schemas/
core/data           repositories and settings
reader/api          format-neutral reader contract, bitmap pool
reader/archive      zip and 7z over a decrypting channel
reader/pdf          PDF through PdfRenderer
reader/comic        CBZ and CB7
feature/*           library, reader, physical (shelf), log, settings
```

## Design

The UI follows the 293 design system: `design/293-design-system.md` (rules) and
`design/293-tokens.json` (values). All tokens live in `core:designsystem`; no other module
hardcodes a colour, size or text style. `ColorTokensTest` fails if the Kotlin colours drift from
the JSON. Components: `Terrace` (face on a ledge), `SweepKeyCard`, `WindowPlate`,
`InlineControl`, `ReaderChips`, buttons, rows, `LiveStatus`, `ErrorNotice`, bottom bar. Previews
for all four themes are in `ComponentPreviews.kt`.

## Security

- **Files:** Tink StreamingAead (AES256_GCM_HKDF_1MB), keyset wrapped by an Android Keystore
  master key. Books and covers live in no-backup storage under random names; the file name is
  bound as associated data. Reads are seekable, so readers never write plaintext to disk.
  Files a crash leaves without a database row are swept at startup.
- **Database:** SQLCipher. A random 256-bit passphrase, wrapped by a Keystore-backed AEAD,
  stored in no-backup storage. The database opens off the main thread.
- **Backups:** disabled. Keystore keys cannot leave the device, so a backup could not be read
  anyway.
- **Search:** queries take the set of visible folders, so locked folders never appear in results.

## License

293 Free Use License v1.0, see `LICENSE` and `AI-NOTICE.md`. Third-party components and their
licenses: `THIRD_PARTY_NOTICES.md`.
