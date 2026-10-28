# Third-party notices

293 Reader is licensed under the 293 Free Use License (see `LICENSE`). It ships the third-party
components below. None are under the GPL, AGPL or LGPL.

Keep this file current: whenever a dependency changes, regenerate the shipped list with

```
./gradlew -q -I scripts/runtime-dependencies.init.gradle :app:printRuntimeDependencies --no-configuration-cache
```

and update the tables. Full license texts: Apache-2.0 at https://www.apache.org/licenses/LICENSE-2.0,
the others below or in `licenses/`.

## Font

| Font | Source | License |
|---|---|---|
| Jost (variable, `core/designsystem/src/main/res/font/jost.ttf`) | The Jost Project Authors, https://github.com/indestructible-type/Jost | SIL Open Font License 1.1, full text in `licenses/OFL-Jost.txt` |

## Code compiled into native libraries

| Component | Inside | License |
|---|---|---|
| SQLite | `sqlcipher-android` | Public domain |
| LibTomCrypt 1.18.2 (SQLCipher's crypto provider) | `sqlcipher-android` | Public domain (The LibTom License) |
| Protocol Buffers (shaded) | `datastore-preferences-external-protobuf` | BSD-3-Clause |

## Shipped libraries (release runtime classpath)

| Group | Artifacts | Version | License |
|---|---|---|---|
| `androidx.activity` | activity, activity-compose, activity-ktx | 1.13.0 | Apache-2.0 |
| `androidx.annotation` | annotation, annotation-jvm | 1.10.0 | Apache-2.0 |
| `androidx.annotation` | annotation-experimental | 1.5.0 | Apache-2.0 |
| `androidx.arch.core` | core-common, core-runtime | 2.2.0 | Apache-2.0 |
| `androidx.autofill` | autofill | 1.0.0 | Apache-2.0 |
| `androidx.collection` | collection, collection-jvm, collection-ktx | 1.6.0 | Apache-2.0 |
| `androidx.compose` | compose-bom | 2026.09.00 | Apache-2.0 |
| `androidx.compose.animation` | animation, animation-android, animation-core, animation-core-android | 1.12.1 | Apache-2.0 |
| `androidx.compose.foundation` | foundation, foundation-android, foundation-layout, foundation-layout-android | 1.12.1 | Apache-2.0 |
| `androidx.compose.material` | material-ripple, material-ripple-android | 1.12.1 | Apache-2.0 |
| `androidx.compose.material3` | material3, material3-android | 1.4.0 | Apache-2.0 |
| `androidx.compose.runtime` | runtime, runtime-android, runtime-annotation, runtime-annotation-android, runtime-retain, runtime-retain-android, runtime-saveable, runtime-saveable-android | 1.12.1 | Apache-2.0 |
| `androidx.compose.ui` | ui, ui-android, ui-geometry, ui-geometry-android, ui-graphics, ui-graphics-android, ui-text, ui-text-android, ui-tooling-preview, ui-tooling-preview-android, ui-unit, ui-unit-android, ui-util, ui-util-android | 1.12.1 | Apache-2.0 |
| `androidx.concurrent` | concurrent-futures | 1.1.0 | Apache-2.0 |
| `androidx.core` | core-viewtree | 1.0.0 | Apache-2.0 |
| `androidx.core` | core, core-ktx | 1.19.1 | Apache-2.0 |
| `androidx.customview` | customview, customview-poolingcontainer | 1.0.0 | Apache-2.0 |
| `androidx.datastore` | datastore, datastore-android, datastore-core, datastore-core-android, datastore-core-okio, datastore-core-okio-jvm, datastore-preferences, datastore-preferences-android, datastore-preferences-core, datastore-preferences-core-android, datastore-preferences-proto | 1.3.0 | Apache-2.0 |
| `androidx.datastore` | datastore-preferences-external-protobuf | 1.3.0 | BSD-3-Clause |
| `androidx.emoji2` | emoji2 | 1.4.0 | Apache-2.0 |
| `androidx.fragment` | fragment | 1.5.1 | Apache-2.0 |
| `androidx.graphics` | graphics-path | 1.0.1 | Apache-2.0 |
| `androidx.hilt` | hilt-lifecycle-viewmodel, hilt-lifecycle-viewmodel-compose, hilt-navigation-compose | 1.4.0 | Apache-2.0 |
| `androidx.interpolator` | interpolator | 1.0.0 | Apache-2.0 |
| `androidx.lifecycle` | lifecycle-common, lifecycle-common-java8, lifecycle-common-jvm, lifecycle-livedata, lifecycle-livedata-core, lifecycle-livedata-core-ktx, lifecycle-process, lifecycle-runtime, lifecycle-runtime-android, lifecycle-runtime-compose, lifecycle-runtime-compose-android, lifecycle-runtime-ktx, lifecycle-runtime-ktx-android, lifecycle-viewmodel, lifecycle-viewmodel-android, lifecycle-viewmodel-compose, lifecycle-viewmodel-compose-android, lifecycle-viewmodel-ktx, lifecycle-viewmodel-savedstate, lifecycle-viewmodel-savedstate-android | 2.11.0 | Apache-2.0 |
| `androidx.loader` | loader | 1.0.0 | Apache-2.0 |
| `androidx.navigation` | navigation-common, navigation-common-android, navigation-compose, navigation-compose-android, navigation-runtime, navigation-runtime-android | 2.10.2 | Apache-2.0 |
| `androidx.navigationevent` | navigationevent, navigationevent-android, navigationevent-compose, navigationevent-compose-android | 1.1.2 | Apache-2.0 |
| `androidx.paging` | paging-common, paging-common-android | 3.3.2 | Apache-2.0 |
| `androidx.profileinstaller` | profileinstaller | 1.4.0 | Apache-2.0 |
| `androidx.room` | room-common, room-common-jvm, room-ktx, room-paging, room-paging-android, room-runtime, room-runtime-android | 2.8.5 | Apache-2.0 |
| `androidx.savedstate` | savedstate, savedstate-android, savedstate-compose, savedstate-compose-android, savedstate-ktx | 1.5.0 | Apache-2.0 |
| `androidx.sqlite` | sqlite, sqlite-android, sqlite-framework, sqlite-framework-android | 2.7.1 | Apache-2.0 |
| `androidx.startup` | startup-runtime | 1.1.1 | Apache-2.0 |
| `androidx.tracing` | tracing, tracing-android | 2.0.1 | Apache-2.0 |
| `androidx.versionedparcelable` | versionedparcelable | 1.1.1 | Apache-2.0 |
| `androidx.viewpager` | viewpager | 1.0.0 | Apache-2.0 |
| `androidx.window` | window, window-core, window-core-android | 1.5.0 | Apache-2.0 |
| `com.google.code.findbugs` | jsr305 | 3.0.2 | Apache-2.0 |
| `com.google.code.gson` | gson | 2.13.2 | Apache-2.0 |
| `com.google.crypto.tink` | tink-android | 1.24.0 | Apache-2.0 |
| `com.google.dagger` | dagger, dagger-lint-aar, hilt-android, hilt-core | 2.60.1 | Apache-2.0 |
| `com.google.errorprone` | error_prone_annotations | 2.41.0 | Apache-2.0 |
| `com.google.guava` | listenablefuture | 1.0 | Apache-2.0 |
| `com.squareup.okio` | okio, okio-jvm | 3.17.0 | Apache-2.0 |
| `jakarta.inject` | jakarta.inject-api | 2.0.1 | Apache-2.0 |
| `javax.inject` | javax.inject | 1 | Apache-2.0 |
| `net.zetetic` | sqlcipher-android | 4.20.0 | BSD-3-Clause |
| `org.jetbrains` | annotations | 23.0.0 | Apache-2.0 |
| `org.jetbrains.kotlin` | kotlin-stdlib | 2.4.21 | Apache-2.0 |
| `org.jetbrains.kotlinx` | atomicfu, atomicfu-jvm | 0.28.0 | Apache-2.0 |
| `org.jetbrains.kotlinx` | kotlinx-coroutines-android, kotlinx-coroutines-bom, kotlinx-coroutines-core, kotlinx-coroutines-core-jvm | 1.11.0 | Apache-2.0 |
| `org.jetbrains.kotlinx` | kotlinx-serialization-bom, kotlinx-serialization-core, kotlinx-serialization-core-jvm | 1.12.0 | Apache-2.0 |
| `org.jspecify` | jspecify | 1.0.0 | Apache-2.0 |

### SQLCipher for Android (BSD-3-Clause)

```
Copyright (c) 2008-2023, ZETETIC LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the ZETETIC LLC nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## Build and test tools (not shipped)

| Tool | License |
|---|---|
| Gradle 9.8.2 (wrapper) | Apache-2.0 |
| Android Gradle Plugin 9.4.1 | Apache-2.0 |
| Kotlin Gradle plugin, Compose compiler plugin, serialization plugin 2.4.21 | Apache-2.0 |
| KSP 2.3.12 | Apache-2.0 |
| Hilt Gradle plugin and compiler 2.60.1 | Apache-2.0 |
| Room compiler and Gradle plugin 2.8.5 | Apache-2.0 |
| Compose UI tooling (debug builds only) | Apache-2.0 |
| JUnit 4.13.2 | EPL-1.0 |
| Hamcrest 1.3 (via JUnit) | BSD-3-Clause |
| Truth 1.4.5 | Apache-2.0 |
| Turbine 1.2.1 | Apache-2.0 |
| kotlinx-coroutines-test 1.11.0 | Apache-2.0 |
| AndroidX Test runner 1.7.0, ext-junit 1.3.0 | Apache-2.0 |
