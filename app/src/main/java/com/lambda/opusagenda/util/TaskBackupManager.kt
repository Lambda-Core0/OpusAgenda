package com.lambda.opusagenda.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.lambda.opusagenda.R
import com.lambda.opusagenda.data.AppDatabase
import com.lambda.opusagenda.data.TaskEntity
import com.lambda.opusagenda.notifications.TaskReminderScheduler
import com.lambda.opusagenda.widget.WidgetRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class TaskBackupManager(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context.applicationContext)
) {

    private val appContext = context.applicationContext
    private val taskDao = database.taskDao()
    private val reminderScheduler = TaskReminderScheduler(appContext)

    suspend fun exportBackup(outputUri: Uri): Int = withContext(Dispatchers.IO) {
        val tasks = taskDao.getAllTasksOnce()
        val resolver = appContext.contentResolver

        resolver.openOutputStream(outputUri)?.use { output ->
            ZipOutputStream(output.buffered()).use { zipStream ->
                val attachmentEntries = mutableMapOf<Int, String>()

                tasks.forEach { task ->
                    val rawAttachmentUri = task.attachmentUri?.takeIf(String::isNotBlank) ?: return@forEach
                    val attachmentEntry = buildAttachmentEntryName(task)
                    zipStream.putNextEntry(ZipEntry(attachmentEntry))
                    try {
                        resolver.openInputStream(Uri.parse(rawAttachmentUri))?.use { input ->
                            input.copyTo(zipStream)
                        } ?: throw TaskBackupException(
                            appContext.getString(
                                R.string.backup_attachment_export_failed,
                                task.attachmentName ?: task.text
                            )
                        )
                    } catch (exception: Exception) {
                        throw TaskBackupException(
                            appContext.getString(
                                R.string.backup_attachment_export_failed,
                                task.attachmentName ?: task.text
                            ),
                            exception
                        )
                    } finally {
                        zipStream.closeEntry()
                    }
                    attachmentEntries[task.id] = attachmentEntry
                }

                zipStream.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                zipStream.write(buildManifest(tasks, attachmentEntries).toString(2).toByteArray(Charsets.UTF_8))
                zipStream.closeEntry()
            }
        } ?: throw TaskBackupException(appContext.getString(R.string.backup_open_failed))

        tasks.size
    }

    suspend fun importBackup(inputUri: Uri): Int = withContext(Dispatchers.IO) {
        val parsedBackup = readBackupArchive(inputUri)
        val previousTasks = taskDao.getAllTasksOnce()
        val importedFiles = mutableListOf<File>()

        try {
            val importedTasks = parsedBackup.tasks.mapIndexed { index, task ->
                materializeImportedTask(
                    task = task,
                    attachmentFiles = parsedBackup.attachmentFiles,
                    importedFiles = importedFiles,
                    index = index
                )
            }

            database.withTransaction {
                taskDao.clearAll()
                if (importedTasks.isNotEmpty()) {
                    taskDao.insertAll(importedTasks)
                }
            }

            previousTasks.forEach { reminderScheduler.cancel(it.id) }
            importedTasks.forEach(reminderScheduler::sync)
            cleanupManagedAttachments(previousTasks)
            WidgetRefresh.notifyTaskWidgets(appContext)

            importedTasks.size
        } catch (exception: Exception) {
            importedFiles.forEach(File::delete)
            throw exception
        } finally {
            parsedBackup.tempDirectory.deleteRecursively()
        }
    }

    private fun readBackupArchive(inputUri: Uri): ParsedBackup {
        val resolver = appContext.contentResolver
        val tempDirectory = File(
            appContext.cacheDir,
            "task_backup_import_${System.currentTimeMillis()}"
        ).apply { mkdirs() }

        return try {
            var manifestContent: String? = null
            val extractedFiles = mutableMapOf<String, File>()

            resolver.openInputStream(inputUri)?.use { input ->
                ZipInputStream(input.buffered()).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val normalizedEntryName = normalizeEntryName(entry.name)
                            if (normalizedEntryName == MANIFEST_ENTRY_NAME) {
                                manifestContent = zipStream.readBytes().toString(Charsets.UTF_8)
                            } else {
                                val targetFile = resolveZipEntryFile(tempDirectory, normalizedEntryName)
                                targetFile.parentFile?.mkdirs()
                                targetFile.outputStream().use { output -> zipStream.copyTo(output) }
                                extractedFiles[normalizedEntryName] = targetFile
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            } ?: throw TaskBackupException(appContext.getString(R.string.backup_open_failed))

            val manifest = manifestContent ?: throw TaskBackupException(
                appContext.getString(R.string.backup_invalid_archive)
            )
            ParsedBackup(
                tasks = parseManifest(manifest),
                attachmentFiles = extractedFiles,
                tempDirectory = tempDirectory
            )
        } catch (exception: Exception) {
            tempDirectory.deleteRecursively()
            throw exception
        }
    }

    private fun parseManifest(manifestContent: String): List<BackupTask> {
        val manifest = try {
            JSONObject(manifestContent)
        } catch (exception: Exception) {
            throw TaskBackupException(appContext.getString(R.string.backup_invalid_archive), exception)
        }

        val version = manifest.optInt(KEY_FORMAT_VERSION, -1)
        if (version != BACKUP_FORMAT_VERSION) {
            throw TaskBackupException(appContext.getString(R.string.backup_unsupported_version))
        }

        val tasksArray = manifest.optJSONArray(KEY_TASKS)
            ?: throw TaskBackupException(appContext.getString(R.string.backup_invalid_archive))

        return buildList(tasksArray.length()) {
            for (index in 0 until tasksArray.length()) {
                val item = tasksArray.optJSONObject(index)
                    ?: throw TaskBackupException(appContext.getString(R.string.backup_invalid_archive))

                val attachmentEntry = item.optNullableString(KEY_ATTACHMENT_ENTRY)
                val attachmentName = item.optNullableString(KEY_ATTACHMENT_NAME)
                val attachmentMimeType = item.optNullableString(KEY_ATTACHMENT_MIME_TYPE)
                val hasAttachmentMetadata = !attachmentName.isNullOrBlank() || !attachmentMimeType.isNullOrBlank()
                if (hasAttachmentMetadata && attachmentEntry.isNullOrBlank()) {
                    throw TaskBackupException(appContext.getString(R.string.backup_invalid_archive))
                }

                add(
                    BackupTask(
                        id = item.optInt(KEY_ID),
                        text = item.optString(KEY_TEXT),
                        isCategory = item.optBoolean(KEY_IS_CATEGORY),
                        completed = item.optBoolean(KEY_COMPLETED),
                        importance = item.optInt(KEY_IMPORTANCE, 2),
                        parentId = item.optNullableInt(KEY_PARENT_ID),
                        sortOrder = item.optLong(KEY_SORT_ORDER, 0L),
                        expanded = item.optBoolean(KEY_EXPANDED, true),
                        dueDate = item.optNullableLong(KEY_DUE_DATE),
                        description = item.optNullableString(KEY_DESCRIPTION),
                        link = item.optNullableString(KEY_LINK),
                        pinned = item.optBoolean(KEY_PINNED, false),
                        reminderAt = item.optNullableLong(KEY_REMINDER_AT),
                        repeatAmount = item.optNullableInt(KEY_REPEAT_AMOUNT),
                        repeatUnit = item.optNullableString(KEY_REPEAT_UNIT),
                        createdAt = item.optLong(KEY_CREATED_AT, System.currentTimeMillis()),
                        attachmentEntry = attachmentEntry,
                        attachmentName = attachmentName,
                        attachmentMimeType = attachmentMimeType
                    )
                )
            }
        }
    }

    private fun materializeImportedTask(
        task: BackupTask,
        attachmentFiles: Map<String, File>,
        importedFiles: MutableList<File>,
        index: Int
    ): TaskEntity {
        val materializedAttachment = task.attachmentEntry?.let { attachmentEntry ->
            val extractedFile = attachmentFiles[normalizeEntryName(attachmentEntry)]
                ?: throw TaskBackupException(appContext.getString(R.string.backup_invalid_archive))
            val finalFile = createImportedAttachmentFile(index, task.attachmentName)
            extractedFile.copyTo(finalFile, overwrite = true)
            importedFiles += finalFile

            val fileUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                finalFile
            )
            MaterializedAttachment(
                uri = fileUri.toString(),
                name = task.attachmentName ?: finalFile.name,
                mimeType = TaskContentSupport.normalizeMimeType(
                    task.attachmentMimeType,
                    task.attachmentName ?: finalFile.name
                )
            )
        }

        return TaskEntity(
            id = task.id,
            text = task.text,
            isCategory = task.isCategory,
            completed = task.completed,
            importance = task.importance,
            parentId = task.parentId,
            sortOrder = task.sortOrder,
            expanded = task.expanded,
            dueDate = task.dueDate,
            description = task.description,
            link = task.link,
            attachmentUri = materializedAttachment?.uri,
            attachmentName = materializedAttachment?.name,
            attachmentMimeType = materializedAttachment?.mimeType,
            pinned = task.pinned,
            reminderAt = task.reminderAt,
            repeatAmount = task.repeatAmount,
            repeatUnit = task.repeatUnit,
            createdAt = task.createdAt
        )
    }

    private fun cleanupManagedAttachments(tasks: List<TaskEntity>) {
        tasks.asSequence()
            .mapNotNull { managedAttachmentFileFor(it.attachmentUri) }
            .distinctBy { it.absolutePath }
            .forEach { file ->
                runCatching { file.delete() }
            }
    }

    private fun managedAttachmentFileFor(rawUri: String?): File? {
        if (rawUri.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        if (uri.authority != "${appContext.packageName}.fileprovider") return null

        val encodedPath = uri.encodedPath ?: return null
        val marker = "/task_attachments/"
        val markerIndex = encodedPath.indexOf(marker)
        if (markerIndex < 0) return null

        val relativeName = Uri.decode(encodedPath.substring(markerIndex + marker.length))
        val candidate = File(attachmentDirectory(), relativeName).canonicalFile
        val baseDirectory = attachmentDirectory().canonicalFile
        return if (candidate.path.startsWith(baseDirectory.path + File.separator) && candidate.exists()) {
            candidate
        } else {
            null
        }
    }

    private fun createImportedAttachmentFile(index: Int, attachmentName: String?): File {
        val baseName = sanitizeFileName(attachmentName ?: "attachment.bin")
        val targetDirectory = attachmentDirectory().apply { mkdirs() }
        return File(
            targetDirectory,
            "import_${System.currentTimeMillis()}_${index}_$baseName"
        )
    }

    private fun attachmentDirectory(): File {
        return File(appContext.filesDir, "task_attachments")
    }

    private fun buildManifest(
        tasks: List<TaskEntity>,
        attachmentEntries: Map<Int, String>
    ): JSONObject {
        return JSONObject().apply {
            put(KEY_FORMAT_VERSION, BACKUP_FORMAT_VERSION)
            put(KEY_EXPORTED_AT, System.currentTimeMillis())
            put(
                KEY_TASKS,
                JSONArray().apply {
                    tasks.forEach { task ->
                        put(
                            JSONObject().apply {
                                put(KEY_ID, task.id)
                                put(KEY_TEXT, task.text)
                                put(KEY_IS_CATEGORY, task.isCategory)
                                put(KEY_COMPLETED, task.completed)
                                put(KEY_IMPORTANCE, task.importance)
                                put(KEY_PARENT_ID, task.parentId)
                                put(KEY_SORT_ORDER, task.sortOrder)
                                put(KEY_EXPANDED, task.expanded)
                                put(KEY_DUE_DATE, task.dueDate)
                                put(KEY_DESCRIPTION, task.description)
                                put(KEY_LINK, task.link)
                                put(KEY_PINNED, task.pinned)
                                put(KEY_REMINDER_AT, task.reminderAt)
                                put(KEY_REPEAT_AMOUNT, task.repeatAmount)
                                put(KEY_REPEAT_UNIT, task.repeatUnit)
                                put(KEY_CREATED_AT, task.createdAt)
                                put(KEY_ATTACHMENT_ENTRY, attachmentEntries[task.id])
                                put(KEY_ATTACHMENT_NAME, task.attachmentName)
                                put(KEY_ATTACHMENT_MIME_TYPE, task.attachmentMimeType)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun buildAttachmentEntryName(task: TaskEntity): String {
        val safeName = sanitizeFileName(task.attachmentName ?: "attachment.bin")
        return "attachments/${task.id}_$safeName"
    }

    private fun sanitizeFileName(fileName: String): String {
        val sanitized = fileName
            .replace("\\", "_")
            .replace("/", "_")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
        return sanitized.ifBlank { "attachment.bin" }
    }

    private fun resolveZipEntryFile(baseDirectory: File, entryName: String): File {
        val normalizedName = normalizeEntryName(entryName)
        val candidate = File(baseDirectory, normalizedName).canonicalFile
        val canonicalBase = baseDirectory.canonicalFile
        if (!candidate.path.startsWith(canonicalBase.path + File.separator)) {
            throw TaskBackupException(appContext.getString(R.string.backup_invalid_archive))
        }
        return candidate
    }

    private fun normalizeEntryName(entryName: String): String {
        return entryName.replace('\\', '/')
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (isNull(key)) null else optString(key).ifBlank { null }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        return if (isNull(key)) null else optInt(key)
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        return if (isNull(key)) null else optLong(key)
    }

    private data class BackupTask(
        val id: Int,
        val text: String,
        val isCategory: Boolean,
        val completed: Boolean,
        val importance: Int,
        val parentId: Int?,
        val sortOrder: Long,
        val expanded: Boolean,
        val dueDate: Long?,
        val description: String?,
        val link: String?,
        val pinned: Boolean,
        val reminderAt: Long?,
        val repeatAmount: Int?,
        val repeatUnit: String?,
        val createdAt: Long,
        val attachmentEntry: String?,
        val attachmentName: String?,
        val attachmentMimeType: String?
    )

    private data class ParsedBackup(
        val tasks: List<BackupTask>,
        val attachmentFiles: Map<String, File>,
        val tempDirectory: File
    )

    private data class MaterializedAttachment(
        val uri: String,
        val name: String,
        val mimeType: String
    )

    private class TaskBackupException(
        message: String,
        cause: Throwable? = null
    ) : IllegalStateException(message, cause)

    private companion object {
        private const val BACKUP_FORMAT_VERSION = 1
        private const val MANIFEST_ENTRY_NAME = "opusagenda_backup.json"

        private const val KEY_FORMAT_VERSION = "formatVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_TASKS = "tasks"
        private const val KEY_ID = "id"
        private const val KEY_TEXT = "text"
        private const val KEY_IS_CATEGORY = "isCategory"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_IMPORTANCE = "importance"
        private const val KEY_PARENT_ID = "parentId"
        private const val KEY_SORT_ORDER = "sortOrder"
        private const val KEY_EXPANDED = "expanded"
        private const val KEY_DUE_DATE = "dueDate"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_LINK = "link"
        private const val KEY_PINNED = "pinned"
        private const val KEY_REMINDER_AT = "reminderAt"
        private const val KEY_REPEAT_AMOUNT = "repeatAmount"
        private const val KEY_REPEAT_UNIT = "repeatUnit"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_ATTACHMENT_ENTRY = "attachmentEntry"
        private const val KEY_ATTACHMENT_NAME = "attachmentName"
        private const val KEY_ATTACHMENT_MIME_TYPE = "attachmentMimeType"
    }
}
