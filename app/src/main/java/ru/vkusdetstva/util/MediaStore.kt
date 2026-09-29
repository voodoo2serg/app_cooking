package ru.vkusdetstva.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.util.UUID

object LocalMedia {
    fun copy(context: Context, uri: Uri): String {
        val folder = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(folder, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Фото недоступно" }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    }

    fun save(context: Context, bitmap: Bitmap): String {
        val folder = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(folder, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file.absolutePath
    }
}
