package com.lambda.opusagenda.util

import android.webkit.MimeTypeMap
import java.util.Locale

enum class TaskAttachmentKind {
    IMAGE,
    VIDEO,
    AUDIO,
    FILE
}

object TaskContentSupport {

    fun classifyAttachment(mimeType: String?, fileName: String?): TaskAttachmentKind {
        val normalizedMime = normalizeMimeType(mimeType, fileName)
        return when {
            normalizedMime.startsWith("image/") -> TaskAttachmentKind.IMAGE
            normalizedMime.startsWith("video/") -> TaskAttachmentKind.VIDEO
            normalizedMime.startsWith("audio/") -> TaskAttachmentKind.AUDIO
            else -> TaskAttachmentKind.FILE
        }
    }

    fun normalizeMimeType(mimeType: String?, fileName: String?): String {
        val cleanMime = mimeType?.trim()?.lowercase(Locale.US)
        if (!cleanMime.isNullOrBlank()) {
            return cleanMime
        }

        val extension = fileName
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            .orEmpty()

        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?.lowercase(Locale.US)
            .orEmpty()
    }

    fun isPreviewableImage(mimeType: String?, fileName: String?): Boolean {
        return classifyAttachment(mimeType, fileName) == TaskAttachmentKind.IMAGE
    }
}
