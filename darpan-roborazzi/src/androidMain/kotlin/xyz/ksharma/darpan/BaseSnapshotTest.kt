package xyz.ksharma.darpan

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import sergio.sastre.composable.preview.scanner.core.preview.getAnnotation

/**
 * Scans a package tree for `@Preview` composables that also carry [ScreenshotTest], and captures
 * each one across the configured theme and font-scale matrix.
 *
 * Subclass it from an `androidHostTest` source set:
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * @Config(sdk = [SnapshotDefaults.DEFAULT_SDK], qualifiers = SnapshotDefaults.DEFAULT_DEVICE, manifest = Config.NONE)
 * class MySnapshotTest : BaseSnapshotTest() {
 *     override val packageToScan = "com.example.app"
 *
 *     @Test fun generateScreenshots() = generateSnapshots()
 * }
 * ```
 *
 * Then `./gradlew :module:recordRoborazziAndroidHostTest` to capture, and
 * `verifyRoborazziAndroidHostTest` to check. The `Debug`-suffixed task names do not exist on a
 * KMP `androidLibrary` module.
 */
abstract class BaseSnapshotTest {

    /** Package tree to scan, e.g. `"xyz.ksharma.sumi"`. */
    abstract val packageToScan: String

    /** Output directory, relative to the module root. */
    open val screenshotsDir: String = "screenshots"

    open val includePrivatePreviews: Boolean = true

    open val lightModeFontScales: List<Float>
        get() = SnapshotDefaults.lightModeFontScales

    open val darkModeFontScales: List<Float>
        get() = SnapshotDefaults.darkModeFontScales

    open val testDarkMode: Boolean = true

    /**
     * Preview function names to skip. Robolectric hangs on composables driving infinite
     * animations, because the capture never sees a stable frame. Matched on the exact function
     * name the scanner reports.
     */
    open val excludedPreviewNames: Set<String> = emptySet()

    /** Call this from an `@Test` method. */
    protected fun generateSnapshots() {
        val scanner = AndroidComposablePreviewScanner()
            .scanPackageTrees(packageToScan)
            .includeAnnotationInfoForAllOf(ScreenshotTest::class.java)
            .includeIfAnnotatedWithAnyOf(ScreenshotTest::class.java)

        val scoped = if (includePrivatePreviews) scanner.includePrivatePreviews() else scanner

        // `@PreviewComponent` / `@PreviewScreen` expand to several `@Preview` variants so the IDE
        // shows them side by side. The scanner reports each variant separately, but the capture
        // below ignores every variant's own uiMode/fontScale and drives those itself — so all
        // variants of one function would render identical input. Keep one per function.
        val allPreviews = scoped.getPreviews()
            .distinctBy { "${it.declaringClass}#${it.methodName}" }
        val (skipped, previews) = allPreviews.partition { it.methodName in excludedPreviewNames }

        println("Found ${allPreviews.size} previews with @ScreenshotTest in $packageToScan")
        if (skipped.isNotEmpty()) {
            println("Skipping ${skipped.size} preview(s) via excludedPreviewNames:")
            skipped.forEach { println("  - ${it.methodName}") }
        }

        previews.forEach { capturePreviewSnapshots(it) }
    }

    private fun capturePreviewSnapshots(preview: ComposablePreview<AndroidPreviewInfo>) {
        val config = preview.getAnnotation<ScreenshotTest>()
        // 0.0 (the annotation default) means "no per-preview override", not "compare exactly".
        val tolerance = config?.threshold?.takeIf { it > 0.0 }?.toFloat()
            ?: SnapshotDefaults.DEFAULT_PIXEL_TOLERANCE

        // A font-scale-invariant preview renders identically at every scale, so capturing the
        // whole ramp would just store the same bytes several times over.
        val scaleOf: (List<Float>) -> List<Float> =
            if (config?.fontScaleSensitive == false) {
                { listOf(SnapshotDefaults.BASE_FONT_SCALE) }
            } else {
                { it }
            }

        val modes = buildList {
            add(false to scaleOf(lightModeFontScales))
            if (testDarkMode) add(true to scaleOf(darkModeFontScales))
        }

        modes.forEach { (isDarkMode, fontScales) ->
            fontScales.forEach { fontScale ->
                captureScreenshot(preview, fontScale, isDarkMode, tolerance)
            }
        }
    }

    private fun captureScreenshot(
        preview: ComposablePreview<AndroidPreviewInfo>,
        fontScale: Float,
        isDarkMode: Boolean,
        pixelTolerance: Float,
    ) {
        val fileName = buildScreenshotFileName(preview, fontScale, isDarkMode)
        println("Capturing: $fileName")

        val activityController = Robolectric.buildActivity(ComponentActivity::class.java)
        if (isDarkMode) applyDarkMode(activityController)
        activityController.setup()

        val activity = activityController.get()
        val composeView = ComposeView(activity).apply {
            setContent {
                ApplyPreviewEnvironment(fontScale, isDarkMode) { preview() }
            }
        }
        activity.setContentView(composeView)

        composeView.captureRoboImage(
            filePath = "$screenshotsDir/$fileName.png",
            roborazziOptions = SnapshotDefaults.roborazziOptions(pixelTolerance = pixelTolerance),
        )

        activityController.pause().stop().destroy()
    }

    private fun applyDarkMode(controller: ActivityController<ComponentActivity>) {
        val resources = controller.get().resources
        val config = Configuration(resources.configuration).apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or
                (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    @Composable
    private fun ApplyPreviewEnvironment(
        fontScale: Float,
        isDarkMode: Boolean,
        content: @Composable () -> Unit,
    ) {
        val customDensity = Density(
            density = LocalDensity.current.density,
            fontScale = fontScale,
        )
        val config = Configuration(LocalConfiguration.current).apply {
            uiMode = if (isDarkMode) {
                Configuration.UI_MODE_NIGHT_YES or (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
            } else {
                Configuration.UI_MODE_NIGHT_NO or (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
            }
        }

        CompositionLocalProvider(
            LocalDensity provides customDensity,
            LocalInspectionMode provides true,
            LocalConfiguration provides config,
            content = content,
        )
    }

    /** `{PreviewName}_{theme}_{fontScale}` — ASCII only, see `PreviewAnnotations.kt`. */
    private fun buildScreenshotFileName(
        preview: ComposablePreview<AndroidPreviewInfo>,
        fontScale: Float,
        isDarkMode: Boolean,
    ): String {
        val baseName = AndroidPreviewScreenshotIdBuilder(preview).ignoreClassName().build()
        val theme = if (isDarkMode) "dark" else "light"
        val scale = when (fontScale) {
            1.0f -> "normal"
            1.5f -> "large"
            2.0f -> "xlarge"
            else -> "scale_${fontScale}x"
        }
        return "${baseName}_${theme}_$scale"
    }
}
