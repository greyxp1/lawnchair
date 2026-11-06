package app.lawnchair.root

import android.app.INotificationManager
import android.content.Context
import android.os.PowerManager
import android.os.ServiceManager
import android.os.SystemClock

class RootHelperBackend(private val context: Context) : IRootHelper.Stub() {
    override fun goToSleep() {
        context.getSystemService(PowerManager::class.java).goToSleep(SystemClock.uptimeMillis())
    }

    @Synchronized
    override fun toggleNotifications(packageName: String, uid: Int): Boolean {
        require(context.packageManager.getPackagesForUid(uid)?.contains(packageName) == true)
        val manager = INotificationManager.Stub.asInterface(ServiceManager.getService(Context.NOTIFICATION_SERVICE))
        val wasEnabled = manager.areNotificationsEnabledForPackage(packageName, uid)
        // Change only the app-level switch, preserving individual channel settings.
        manager.setNotificationsEnabledForPackage(packageName, uid, !wasEnabled)
        val enabled = manager.areNotificationsEnabledForPackage(packageName, uid)
        check(enabled != wasEnabled) { "Notification permission is fixed" }
        return enabled
    }
}
