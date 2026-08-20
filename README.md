# Darpan

दर्पण — *mirror*.

Snapshot testing for Kotlin Multiplatform Compose apps. Point it at a package, and it finds every
`@Preview` composable you have opted in, captures each across light/dark and a font-scale ramp,
and compares against committed baselines.

Built on [Roborazzi](https://github.com/takahirom/roborazzi),
[Robolectric](https://robolectric.org) and
[ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner).

## Modules

| Artifact | Source set | What it is |
|---|---|---|
| `darpan-annotations` | `commonMain`, all targets | Just `@ScreenshotTest`. Pure Kotlin, no test dependencies, safe in a production source set. |
| `darpan-roborazzi` | `androidMain` | The capture harness. Host-test classpath only. |

The split matters: the annotation has to be visible from the `commonMain` code where your
previews live, but the harness drags in Robolectric and belongs nowhere near production.

`darpan-roborazzi` is Android host-test code, not multiplatform. Only the annotation module is
genuinely KMP.

## Setup

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.roborazzi)   // must be in plugins {}, not only a dependency
}

kotlin {
    androidLibrary {
        withHostTest { isIncludeAndroidResources = true }
        androidResources { enable = true }   // if you ship composeResources
    }
    sourceSets {
        commonMain.dependencies {
            implementation("xyz.ksharma:darpan-annotations:<version>")
        }
        getByName("androidHostTest") {
            kotlin.srcDir("src/androidHostTest/kotlin")
            dependencies {
                implementation("xyz.ksharma:darpan-roborazzi:<version>")
            }
        }
    }
}
```

```kotlin
// src/androidHostTest/kotlin/.../MySnapshotTest.kt
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [SnapshotDefaults.DEFAULT_SDK],
    qualifiers = SnapshotDefaults.DEFAULT_DEVICE,
    manifest = Config.NONE,
)
class MySnapshotTest : BaseSnapshotTest() {
    override val packageToScan = "com.example.app"

    @Test fun generateScreenshots() = generateSnapshots()
}
```

Then annotate previews:

```kotlin
@ScreenshotTest
@Preview
@Composable
internal fun MyButtonPreview() { MyTheme { MyButton(...) } }
```

## Commands

| Action | Command |
|---|---|
| Record baselines | `./gradlew :module:recordRoborazziAndroidHostTest` |
| Verify | `./gradlew :module:verifyRoborazziAndroidHostTest` |
| Emit diff PNGs | `./gradlew :module:compareRoborazziAndroidHostTest` |

The `Debug`-suffixed names (`recordRoborazziDebug`) do **not** exist on a KMP `androidLibrary`
module. Using them fails with "task not found", which reads like a setup error but is not.

## Things this gets right that a hand-rolled setup usually does not

**Cross-platform comparison.** Baselines recorded on macOS do not match a Linux CI runner byte
for byte. The geometry is identical; the antialiasing is not. Measured on a real project: ~10% of
pixels differ, each by at most 4/255 per channel. A changed-pixel-ratio threshold large enough to
absorb that (>0.10) would also wave through a genuinely moved element. Darpan defaults to a
**per-pixel** tolerance (`SimpleImageComparator`) instead, which absorbs the antialiasing noise
while a moved glyph or a changed colour still fails. See `SnapshotDefaults.DEFAULT_PIXEL_TOLERANCE`.

**Multi-preview deduplication.** Annotations like `@PreviewComponent` expand to several `@Preview`
variants so the IDE shows them side by side. The scanner reports each variant separately, but the
harness drives theme and font scale itself, so every variant of one function renders identical
input. Darpan dedupes by `declaringClass#methodName`.

**Font-scale-invariant previews.** Composables that size text from layout rather than in sp — a
game board deriving glyphs from a cell size, fixed-size icons — render identically at every font
scale. `@ScreenshotTest(fontScaleSensitive = false)` captures those once instead of writing
byte-identical baselines per scale. On the project this was extracted from, that removed 11 of 27
baselines.

**Animations.** Composables driving infinite or entrance animations never settle under
Robolectric's frame clock, so the captured frame is arbitrary and the baseline is unstable. Do not
annotate them; list them in `excludedPreviewNames` and the run logs each skip by name so a stale
entry is visible.

## Baselines and Git LFS

PNGs are regenerated wholesale on any visual change, so track them with LFS:

```
# .gitattributes
**/screenshots/**/*.png filter=lfs diff=lfs merge=lfs -text
```

Watch for an unanchored `screenshots/` line in `.gitignore` — it will silently swallow the
baselines. Anchor it (`/screenshots/`) if it was meant for store-listing art.

CI must check out with `lfs: true`, or the baselines arrive as pointer files and every comparison
fails for a reason unrelated to the change under test.

## Non-ASCII preview names

Roborazzi derives the PNG file name from `@Preview(name = ...)`. A non-ASCII character there hangs
the capture on macOS and throws on Linux CI. Keep those names ASCII.

## License

Apache 2.0
