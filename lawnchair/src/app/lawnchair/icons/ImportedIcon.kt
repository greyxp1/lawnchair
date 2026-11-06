package app.lawnchair.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.drawable.toDrawable
import androidx.exifinterface.media.ExifInterface
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.iconoverride.IconImage
import com.caverock.androidsvg.SVG
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object ImportedIcon {
    private const val SIZE = 384
    private const val MAX_INPUT_BYTES = 10 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun decode(context: Context, uri: Uri): Bitmap {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_INPUT_BYTES) { "Image is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val target = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (max(options.outWidth, options.outHeight) / options.inSampleSize > SIZE * 2) {
                options.inSampleSize *= 2
            }
            val original = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            val exif = runCatching { ExifInterface(bytes.inputStream()) }.getOrNull()
            val matrix = Matrix().apply {
                if (exif?.isFlipped == true) postScale(-1f, 1f)
                postRotate(exif?.rotationDegrees?.toFloat() ?: 0f)
            }
            val image = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            canvas.drawBitmap(image, null, fit(image.width.toFloat(), image.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            if (image !== original) image.recycle()
            original.recycle()
        } else {
            SVG.setInternalEntitiesEnabled(false)
            val svg = SVG.getFromInputStream(bytes.inputStream())
            val ratio = svg.documentAspectRatio.takeIf { it.isFinite() && it > 0 } ?: 1f
            if (svg.documentViewBox == null && svg.documentWidth > 0 && svg.documentHeight > 0) {
                svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
            }
            svg.setDocumentWidth("100%")
            svg.setDocumentHeight("100%")
            svg.renderToCanvas(canvas, fit(ratio, 1f))
        }
        return target
    }

    fun encode(bitmap: Bitmap): IconImage {
        val png = ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }
        val id = MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
        return IconImage(id, png)
    }

    fun getDrawable(context: Context, id: String): Drawable? {
        val bitmap = cache[id] ?: runBlocking(Dispatchers.IO) {
            AppDatabase.INSTANCE.get(context).iconOverrideDao().getImage(id)?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
        }?.also { cache.put(id, it) } ?: return null
        return bitmap.toDrawable(context.resources)
    }

    private fun fit(width: Float, height: Float): RectF {
        val scale = SIZE / max(width, height)
        val w = width * scale
        val h = height * scale
        return RectF((SIZE - w) / 2, (SIZE - h) / 2, (SIZE + w) / 2, (SIZE + h) / 2)
    }
}
