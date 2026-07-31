package xyz.ksharma.darpan

/**
 * Opts a `@Preview` composable into snapshot testing.
 *
 * The scanner finds previews by their `@Preview` annotation in the bytecode and then filters by
 * this one, so it must sit on a function that already carries `@Preview` - directly, or via a
 * multi-preview annotation whose own meta-annotations expand to `@Preview`. A function annotated
 * only with `@ScreenshotTest` is never found.
 *
 * ```
 * @ScreenshotTest
 * @Preview
 * @Composable
 * internal fun MyButtonPreview() {
 *     MyTheme { MyButton(text = "Continue", onClick = {}) }
 * }
 * ```
 *
 * Do not annotate previews that drive infinite or entrance animations. They never settle under
 * Robolectric's frame clock, so the captured frame is arbitrary and the baseline is unstable.
 * List those in `excludedPreviewNames` on the test class instead.
 *
 * This module is pure Kotlin with no test dependencies, so it is safe to depend on from a
 * production source set. The capture machinery lives in `darpan-roborazzi`, which belongs on the
 * host-test classpath only.
 *
 * @param threshold Comparison tolerance for this preview, 0.0 (exact match, the default) to 1.0.
 *   Prefer leaving this alone and setting a per-pixel tolerance globally - see
 *   `SnapshotDefaults.roborazziOptions`.
 * @param fontScaleSensitive Whether this preview's rendering responds to the system font scale.
 *   Set false for composables that size their text from layout rather than in sp - a game board
 *   that derives glyphs from a cell size, or fixed-size icons. Those render identically at every
 *   scale, so the harness captures them once instead of writing byte-identical baselines per
 *   scale.
 * @param description Free text for documentation and debugging.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ScreenshotTest(
    val threshold: Double = 0.0,
    val fontScaleSensitive: Boolean = true,
    val description: String = "",
)
