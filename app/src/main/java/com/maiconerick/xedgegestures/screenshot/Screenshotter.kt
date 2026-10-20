package com.maiconerick.xedgegestures.screenshot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.maiconerick.xedgegestures.TargetDisplay
import com.maiconerick.xedgegestures.root.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures the main display (plus the second one on dual-screen devices) via `screencap -d <id>` (root), then stitches them
 * side-by-side into a single PNG at Pictures/Screenshots/xedge_<ts>.png.
 *
 * If one display fails the other is still saved on its own.
 */
@Singleton
class Screenshotter @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun capture() {
        scope.launch {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val tmpMain = File("/sdcard/xedge_main_$ts.png")
            val tmpTarget = File("/sdcard/xedge_target_$ts.png")

            val mainOk = RootShell.exec("screencap -d 0 ${tmpMain.absolutePath}") && waitFor(tmpMain)
            val targetOk = TargetDisplay.isSecondary &&
                RootShell.exec("screencap -d ${TargetDisplay.id} ${tmpTarget.absolutePath}") &&
                waitFor(tmpTarget)

            val mainBmp = if (mainOk) safeDecode(tmpMain) else null
            val targetBmp = if (targetOk) safeDecode(tmpTarget) else null

            val stitched = stitch(mainBmp, targetBmp)
            tmpMain.delete(); tmpTarget.delete()

            if (stitched == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Screenshot failed (no display captured)", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val name = "xedge_$ts.png"
            val savedTo = saveToPictures(stitched, name)
            stitched.recycle()
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, "Saved $name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun waitFor(f: File, maxMs: Long = 1500): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < deadline) {
            if (f.exists() && f.length() > 0) return@withContext true
            Thread.sleep(30)
        }
        f.exists()
    }

    private fun safeDecode(f: File): Bitmap? =
        runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()

    private fun stitch(a: Bitmap?, b: Bitmap?): Bitmap? {
        if (a == null && b == null) return null
        if (a == null) return b
        if (b == null) return a
        val h = maxOf(a.height, b.height)
        val out = Bitmap.createBitmap(a.width + b.width, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(a, 0f, ((h - a.height) / 2f), null)
        canvas.drawBitmap(b, a.width.toFloat(), ((h - b.height) / 2f), null)
        return out
    }

    private fun saveToPictures(bmp: Bitmap, name: String): String {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots")
            }
            val uri = ctx.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
            ) ?: return "(mediastore insert failed)"
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            uri.toString()
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Screenshots",
            ).also { it.mkdirs() }
            val out = File(dir, name)
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out.absolutePath
        }
    }
}
