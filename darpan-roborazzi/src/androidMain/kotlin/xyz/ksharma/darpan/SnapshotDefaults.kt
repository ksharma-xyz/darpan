package xyz.ksharma.darpan

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * Defaults shared by every [BaseSnapshotTest]. Override per test class where a project needs
 * something different.
 */
object SnapshotDefaults {

    /** The unscaled baseline, and the only scale used for font-scale-invariant previews. */
    const val BASE_FONT_SCALE = 1.0f

    /** Font scales captured in light mode. 2x is the accessibility case that breaks layouts. */
    val lightModeFontScales = listOf(BASE_FONT_SCALE, 2.0f)

    /** Font scales captured in dark mode. */
    val darkModeFontScales = listOf(BASE_FONT_SCALE)

    const val DEFAULT_DEVICE = RobolectricDeviceQualifiers.Pixel6

    const val DEFAULT_SDK = 34

    /**
     * Per-pixel colour tolerance, as a normalised distance.
     *
     * Not a "how many pixels may differ" ratio - a per-pixel one. That distinction matters. The
     * same composable rendered on macOS and on a Linux CI runner produces the same geometry but
     * slightly different antialiasing: measured on this project, roughly 10% of pixels differ,
     * every one of them by at most 4/255 per channel. A changed-pixel-ratio threshold big enough
     * to absorb that (>0.10) would also wave through a genuinely moved element. A per-pixel
     * tolerance absorbs the antialiasing noise while a moved glyph, a colour change or a shifted
     * margin still blows straight past it.
     *
     * Raise this only with evidence from an actual diff, never to make a red build go green.
     */
    const val DEFAULT_PIXEL_TOLERANCE = 0.03f

    fun roborazziOptions(
        pixelTolerance: Float = DEFAULT_PIXEL_TOLERANCE,
        resizeScale: Double = 1.0,
    ) = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = pixelTolerance),
        ),
        recordOptions = RoborazziOptions.RecordOptions(
            resizeScale = resizeScale,
        ),
    )
}
