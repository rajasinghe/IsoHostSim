package lk.cba.isohostsim.compat

/**
 * Names the app used before it was renamed to POS Testing Platform.
 *
 * This is the only place the old name may appear. It exists so copies installed under the old name
 * keep their data: the settings folder and the saved-simulators file are moved to their new names
 * the first time the renamed app starts. Once every installed copy has been updated, [HOME_DIR] and
 * [SIMULATORS_NAME] and the code that reads them can be deleted.
 *
 * [KEYSHARE_SALT] is different: it is part of how protected key shares are derived from a PIN, so
 * changing it would make every share already handed out unreadable. It stays as a format constant.
 */
object LegacyNames {
    /** Old settings folder under the user's home. Now `.pos-testing-platform`. */
    const val HOME_DIR = ".iso8583studio"

    /** Old prefix of the saved-simulators file, `<prefix>_simulators.json`. */
    const val SIMULATORS_NAME = "Iso8583Studio"

    /** Prefix hashed with the PIN to derive a key share's protection key. */
    const val KEYSHARE_SALT = "iso8583studio-keyshare:"
}
