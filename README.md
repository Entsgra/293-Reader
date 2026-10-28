# 293 Reader

An Android ebook reader and physical book tracker. Fully offline: no network permission, no
analytics, no crash reporting. Books and the database are encrypted at rest with keys held in
Android Keystore.

Distributed through GitHub releases only, self-signed.

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Scaffold, theme and components, navigation shell, encrypted database, data model | Done |
| 2 | Import, library UI, folders | |
| 3 | PDF and comic readers (CBZ, CB7) | |
| 4 | EPUB and TXT readers | |
| 5 | Physical book tracking, reading log UI | |
| 6 | Locked folders, biometric/PIN, passphrase | |
| 7 | Performance pass, baseline profiles, release config | |

## Formats

PDF, EPUB, CBZ, CB7, TXT. Each format sits behind `reader:api` (`BookFormat`, `PagedDocument`,
`FlowDocument`), so MOBI, AZW3 or FB2 can be added later as new modules. CBR is not supported:
the only non-GPL RAR reader for the JVM covers RAR4 only and has a non-OSI license.

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
reader/api          format-neutral reader contract
feature/*           library, physical (shelf), log, settings
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
  master key. The file name is bound as associated data. Reads are seekable, so readers never
  write plaintext to disk.
- **Database:** SQLCipher. A random 256-bit passphrase, wrapped by a Keystore-backed AEAD,
  stored in no-backup storage. The database opens off the main thread.
- **Backups:** disabled. Keystore keys cannot leave the device, so a backup could not be read
  anyway.
- **Search:** queries take the set of visible folders, so locked folders never appear in results.

## License

293 Free Use License v1.0, see `LICENSE` and `AI-NOTICE.md`. Third-party components and their
licenses: `THIRD_PARTY_NOTICES.md`.
