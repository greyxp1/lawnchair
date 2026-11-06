package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.iconoverride.IconImage
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.ImportedIcon
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.IconType
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SelectIconPreference"

@Composable
fun SelectIconPreference(componentKey: ComponentKey) {
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val label = remember(componentKey) {
        resolveAppLabel(context.requireSystemService(), componentKey)
    }
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val launcherAppState = LauncherAppState.getInstance(context)
    val model = launcherAppState.model

    val repo = IconOverrideRepository.INSTANCE.get(context)
    var pendingImage by remember { mutableStateOf<Pair<Bitmap, IconImage>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun applyIcon(item: IconPickerItem, image: IconImage? = null) {
        busy = true
        scope.launch {
            try {
                repo.setOverride(componentKey, item, image)
                // Refresh package icons while the model is still loaded; forceReload alone can
                // skip PackageUpdatedTask and leave icon-cache entries that look "fresh".
                // onAppIconChanged is @WorkerThread (blocking ShortcutManager query).
                withContext(Dispatchers.IO) {
                    model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
                }
                (context as Activity).let {
                    it.setResult(Activity.RESULT_OK)
                    it.finish()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not apply app icon", e)
                Toast.makeText(context, R.string.icon_import_failed, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
    OnResult<IconPickerItem> { applyIcon(it) }

    fun importImage(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            busy = true
            try {
                pendingImage = withContext(Dispatchers.IO) {
                    val bitmap = ImportedIcon.decode(context, uri)
                    bitmap to ImportedIcon.encode(bitmap)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not import app icon", e)
                Toast.makeText(context, R.string.icon_import_failed, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), ::importImage)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::importImage)

    val overrideItem by repo.observeTarget(componentKey).collectAsStateWithLifecycle(initialValue = null)
    val hasOverride = overrideItem != null

    PreferenceLayoutLazyColumn(label = label) {
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = true) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        scope.launch {
                            repo.deleteOverride(componentKey)
                            withContext(Dispatchers.IO) {
                                model.onAppIconChanged(
                                    componentKey.componentName.packageName,
                                    componentKey.user,
                                )
                            }
                            (context as Activity).let {
                                it.setResult(Activity.RESULT_OK)
                                it.finish()
                            }
                        }
                    },
                )
            }
        }
        preferenceGroupItems(
            count = iconPacks.size + 2,
            isFirstChild = !hasOverride,
            heading = { stringResource(id = R.string.pick_icon_from_label) },
        ) { index ->
            if (index < 2) {
                ClickablePreference(
                    label = stringResource(if (index == 0) R.string.icon_import_photos else R.string.icon_import_files),
                    onClick = {
                        if (!busy) {
                            if (index == 0) {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } else {
                                filePicker.launch(arrayOf("image/*"))
                            }
                        }
                    },
                )
                return@preferenceGroupItems
            }
            val iconPack = iconPacks[index - 2]
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                    if (iconPack.packageName.isEmpty()) {
                        navController.navigate(IconPicker())
                    } else {
                        navController.navigate(IconPicker(iconPack.packageName))
                    }
                },
            )
        }
    }
    pendingImage?.let { (bitmap, image) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingImage = null },
            title = { Text(label) },
            text = { Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(128.dp)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    applyIcon(IconPickerItem("", image.id, label, IconType.Image), image)
                }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

/**
 * Resolves a display label for [componentKey], including work/private profiles.
 *
 * [LauncherApps.resolveActivity] can return null or throw for non-current users; fall back to
 * [LauncherApps.getActivityList] and finally the activity class name.
 */
private fun resolveAppLabel(launcherApps: LauncherApps, componentKey: ComponentKey): String {
    val componentName = componentKey.componentName
    val user = componentKey.user
    try {
        val intent = Intent().setComponent(componentName)
        launcherApps.resolveActivity(intent, user)?.label?.toString()?.let { return it }
    } catch (t: Throwable) {
        Log.w(TAG, "resolveActivity failed for $componentKey", t)
    }
    try {
        val activities = launcherApps.getActivityList(componentName.packageName, user)
        activities
            .firstOrNull { it.componentName == componentName }
            ?.label
            ?.toString()
            ?.let { return it }
        activities
            .firstOrNull()
            ?.label
            ?.toString()
            ?.let { return it }
    } catch (t: Throwable) {
        Log.w(TAG, "getActivityList failed for $componentKey", t)
    }
    return componentName.shortClassName?.trimStart('.') ?: componentName.packageName
}
