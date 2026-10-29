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
| 4 | EPUB and TXT readers | Done |
| 5 | Physical book tracking, reading log UI | Done |
| 6 | Locked folders, biometric/PIN, passphrase | Done |
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
  finds "Brücke"). Books in locked folders never appear in results, open or closed.
- **Covers:** stored encrypted and decoded at grid size. PDF and comic covers come from the
  first page, EPUB covers from the cover image the book names (else an image called cover, or
  its first full-page picture); TXT books, and EPUBs without a picture, show the title and
  format. When a format gains a reader, books imported before it get their covers and page
  counts at the next start, keeping any title or author edited by hand.
- **Locked folders:** long press a folder and choose Lock to protect it with a PIN (6 or
  more digits) or a passphrase (8 or more characters). A PIN only stops someone holding the
  phone; a passphrase is recommended. Fingerprint unlock can be added on top. Opening a locked
  folder asks for the secret; once open, the folder's actions add Lock now, Change PIN or
  passphrase, Fingerprint unlock and Remove lock. A closed folder offers only Unlock, and
  cannot be deleted or have books moved in or out. Locks do not nest: a folder inside a
  locked folder cannot be locked, and a locked folder cannot be moved into another one.
- **While a folder is closed** the library shows its name, how many books and folders it
  holds, and their sizes, formats, page counts, dates and reading sittings. Titles, authors,
  covers, subfolder names and the books themselves stay sealed.
- **Locking and unlocking** re-encrypts every book and cover under the folder's own key (or
  back under the app's key when the lock is removed), with progress in the library. Each book
  switches in one step, so a crash or a killed app leaves every book readable; the work
  resumes at the next start.
- **Closing:** open folders close on Lock now, when the screen turns off, and after the app
  has been in the background for the Lock after time (at once, 1, 5 or 15 minutes; 1 by
  default). A book open in the reader closes with its folder.
- **App lock** (Settings, Locks) puts the whole library behind a PIN or passphrase. Locking it
  ends the app, after the file being imported finishes, and it follows the Lock after time
  only, not screen off.
- **Forgotten secret:** there is no recovery. The books in that folder, or the whole library
  for the app lock, cannot be opened again. A closed folder cannot be deleted from the app;
  clearing the app's storage in Android settings removes everything.

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

## Reading (EPUB, TXT)

- **Layout:** the app lays the text out itself, a chapter at a time, to the screen and the text
  size (Small, Medium, Large, for every book). Structure comes from the markup (headings,
  paragraphs, quotes, lists, emphasis, pictures); the book's CSS is ignored, so every book
  reads in Jost on the theme's ground. Hyphenation follows the book's language.
- **Turning:** as in page mode: swipe, or tap the outer thirds; the middle shows the controls
  (progress scrubber, text size, contents). Turning past a chapter's last page carries on into
  the next. A right-to-left EPUB turns the other way.
- **Contents and links:** the EPUB 3 nav document, else the EPUB 2 NCX. Links inside the book
  jump to their target; links out of it are plain text, since the app has no network.
- **Position:** a chapter and a character offset, so it survives a change of text size or
  rotation. Progress is weighed by chapter length. Sittings go to the reading log as for PDFs,
  with progress in place of pages.
- **EPUB:** EPUB 2 and 3 through the app's own parser, read in place from the encrypted zip. DRM
  is refused (font obfuscation is fine). DTDs are never processed. A damaged chapter shows a
  note and the rest of the book still reads.
- **TXT:** UTF-8, UTF-16 with a byte order mark, else Windows-1252. Paragraphs come from blank
  lines, hard-wrapped lines are joined, and lines such as "Chapter 12" or "PART TWO" start
  chapters. Files over 16 MB are refused.

## Shelf (paper books)

- **Books:** title, author and page count (both optional), added by hand. The book being read
  sits on the key card with its page; the rest are grouped To read, Reading, Finished and Set
  aside, most recently touched first. Status can be changed from the book's screen.
- **Logging:** "Log reading" takes the pages read (from the bookmark on) and the minutes, as a
  sitting that ended now. The bookmark moves on; a sitting starts a book waiting to be read,
  and reaching the last page finishes it. Long press a sitting to delete it.
- Stored in the encrypted database with the ebooks. Deleting a book deletes its sittings.

## Log

- One list for ebooks and paper books: sittings the reader records (30 seconds or more) and
  sittings logged on the shelf, newest first, by day. A row opens its book.
- The key card shows hours read this week (from Monday) and today.
- Sittings with books in locked folders never appear.

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
core/security       Keystore-backed Tink keys, file encryption, database key, Argon2id
                    secret wraps, folder keyring, app lock, unlock rate limit
core/database       Room on SQLCipher: entities, DAOs, schema exports in schemas/
core/data           repositories and settings
reader/api          format-neutral reader contract, bitmap pool
reader/archive      zip and 7z over a decrypting channel
reader/pdf          PDF through PdfRenderer
reader/comic        CBZ and CB7
reader/epub         EPUB 2 and 3: OPF, nav/NCX and XHTML parsing
reader/txt          plain text: encoding detection, paragraphs and chapters
feature/*           library, reader, physical (shelf), log, settings, lock (unlock and
                    setup screens)
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
- **Search:** queries take the set of unlocked folders, and sealed books have no plain title
  or author to match, so locked folders never appear in results.
- **Locked folders:** each has its own Tink keysets (StreamingAead for files, AEAD for
  titles, authors, content hashes and subfolder names). The keysets are wrapped twice: first
  under a key derived from the PIN or passphrase with Argon2id (Bouncy Castle; 64 MiB, 3
  passes, 1 lane, random salt, parameters bound as associated data), then under a
  non-exportable Android Keystore AES-GCM key that belongs to that folder alone. Copying the
  app's data off the phone is not enough without the Keystore, and the Keystore is not enough
  without the secret. Fingerprint unlock is a separate wrap under a Keystore key that needs a
  strong biometric and is invalidated when a fingerprint is added; when that happens the PIN
  or passphrase still works and the app offers to set fingerprint up again. Sealed values are
  bound to their row, so they cannot be swapped between books. Changing the secret re-wraps
  the keysets only.
- **App lock:** the database passphrase and the app's file keyset are wrapped the same two
  ways, and their Keystore-only copies are deleted, so nothing can be read until the secret is
  entered. Locking it ends the process, so no key stays in memory.
- **Wrong guesses:** four are free, then each one waits 30 seconds, doubling up to an hour.
  The count survives restarts and clock changes. Nothing is wiped.
- **Screenshots:** locked folders, books in them and the unlock screens are kept out of
  screenshots and the recent apps view. Settings can block screenshots everywhere (off by
  default). With the app lock on, the recent apps view shows no picture of the app.
- **Leftovers:** the database deletes securely (`secure_delete`) and truncates its write-ahead
  log after sealing, so plain titles do not linger in free pages. Flash storage can still keep
  old blocks, and Java strings and Tink keys cannot be zeroed in memory.
- **Limits:** a PIN has a small key space; someone with the app's data and the phone's
  Keystore could guess it offline. Android may freeze a background app, so the Lock after
  time can run late until the app comes back, when it locks before showing anything.
  Duplicate detection does not see books inside locked folders, and covers and page counts
  for locked books are not backfilled while they are closed.

## License

293 Free Use License v1.0, see `LICENSE` and `AI-NOTICE.md`. Third-party components and their
licenses: `THIRD_PARTY_NOTICES.md`.
