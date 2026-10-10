package com.zandaulion.bpdigitized.data.ocr

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** All inputs are synthetic. Never load stored readings or camera photos here. */
@RunWith(AndroidJUnit4::class)
class HearthOcrRecognizerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun installedAppHasNoNetworkPermissionOrAutomaticBackup() {
        val permissions = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertFalse(context.assets.list("hearth").orEmpty().any { it.endsWith(".js") || it.endsWith(".html") })
    }

    @Test fun nativeReaderRefusesBlankPhotosAndCanRepeatAndRetryAfterCancellation() = runBlocking {
        val reader = HearthOcrRecognizer(context)
        val bitmap = Bitmap.createBitmap(256, 384, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            repeat(2) {
                val result = reader.recognize(bitmap)
                assertNull(result.systolic)
                assertNull(result.diastolic)
                assertNull(result.pulse)
            }
            val scan = launch { reader.recognize(bitmap) }
            delay(10)
            scan.cancelAndJoin()
            assertNull(reader.recognize(bitmap).systolic)
        } finally {
            reader.close()
            bitmap.recycle()
        }
    }
}
