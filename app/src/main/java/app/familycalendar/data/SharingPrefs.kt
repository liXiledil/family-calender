package app.familycalendar.data

import android.content.Context

/** What this phone has agreed to share and watch for; read by the background service too. */
class SharingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sharing", Context.MODE_PRIVATE)

    var familyId: String
        get() = prefs.getString("familyId", "").orEmpty()
        set(v) = prefs.edit().putString("familyId", v).apply()
    var uid: String
        get() = prefs.getString("uid", "").orEmpty()
        set(v) = prefs.edit().putString("uid", v).apply()
    /** Which family member this phone belongs to. */
    var personId: String
        get() = prefs.getString("personId", "").orEmpty()
        set(v) = prefs.edit().putString("personId", v).apply()
    var personName: String
        get() = prefs.getString("personName", "").orEmpty()
        set(v) = prefs.edit().putString("personName", v).apply()
    var shareLocation: Boolean
        get() = prefs.getBoolean("share", false)
        set(v) = prefs.edit().putBoolean("share", v).apply()
    var receiveAlerts: Boolean
        get() = prefs.getBoolean("alerts", false)
        set(v) = prefs.edit().putBoolean("alerts", v).apply()
}
