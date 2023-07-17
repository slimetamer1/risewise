package com.better.alarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import com.better.alarm.background.PlayerWrapper
import com.better.alarm.configuration.globalLogger
import com.better.alarm.logger.Logger
import com.better.alarm.model.Alarmtone
import com.better.alarm.model.ringtoneManagerUri
import com.better.alarm.presenter.userFriendlyTitle

/** Checks if all ringtones can be played, and requests permissions if it is not the case */
fun checkPermissions(activity: Activity, tones: List<Alarmtone>) {
  if (Build.VERSION.SDK_INT >= 23 &&
      activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
          PackageManager.PERMISSION_GRANTED) {
    val logger: Logger by globalLogger("checkPermissions")

    val unplayable =
        tones
            .filter { alarmtone ->
              val uri = alarmtone.ringtoneManagerUri()
              uri != null &&
                  runCatching {
                        PlayerWrapper(
                                context = activity,
                                resources = activity.resources,
                                log = logger,
                            )
                            .setDataSource(uri)
                      }
                      .isFailure
            }
            .map { ringtone -> ringtone.userFriendlyTitle(activity) }

    if (unplayable.isNotEmpty()) {
      try {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.alert))
            .setMessage(
                activity.getString(
                    R.string.permissions_external_storage_text, unplayable.joinToString(", ")))
            .setPositiveButton(android.R.string.ok) { _, _ ->
              activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 3)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
      } catch (e: Exception) {
        logger.e("Was not able to show dialog to request permission, continue without the dialog")
        activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 3)
      }
    }
  }
}
