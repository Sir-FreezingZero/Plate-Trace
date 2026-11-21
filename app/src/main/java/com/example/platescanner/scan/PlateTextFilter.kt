package com.example.platescanner.scan

/** How strictly recognised text must look like a plate. Chosen in Settings. */
enum class FormatMode {
    /** Anything plate-like: 4–8 letters/digits with at least one digit. */
    ANY,

    /** US plates: 4–8 characters with both letters and digits (rejects phone numbers). */
    US,

    /** Only the chosen [PlateRules.masks]; look-alike characters are corrected to fit. */
    SPECIFIC,
}

/**
 * @property masks plate formats for [FormatMode.SPECIFIC], one character per plate character:
 * `L` = letter, `0` = digit, `*` = either. E.g. "LLL0000" matches ABC1234.
 */
data class PlateRules(
    val mode: FormatMode = FormatMode.US,
    val masks: List<String> = emptyList(),
)

/** Decides whether text read from a plate crop is a plate number, using the Settings rules. */
object PlateTextFilter {

    private val GENERIC = Regex("^[A-Z0-9]{4,8}$")
    private val MASK = Regex("^[L0*]{2,9}$")

    /** Current rules; updated from Settings, read on the analysis thread. */
    @Volatile var rules: PlateRules = PlateRules()

    /** Look-alike fixes used when a mask expects a letter but OCR read a digit, and vice versa. */
    private val DIGIT_TO_LETTER = mapOf(
        '0' to 'O', '1' to 'I', '2' to 'Z', '4' to 'A', '5' to 'S', '6' to 'G', '8' to 'B',
    )
    private val LETTER_TO_DIGIT = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1', 'Z' to '2', 'A' to '4',
        'S' to '5', 'G' to '6', 'B' to '8',
    )
    private const val MAX_CORRECTIONS = 2

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** Cleans up a user-typed mask; returns null if it isn't valid. */
    fun cleanMask(input: String): String? {
        val m = input.trim().uppercase().replace('O', '0')
        return if (MASK.matches(m)) m else null
    }

    /**
     * @return the plate number to use (possibly with look-alike characters corrected to fit a
     * format), or null if [plate] doesn't look like a plate under [rules].
     */
    fun accept(plate: String, rules: PlateRules = this.rules): String? {
        val digits = plate.count { it.isDigit() }
        val letters = plate.length - digits
        return when (rules.mode) {
            FormatMode.ANY -> {
                if (!GENERIC.matches(plate)) null
                else if (digits == 0) null                       // plain words: "STOP", "TAXI"
                else if (letters == 0 && plate.length < 5) null  // years, house numbers, clocks
                else plate
            }
            FormatMode.US -> {
                if (!GENERIC.matches(plate)) null
                else if (digits == 0 || letters == 0) null       // words; phone numbers
                else plate
            }
            // If several formats fit, prefer the one needing the fewest corrections.
            FormatMode.SPECIFIC -> rules.masks
                .mapNotNull { matchMask(plate, it) }
                .minByOrNull { fixed -> fixed.indices.count { fixed[it] != plate[it] } }
        }
    }

    /** Fits [plate] to [mask], fixing up to [MAX_CORRECTIONS] look-alike characters. */
    fun matchMask(plate: String, mask: String): String? {
        if (plate.length != mask.length) return null
        val out = StringBuilder(plate.length)
        var corrections = 0
        for (i in plate.indices) {
            val c = plate[i]
            val fixed: Char = when (mask[i]) {
                'L' -> if (c.isLetter()) c else DIGIT_TO_LETTER[c] ?: return null
                '0' -> if (c.isDigit()) c else LETTER_TO_DIGIT[c] ?: return null
                else -> c
            }
            if (fixed != c && ++corrections > MAX_CORRECTIONS) return null
            out.append(fixed)
        }
        return out.toString()
    }
}
