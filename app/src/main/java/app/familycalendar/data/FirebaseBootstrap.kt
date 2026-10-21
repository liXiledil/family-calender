package app.familycalendar.data

import android.content.Context
import app.familycalendar.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/** Starts Firebase from the identifiers in firebase.properties; null when this build has none (local-only mode). */
object FirebaseBootstrap {
    fun app(context: Context): FirebaseApp? {
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank()) return null
        FirebaseApp.getApps(context).firstOrNull()?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
        return FirebaseApp.initializeApp(context, options)
    }
}
