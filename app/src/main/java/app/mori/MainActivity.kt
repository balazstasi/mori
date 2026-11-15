package app.mori

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    // Read again on every resume, because the user turns it on in Settings and comes back.
    private val serviceOn = mutableStateOf(false)

    // Counts rise while the user scrolls in other apps, so the screen reads them again on each resume.
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            MoriTheme {
                SettingsScreen(
                    prefs = prefs,
                    serviceOn = serviceOn.value,
                    onTurnOn = ::openAccessibilitySettings,
                    onAppInfo = ::openAppInfo,
                    resumes = resumes.intValue,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn.value = isServiceEnabled()
        resumes.intValue++
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    // Android 11 and newer can open Mori's own page. Phones without it get the full list.
    private fun openAccessibilitySettings() {
        val service = ComponentName(this, GuardService::class.java).flattenToString()
        try {
            startActivity(Intent(ACTION_ACCESSIBILITY_DETAILS).putExtra(Intent.EXTRA_COMPONENT_NAME, service))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val info = it.resolveInfo.serviceInfo
            info.packageName == packageName && info.name == GuardService::class.java.name
        }
    }

    private companion object {
        const val ACTION_ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    }
}
