package app.lawnchair.ui.popup

import android.content.pm.LauncherApps
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.root.RootHelperManager
import app.lawnchair.root.RootNotAvailableException
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationShortcut(
    launcher: LawnchairLauncher,
    item: ItemInfo,
    originalView: View,
) : SystemShortcut<LawnchairLauncher>(
    R.drawable.ic_toggle_notifications,
    R.string.toggle_notifications_action,
    launcher,
    item,
    originalView,
) {
    override fun onClick(view: View) {
        val packageName = mItemInfo.targetComponent?.packageName ?: return
        AbstractFloatingView.closeAllOpenViews(mTarget)
        mTarget.lifecycleScope.launch {
            val message = try {
                val service = RootHelperManager.INSTANCE.get(mTarget).getService()
                val enabled = withContext(Dispatchers.IO) {
                    val apps = mTarget.getSystemService(LauncherApps::class.java)
                    val app = apps.getApplicationInfo(packageName, 0, mItemInfo.user)
                    service.toggleNotifications(packageName, app.uid)
                }
                if (enabled) R.string.app_notifications_enabled else R.string.app_notifications_disabled
            } catch (e: CancellationException) {
                throw e
            } catch (_: RootNotAvailableException) {
                R.string.app_notifications_root_required
            } catch (e: Exception) {
                Log.w("NotificationShortcut", "Could not toggle notifications for $packageName", e)
                R.string.app_notifications_failed
            }
            Toast.makeText(mTarget, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        val FACTORY = SystemShortcut.Factory<LawnchairLauncher> { launcher, item, view ->
            if (item.itemType == ITEM_TYPE_APPLICATION && item.targetComponent != null) {
                NotificationShortcut(launcher, item, view)
            } else {
                null
            }
        }
    }
}
