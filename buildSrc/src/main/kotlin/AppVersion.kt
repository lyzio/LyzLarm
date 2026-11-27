/**
 * App version from a release tag. The release workflow passes the tag (`v0.4.0`) as the
 * Gradle property `appVersion`; other builds use `anatole.version` in gradle.properties.
 *
 * Version code = major × 10000 + minor × 100 + patch, so 0.4.0 → 400, 0.4.1 → 401,
 * 1.0.0 → 10000. It only ever grows as versions grow (Google Play requires that),
 * as long as minor and patch stay below 100.
 */
object AppVersion {
    private val SEMVER = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    /** "v0.4.0" or "0.4.0" → "0.4.0". Throws for anything else. */
    fun name(tag: String): String {
        val m = SEMVER.matchEntire(tag.trim()) ?: throw IllegalArgumentException("Not a version like v0.4.0: '$tag'")
        val (major, minor, patch) = m.destructured
        return "${major.toInt()}.${minor.toInt()}.${patch.toInt()}"
    }

    fun code(tag: String): Int {
        val (major, minor, patch) = name(tag).split('.').map { it.toInt() }
        require(minor < 100 && patch < 100) { "Minor and patch must be below 100: '$tag'" }
        require(major < 2100) { "Major version too large for a version code: '$tag'" }
        return major * 10_000 + minor * 100 + patch
    }
}
