package com.lambda.opusagenda.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.ScrollView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import android.media.MediaPlayer
import androidx.core.content.FileProvider
import com.lambda.opusagenda.util.TaskAudioRecorder
import com.lambda.opusagenda.util.TaskBackupManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import android.util.Log
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.lambda.opusagenda.R
import com.lambda.opusagenda.data.AppDatabase
import com.lambda.opusagenda.databinding.ActivityMainBinding
import com.lambda.opusagenda.databinding.DialogQuickLinkBinding
import com.lambda.opusagenda.databinding.DialogTaskEditorBinding
import com.lambda.opusagenda.repository.TaskRepository
import com.lambda.opusagenda.util.TaskContentSupport
import com.lambda.opusagenda.util.TaskDateFormatter
import com.lambda.opusagenda.util.TaskHierarchyManager.DropMode
import com.lambda.opusagenda.util.TaskRepeatCalculator
import com.lambda.opusagenda.util.TaskRepeatCalculator.RepeatUnit
import com.lambda.opusagenda.viewmodel.MainViewModel
import com.lambda.opusagenda.viewmodel.TaskFilterMode
import com.lambda.opusagenda.viewmodel.TaskListItem
import com.lambda.opusagenda.widget.WidgetRefresh
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity(), TaskAdapter.TaskItemActions {

    private enum class ReminderPreset(val labelRes: Int) {
        ONE_HOUR(R.string.reminder_option_hour),
        ONE_DAY(R.string.reminder_option_day),
        ONE_WEEK(R.string.reminder_option_week),
        ONE_MONTH(R.string.reminder_option_month)
    }

    private enum class RepeatPreset(
        val amount: Int,
        val unit: RepeatUnit,
        val labelRes: Int
    ) {
        ONE_HOUR(1, RepeatUnit.HOURS, R.string.repeat_option_hour),
        ONE_DAY(1, RepeatUnit.DAYS, R.string.repeat_option_day),
        ONE_WEEK(1, RepeatUnit.WEEKS, R.string.repeat_option_week),
        ONE_MONTH(1, RepeatUnit.MONTHS, R.string.repeat_option_month),
        ONE_YEAR(1, RepeatUnit.YEARS, R.string.repeat_option_year)
    }

    private companion object {
        private const val PREFS_NAME = "opusagenda_prefs"
        private const val PREF_FONT_KEY = "selected_font"
        private const val FONT_INTER = "inter"
        private const val FONT_GEO = "geo"
        private const val FONT_MEDIEVAL_SHARP = "medieval_sharp"
        private const val FONT_OCTOSQUARES = "octosquares"
        private const val FONT_PIRATEONE = "pirateone"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var taskBackupManager: TaskBackupManager
    private lateinit var quickLinksStore: QuickLinksStore
    private lateinit var taskTouchHelper: ItemTouchHelper
    private var selectedTypeface: Typeface? = null

    private val quickLinks = mutableListOf<QuickLink>()
    private var latestVisibleTasks: List<TaskListItem> = emptyList()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.getDefault())

    private var statusToast: Toast? = null
    private var pendingAttachmentSelection: ((Uri?) -> Unit)? = null
    private var pendingRecordAudioAction: (() -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showStatus(getString(R.string.notifications_permission_denied))
        }
    }

    private val attachmentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingAttachmentSelection?.invoke(uri)
        pendingAttachmentSelection = null
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@registerForActivityResult
        exportTasksBackup(uri)
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        if (!persistReadPermission(uri)) {
            showStatus(getString(R.string.backup_open_failed))
            return@registerForActivityResult
        }
        showImportBackupWarning(uri)
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingRecordAudioAction?.invoke()
        } else {
            showStatus(getString(R.string.task_attachment_record_permission_denied))
        }
        pendingRecordAudioAction = null
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            repository = TaskRepository(
                AppDatabase.getInstance(applicationContext).taskDao()
            ),
            appContext = applicationContext
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySelectedFontTheme()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySelectedTypeface()

        taskBackupManager = TaskBackupManager(applicationContext)
        quickLinksStore = QuickLinksStore(applicationContext)
        quickLinks.clear()
        quickLinks.addAll(quickLinksStore.load())

        taskAdapter = TaskAdapter(this)
        taskTouchHelper = ItemTouchHelper(TaskDragCallback())
        binding.recyclerTasks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = taskAdapter
        }
        taskTouchHelper.attachToRecyclerView(binding.recyclerTasks)

        bindUi()
        observeViewModel()
        renderQuickLinks()
        startClock()
    }

    private fun bindUi() {
        binding.editSearch.doAfterTextChanged { editable ->
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }

        binding.buttonAll.setOnClickListener {
            viewModel.setFilterMode(TaskFilterMode.ALL)
        }

        binding.buttonToday.setOnClickListener {
            viewModel.setFilterMode(TaskFilterMode.TODAY)
        }

        binding.buttonNew.setOnClickListener {
            showTaskDialog(task = null, parentId = null, forceIsCategory = false)
        }

        binding.buttonNewCategory.setOnClickListener {
            showTaskDialog(task = null, parentId = null, forceIsCategory = true)
        }

        binding.buttonNewLink.setOnClickListener {
            showQuickLinkDialog()
        }

        binding.buttonFont.setOnClickListener {
            showFontDialog()
        }

        binding.buttonExport.setOnClickListener {
            launchBackupExport()
        }

        binding.buttonImport.setOnClickListener {
            launchBackupImport()
        }
    }

    private fun showFontDialog() {
        val fontKeys = arrayOf(
            FONT_INTER,
            FONT_GEO,
            FONT_MEDIEVAL_SHARP,
            FONT_OCTOSQUARES,
            FONT_PIRATEONE
        )
        val fontLabels = resources.getStringArray(R.array.font_option_labels)
        val selectedFont = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_FONT_KEY, FONT_INTER)
            ?: FONT_INTER
        val checkedItem = fontKeys.indexOf(selectedFont).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.font_dialog_title)
            .setSingleChoiceItems(fontLabels, checkedItem) { dialog, which ->
                val chosenKey = fontKeys[which]
                if (chosenKey != selectedFont) {
                    val saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(PREF_FONT_KEY, chosenKey)
                        .commit()
                    if (saved) {
                        showStatus(getString(R.string.font_applied, fontLabels[which]))
                        recreate()
                    } else {
                        showStatus(getString(R.string.task_failed))
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel_label, null)
            .show()
    }

    private fun launchBackupExport() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        exportBackupLauncher.launch("opusagenda_tasks_$timestamp.zip")
    }

    private fun launchBackupImport() {
        importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    private fun showImportBackupWarning(sourceUri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_import_warning_title)
            .setMessage(R.string.backup_import_warning_message)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.backup_replace_label) { _, _ ->
                importTasksBackup(sourceUri)
            }
            .show()
            .also(::applyDialogPanelBackgrounds)
    }

    private fun exportTasksBackup(destinationUri: Uri) {
        lifecycleScope.launch {
            try {
                val itemCount = taskBackupManager.exportBackup(destinationUri)
                showStatus(getString(R.string.backup_export_success, itemCount))
            } catch (exception: Exception) {
                showStatus(exception.message ?: getString(R.string.backup_export_failed))
            }
        }
    }

    private fun importTasksBackup(sourceUri: Uri) {
        lifecycleScope.launch {
            try {
                val itemCount = taskBackupManager.importBackup(sourceUri)
                showStatus(getString(R.string.backup_import_success, itemCount))
            } catch (exception: Exception) {
                showStatus(exception.message ?: getString(R.string.backup_import_failed))
            }
        }
    }

    private fun applySelectedFontTheme() {
        val selectedFont = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_FONT_KEY, FONT_INTER)
            ?: FONT_INTER
        val themeRes = when (selectedFont) {
            FONT_GEO -> R.style.Theme_OpusAgenda_Geo
            FONT_MEDIEVAL_SHARP -> R.style.Theme_OpusAgenda_MedievalSharp
            FONT_OCTOSQUARES -> R.style.Theme_OpusAgenda_Octosquares
            FONT_PIRATEONE -> R.style.Theme_OpusAgenda_PirateOne
            else -> R.style.Theme_OpusAgenda
        }
        setTheme(themeRes)
    }

    private fun applySelectedTypeface() {
        val selectedFont = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_FONT_KEY, FONT_INTER)
            ?: FONT_INTER
        val fontRes = when (selectedFont) {
            FONT_GEO -> R.font.app_font_geo
            FONT_MEDIEVAL_SHARP -> R.font.app_font_medievalsharp
            FONT_OCTOSQUARES -> R.font.app_font_octosquares
            FONT_PIRATEONE -> R.font.app_font_pirataone
            else -> R.font.app_font_inter
        }
        val typeface = ResourcesCompat.getFont(this, fontRes) ?: Typeface.SANS_SERIF
        selectedTypeface = typeface
        applyTypefaceRecursively(binding.root, typeface)
    }

    private fun applyTypefaceRecursively(view: View, typeface: Typeface) {
        if (view is TextView) {
            view.typeface = typeface
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTypefaceRecursively(view.getChildAt(i), typeface)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.visibleTasks.observe(this) { tasks ->
            latestVisibleTasks = tasks
            taskAdapter.submitItems(tasks)
            binding.textEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.summary.observe(this) { summary ->
            binding.textSummary.text = summary
        }

        viewModel.mode.observe(this) { mode ->
            binding.buttonAll.isSelected = mode == TaskFilterMode.ALL
            binding.buttonToday.isSelected = mode == TaskFilterMode.TODAY
            // Buttons are ImageButtons now (icons). Tint the icon instead of setting text color.
            val colorAll = getColor(if (mode == TaskFilterMode.ALL) R.color.terminal_yellow else R.color.terminal_green)
            val colorToday = getColor(if (mode == TaskFilterMode.TODAY) R.color.terminal_yellow else R.color.terminal_green)
            binding.buttonAll.imageTintList = android.content.res.ColorStateList.valueOf(colorAll)
            binding.buttonToday.imageTintList = android.content.res.ColorStateList.valueOf(colorToday)
            binding.textEmpty.setText(
                if (mode == TaskFilterMode.TODAY) R.string.empty_state_today else R.string.empty_state_all
            )
        }

        viewModel.statusMessage.observe(this) { event ->
            event.getContentIfNotHandled()?.let(::showStatus)
        }
    }

    private fun showTaskDialog(task: TaskListItem?, parentId: Int? = null, forceIsCategory: Boolean? = null) {
        val dialogBinding = DialogTaskEditorBinding.inflate(layoutInflater)
        val isCategory = (task?.isCategory == true) || (forceIsCategory == true)
        val effectiveParentId = task?.parentId ?: parentId
        val importanceOptions = resources.getStringArray(R.array.importance_options).toList()
        val spinnerAdapter = ArrayAdapter(
            this,
            R.layout.item_spinner_terminal,
            importanceOptions
        )
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_terminal_dropdown)
        dialogBinding.spinnerImportance.adapter = spinnerAdapter
        dialogBinding.editTaskText.setText(task?.text.orEmpty())
        dialogBinding.spinnerImportance.setSelection((task?.importance ?: 2) - 1)
        dialogBinding.checkPinned.isChecked = task?.pinned == true
        dialogBinding.editDescription.setText(task?.description.orEmpty())
        dialogBinding.editLink.setText(task?.link.orEmpty())

        if (isCategory) {
            dialogBinding.editTaskText.setHint(R.string.category_text_hint)
            dialogBinding.textImportanceLabel.visibility = View.GONE
            dialogBinding.spinnerImportance.visibility = View.GONE
            dialogBinding.textDueDate.visibility = View.GONE
            dialogBinding.layoutDueActions.visibility = View.GONE
            dialogBinding.textReminder.visibility = View.GONE
            dialogBinding.textRepeat.visibility = View.GONE
            dialogBinding.layoutReminderActions.visibility = View.GONE
            dialogBinding.checkPinned.visibility = View.GONE
        }

        var selectedDueDate = task?.dueDate
        var selectedReminderAt = task?.reminderAt
        var selectedReminderPreset: ReminderPreset? = null
        var selectedRepeatAmount = task?.repeatAmount
        var selectedRepeatUnit = task?.repeatUnit
        var selectedAttachmentUri = task?.attachmentUri
        var selectedAttachmentName = task?.attachmentName
        var selectedAttachmentMimeType = task?.attachmentMimeType
        val audioRecorder = TaskAudioRecorder(this)

        fun updateDueLabel() {
            dialogBinding.textDueDate.text = getString(
                R.string.task_due_label,
                TaskDateFormatter.formatForPicker(this, selectedDueDate)
            )
        }

        fun updateReminderLabel() {
            val value = when {
                selectedReminderAt == null -> getString(R.string.no_reminder)
                selectedReminderPreset != null -> getString(
                    R.string.reminder_summary_with_datetime,
                    getString(selectedReminderPreset!!.labelRes),
                    TaskDateFormatter.formatDateTime(this, selectedReminderAt)
                )
                else -> TaskDateFormatter.formatDateTime(this, selectedReminderAt)
            }
            dialogBinding.textReminder.text = getString(R.string.task_reminder_value_label, value)
        }

        fun updateRepeatLabel() {
            val value = TaskRepeatCalculator.formatSummary(
                context = this,
                amount = selectedRepeatAmount,
                unitValue = selectedRepeatUnit
            )
            dialogBinding.textRepeat.text = getString(R.string.task_repeat_value_label, value)
        }

        fun updateAttachmentLabel() {
            dialogBinding.textAttachmentName.text = selectedAttachmentName
                ?: getString(R.string.task_attachment_empty)
        }

        fun resetRecordAttachmentUi() {
            dialogBinding.buttonRecordAttachment.setImageResource(R.drawable.mic_btn)
            dialogBinding.buttonRecordAttachment.contentDescription =
                getString(R.string.task_attachment_record)
            dialogBinding.buttonPickAttachment.isEnabled = true
            dialogBinding.buttonClearAttachment.isEnabled = true
            updateAttachmentLabel()
        }

        fun refreshPresetReminder() {
            val preset = selectedReminderPreset ?: return
            val recomputed = computeReminderForPreset(selectedDueDate, preset)
            if (recomputed == null) {
                selectedReminderAt = null
                selectedReminderPreset = null
                showStatus(getString(R.string.reminder_requires_due_date))
                updateReminderLabel()
                return
            }
            if (!isFutureReminder(recomputed)) {
                selectedReminderAt = null
                selectedReminderPreset = null
                showStatus(getString(R.string.reminder_time_in_past))
                updateReminderLabel()
                return
            }
            selectedReminderAt = recomputed
            updateReminderLabel()
        }

        updateDueLabel()
        updateReminderLabel()
        updateRepeatLabel()
        updateAttachmentLabel()

        dialogBinding.buttonPickDueDate.setOnClickListener {
            showDueDatePicker(selectedDueDate) { millis ->
                selectedDueDate = millis
                updateDueLabel()
                refreshPresetReminder()
            }
        }

        dialogBinding.buttonClearDueDate.setOnClickListener {
            selectedDueDate = null
            updateDueLabel()
            if (selectedReminderPreset != null) {
                selectedReminderAt = null
                selectedReminderPreset = null
                updateReminderLabel()
            }
        }

        dialogBinding.buttonPickReminder.setOnClickListener {
            showReminderPresetDialog(
                currentDueDate = selectedDueDate,
                currentReminderAt = selectedReminderAt
            ) { reminderAt, preset ->
                selectedReminderAt = reminderAt
                selectedReminderPreset = preset
                if (reminderAt == null) {
                    selectedRepeatAmount = null
                    selectedRepeatUnit = null
                    updateRepeatLabel()
                }
                updateReminderLabel()
            }
        }

        dialogBinding.buttonPickRepeat.setOnClickListener {
            if (selectedReminderAt == null) {
                showStatus(getString(R.string.repeat_requires_reminder))
                return@setOnClickListener
            }
            showRepeatDialog(
                currentAmount = selectedRepeatAmount,
                currentUnitValue = selectedRepeatUnit
            ) { amount, unitValue ->
                selectedRepeatAmount = amount
                selectedRepeatUnit = unitValue
                updateRepeatLabel()
            }
        }

        dialogBinding.buttonPickAttachment.setOnClickListener {
            if (audioRecorder.isRecording) {
                audioRecorder.cancel()
                resetRecordAttachmentUi()
            }
            launchAttachmentPicker { uri ->
                if (uri == null) return@launchAttachmentPicker
                if (!persistReadPermission(uri)) {
                    showStatus(getString(R.string.task_attachment_picker_failed))
                    return@launchAttachmentPicker
                }

                selectedAttachmentUri = uri.toString()
                selectedAttachmentName = resolveAttachmentName(uri) ?: uri.lastPathSegment
                selectedAttachmentMimeType = contentResolver.getType(uri)
                    ?: TaskContentSupport.normalizeMimeType(null, selectedAttachmentName)
                updateAttachmentLabel()
                showStatus(getString(R.string.task_attachment_saved))
            }
        }

        dialogBinding.buttonClearAttachment.setOnClickListener {
            if (audioRecorder.isRecording) {
                audioRecorder.cancel()
                resetRecordAttachmentUi()
            }
            selectedAttachmentUri = null
            selectedAttachmentName = null
            selectedAttachmentMimeType = null
            updateAttachmentLabel()
        }

        dialogBinding.buttonRecordAttachment.setOnClickListener {
            if (audioRecorder.isRecording) {
                stopRecordingAttachment(
                    audioRecorder = audioRecorder,
                    dialogBinding = dialogBinding,
                    onRecorded = { uri, name, mimeType ->
                        selectedAttachmentUri = uri
                        selectedAttachmentName = name
                        selectedAttachmentMimeType = mimeType
                        updateAttachmentLabel()
                        showStatus(getString(R.string.task_attachment_saved))
                    },
                    onResetUi = ::resetRecordAttachmentUi
                )
                return@setOnClickListener
            }
            runWithRecordAudioPermission {
                startRecordingAttachment(
                    audioRecorder = audioRecorder,
                    dialogBinding = dialogBinding,
                    onStarted = {
                        dialogBinding.buttonPickAttachment.isEnabled = false
                        dialogBinding.buttonClearAttachment.isEnabled = false
                        dialogBinding.textAttachmentName.text = getString(R.string.task_attachment_recording)
                    },
                    onFailed = {
                        showStatus(getString(R.string.task_attachment_record_failed))
                    }
                )
            }
        }

        val titleRes = when {
            isCategory && task == null -> R.string.category_dialog_title_new
            isCategory -> R.string.category_dialog_title_edit
            task == null -> R.string.task_dialog_title_new
            else -> R.string.task_dialog_title_edit
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.save_label, null)
            .show()

        applyDialogPanelBackgrounds(dialog)

        dialog.setOnDismissListener {
            if (audioRecorder.isRecording) {
                audioRecorder.cancel()
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (audioRecorder.isRecording) {
                showStatus(getString(R.string.task_attachment_finish_recording))
                return@setOnClickListener
            }
            val text = dialogBinding.editTaskText.text?.toString().orEmpty()
            val importance = dialogBinding.spinnerImportance.selectedItemPosition + 1
            val pinned = dialogBinding.checkPinned.isChecked
            val descriptionDraft = dialogBinding.editDescription.text?.toString()?.trim().orEmpty()
            val linkDraft = dialogBinding.editLink.text?.toString()?.trim().orEmpty()
            val finalDescription = descriptionDraft.takeIf { it.isNotBlank() }
            val finalLink = linkDraft.takeIf { it.isNotBlank() }?.let(::normalizeUrl)

            if (text.isBlank()) {
                showStatus(getString(R.string.task_text_required))
                return@setOnClickListener
            }

            if (selectedReminderAt != null && !isFutureReminder(selectedReminderAt)) {
                showStatus(getString(R.string.reminder_time_in_past))
                return@setOnClickListener
            }

            if (selectedRepeatAmount != null && selectedReminderAt == null) {
                showStatus(getString(R.string.repeat_requires_reminder))
                return@setOnClickListener
            }

            ensureNotificationPermissionIfNeeded(selectedReminderAt)

            if (task == null) {
                viewModel.addTask(
                    text = text,
                    importance = importance,
                    dueDate = selectedDueDate,
                    description = finalDescription,
                    link = finalLink,
                    attachmentUri = selectedAttachmentUri,
                    attachmentName = selectedAttachmentName,
                    attachmentMimeType = selectedAttachmentMimeType,
                    pinned = pinned,
                    reminderAt = selectedReminderAt,
                    repeatAmount = selectedRepeatAmount,
                    repeatUnit = selectedRepeatUnit,
                    parentId = effectiveParentId,
                    isCategory = isCategory
                )
            } else {
                viewModel.updateTask(
                    id = task.id,
                    text = text,
                    importance = importance,
                    dueDate = selectedDueDate,
                    description = finalDescription,
                    link = finalLink,
                    attachmentUri = selectedAttachmentUri,
                    attachmentName = selectedAttachmentName,
                    attachmentMimeType = selectedAttachmentMimeType,
                    pinned = pinned,
                    completed = task.completed,
                    reminderAt = selectedReminderAt,
                    repeatAmount = selectedRepeatAmount,
                    repeatUnit = selectedRepeatUnit,
                    createdAt = task.createdAt,
                    isCategory = task.isCategory,
                    parentId = task.parentId,
                    sortOrder = task.sortOrder,
                    expanded = task.expanded
                )
            }
            dialog.dismiss()
        }
    }

    private fun showDueDatePicker(currentDueDate: Long?, onPicked: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.ThemeOverlay_OpusAgenda_MaterialCalendar)
            .setTitleText(getString(R.string.pick_due_date_label))
            .setSelection(currentDueDate?.let(::toPickerSelection) ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            onPicked(fromPickerSelection(selection))
        }

        picker.show(supportFragmentManager, "due_date_picker")
    }

    private fun showReminderPresetDialog(
        currentDueDate: Long?,
        currentReminderAt: Long?,
        onPicked: (Long?, ReminderPreset?) -> Unit
    ) {
        val presetOptions = listOf(getString(R.string.reminder_option_none)) +
            ReminderPreset.values().map { getString(it.labelRes) } +
            getString(R.string.reminder_option_custom)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reminder_dialog_title)
            .setItems(presetOptions.toTypedArray()) { _, which ->
                if (which == 0) {
                    onPicked(null, null)
                } else if (which <= ReminderPreset.values().size) {
                    val preset = ReminderPreset.values()[which - 1]
                    val reminderAt = computeReminderForPreset(currentDueDate, preset)
                    if (reminderAt == null) {
                        showStatus(getString(R.string.reminder_requires_due_date))
                        return@setItems
                    }
                    if (!isFutureReminder(reminderAt)) {
                        showStatus(getString(R.string.reminder_time_in_past))
                        return@setItems
                    }
                    onPicked(reminderAt, preset)
                } else {
                    showCustomReminderDateTimePicker(
                        initialReminderAt = currentReminderAt ?: System.currentTimeMillis()
                    ) { customMillis ->
                        if (!isFutureReminder(customMillis)) {
                            showStatus(getString(R.string.reminder_time_in_past))
                            return@showCustomReminderDateTimePicker
                        }
                        onPicked(customMillis, null)
                    }
                }
            }
            .show()
            .also(::applyDialogPanelBackgrounds)
    }

    private fun showRepeatDialog(
        currentAmount: Int?,
        currentUnitValue: String?,
        onPicked: (Int?, String?) -> Unit
    ) {
        val options = listOf(getString(R.string.repeat_option_none)) +
            RepeatPreset.values().map { getString(it.labelRes) } +
            getString(R.string.repeat_option_custom)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.repeat_dialog_title)
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 -> onPicked(null, null)
                    which <= RepeatPreset.values().size -> {
                        val preset = RepeatPreset.values()[which - 1]
                        onPicked(preset.amount, preset.unit.storageValue)
                    }
                    else -> showCustomRepeatDialog(currentAmount, currentUnitValue, onPicked)
                }
            }
            .show()
            .also(::applyDialogPanelBackgrounds)
    }

    private fun showCustomRepeatDialog(
        currentAmount: Int?,
        currentUnitValue: String?,
        onPicked: (Int, String) -> Unit
    ) {
        val unitOptions = RepeatUnit.values().toList()
        val unitLabels = unitOptions.map { getString(it.labelRes) }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * resources.displayMetrics.density).toInt())
        }

        val amountInput = EditText(this).apply {
            hint = getString(R.string.repeat_custom_amount_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((currentAmount ?: 1).toString())
            setSelection(text.length)
            setBackgroundResource(R.drawable.bg_terminal_input)
            setPadding((12 * resources.displayMetrics.density).toInt())
            setTextColor(getColor(R.color.terminal_text))
            setHintTextColor(getColor(R.color.terminal_text_muted))
        }

        val unitSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                R.layout.item_spinner_terminal,
                unitLabels
            ).also { adapter ->
                adapter.setDropDownViewResource(R.layout.item_spinner_terminal_dropdown)
            }
            background = getDrawable(R.drawable.bg_terminal_input)
            val selectedUnit = RepeatUnit.fromStorage(currentUnitValue) ?: RepeatUnit.DAYS
            setSelection(unitOptions.indexOf(selectedUnit).coerceAtLeast(0))
        }

        container.addView(amountInput)
        container.addView(unitSpinner.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.repeat_custom_title)
            .setView(container)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.save_label, null)
            .show()

        applyDialogPanelBackgrounds(dialog)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val amount = amountInput.text?.toString()?.toIntOrNull()
            if (amount == null || amount <= 0) {
                showStatus(getString(R.string.repeat_custom_amount_invalid))
                return@setOnClickListener
            }

            val unit = unitOptions[unitSpinner.selectedItemPosition]
            onPicked(amount, unit.storageValue)
            dialog.dismiss()
        }
    }

    private fun showCustomReminderDateTimePicker(
        initialReminderAt: Long,
        onPicked: (Long) -> Unit
    ) {
        val initialDateTime = Instant.ofEpochMilli(initialReminderAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.ThemeOverlay_OpusAgenda_MaterialCalendar)
            .setTitleText(getString(R.string.reminder_custom_date_title))
            .setSelection(toPickerSelection(initialReminderAt))
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val pickedDate = Instant.ofEpochMilli(selection)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(initialDateTime.hour)
                .setMinute(initialDateTime.minute)
                .setTitleText(R.string.reminder_custom_time_title)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val customReminderAt = pickedDate
                    .atTime(timePicker.hour, timePicker.minute)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                onPicked(customReminderAt)
            }

            timePicker.show(supportFragmentManager, "reminder_time_picker")
        }

        datePicker.show(supportFragmentManager, "reminder_date_picker")
    }

    override fun onToggleCompleted(item: TaskListItem, completed: Boolean) {
        viewModel.toggleCompleted(item, completed)
    }

    override fun onTogglePinned(item: TaskListItem) {
        viewModel.togglePinned(item)
    }

    override fun onEdit(item: TaskListItem) {
        showTaskDialog(item)
    }

    override fun onDelete(item: TaskListItem) {
        viewModel.deleteTask(item)
    }

    override fun onToggleExpanded(item: TaskListItem) {
        viewModel.toggleExpanded(item)
    }

    override fun onCreateSubcategory(item: TaskListItem) {
        showTaskDialog(task = null, parentId = item.id, forceIsCategory = true)
    }

    override fun onCreateChildTask(item: TaskListItem) {
        showTaskDialog(task = null, parentId = item.id, forceIsCategory = false)
    }

    override fun onShowDescription(item: TaskListItem) {
        val description = item.description?.trim().orEmpty()
        if (description.isBlank()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_description_title)
            .setMessage(description)
            .setPositiveButton(android.R.string.ok, null)
            .show()
            .also(::applyDialogPanelBackgrounds)
    }

    override fun onShowLink(item: TaskListItem) {
        val link = item.link?.trim().orEmpty()
        if (link.isBlank()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_link_title)
            .setMessage(link)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.link_open_label) { _, _ ->
                openUrl(link)
            }
            .show()
            .also(::applyDialogPanelBackgrounds)
    }

    override fun onShowAttachment(item: TaskListItem) {
        showAttachmentDialog(
            attachmentUri = item.attachmentUri,
            attachmentName = item.attachmentName,
            attachmentMimeType = item.attachmentMimeType
        )
    }

    private inner class TaskDragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        private var draggedId: Int? = null
        private var targetId: Int? = null
        private var dropMode: DropMode = DropMode.REORDER_AFTER

        // Hover/merge confirmation state
        private var pendingMergeTarget: Int? = null
        private var pendingMergeMode: DropMode? = null
        private var confirmedMergeTarget: Int? = null
        private var mergeConfirmJob: Job? = null
        // Snapshot of item view top positions when drag started to detect whether a potential target moved
        private var initialTops: Map<Int, Int>? = null

        override fun isLongPressDragEnabled(): Boolean = true

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val item = taskAdapter.getItemAt(viewHolder.bindingAdapterPosition) ?: return 0
            if (item.isCategory && latestVisibleTasks.size <= 1) return 0
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            val dragged = taskAdapter.getItemAt(from) ?: return false
            val hovered = taskAdapter.getItemAt(to) ?: return false
            val firstTime = draggedId == null
            draggedId = draggedId ?: dragged.id
            targetId = hovered.id
            if (firstTime) {
                // capture initial visible child top positions before previewMove mutates layout.
                val map = mutableMapOf<Int, Int>()
                for (i in 0 until binding.recyclerTasks.childCount) {
                    val child = binding.recyclerTasks.getChildAt(i)
                    val holder = binding.recyclerTasks.getChildViewHolder(child)
                    val id = taskAdapter.getItemAt(holder.bindingAdapterPosition)?.id
                    if (id != null && id != dragged.id) {
                        map[id] = child.top
                    }
                }
                initialTops = map
            }
            taskAdapter.previewMove(from, to)
            return true
        }

        override fun chooseDropTarget(
            selected: RecyclerView.ViewHolder,
            dropTargets: MutableList<RecyclerView.ViewHolder>,
            curX: Int,
            curY: Int
        ): RecyclerView.ViewHolder? {
            val child = binding.recyclerTasks.findChildViewUnder(curX.toFloat(), curY.toFloat())
            val holderUnder = child?.let { binding.recyclerTasks.getChildViewHolder(it) }

            val chosenHolder = when {
                holderUnder != null && holderUnder != selected -> holderUnder
                else -> super.chooseDropTarget(selected, dropTargets, curX, curY)
            } ?: return null

            val item = taskAdapter.getItemAt(chosenHolder.bindingAdapterPosition) ?: return chosenHolder

            val top = chosenHolder.itemView.top.toFloat()
            val bottom = chosenHolder.itemView.bottom.toFloat()
            val height = bottom - top
            val relativeY = curY.toFloat() - top

            // Disable merge behavior: only support reordering on drag. This eliminates
            // automatic category creation via merge; category creation is handled via buttons.
            val computedMode = if (relativeY < height / 2f) DropMode.REORDER_BEFORE else DropMode.REORDER_AFTER

            // Cancel any pending merge state
            mergeConfirmJob?.cancel()
            pendingMergeTarget = null
            pendingMergeMode = null
            confirmedMergeTarget = null

            dropMode = computedMode
            targetId = item.id

            Log.d("TaskDrag", "chooseDropTarget chosen=${item.id}, isCategory=${item.isCategory}, curY=$curY, relativeY=$relativeY, computedMode=$computedMode")
            return chosenHolder
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            val dragged = draggedId
            val finalTarget = confirmedMergeTarget ?: targetId
            val finalMode = if (confirmedMergeTarget != null) (pendingMergeMode ?: dropMode) else dropMode
            Log.d("TaskDrag", "clearView dragged=$dragged finalTarget=$finalTarget finalMode=$finalMode pendingMerge=$pendingMergeTarget confirmed=$confirmedMergeTarget")

            if (dragged != null && finalTarget != null && dragged != finalTarget) {
                Log.d("TaskDrag", "Calling viewModel.moveItem($dragged, $finalTarget, $finalMode)")
                viewModel.moveItem(dragged, finalTarget, finalMode)
            } else {
                taskAdapter.submitItems(latestVisibleTasks)
            }

            // cleanup
            mergeConfirmJob?.cancel()
            mergeConfirmJob = null
            pendingMergeTarget = null
            pendingMergeMode = null
            confirmedMergeTarget = null
            initialTops = null
            draggedId = null
            targetId = null
            dropMode = DropMode.REORDER_AFTER
        }
    }

    private fun startClock() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (isActive) {
                    val now = java.time.LocalDateTime.now()
                    binding.textClock.text = now.format(timeFormatter)
                    binding.textDate.text = now.format(dateFormatter)
                    delay(1000L - (System.currentTimeMillis() % 1000L))
                }
            }
        }
    }

    private fun renderQuickLinks() {
        binding.layoutQuickLinks.removeAllViews()
        quickLinks.forEachIndexed { index, link ->
            binding.layoutQuickLinks.addView(createQuickLinkButton(link, index))
        }
    }

    private fun createQuickLinkButton(link: QuickLink, index: Int): View {
        return Button(this).apply {
            text = link.label.uppercase(Locale.getDefault())
            background = getDrawable(R.drawable.bg_terminal_action)
            backgroundTintList = null
            setTextColor(getColor(R.color.terminal_text))
            isAllCaps = false
            gravity = Gravity.CENTER
            minHeight = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            setPadding((12 * resources.displayMetrics.density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener {
                openUrl(link.url)
            }
            setOnLongClickListener {
                showQuickLinkDialog(link, index)
                true
            }
        }
    }

    private fun showQuickLinkDialog(existing: QuickLink? = null, index: Int? = null) {
        val dialogBinding = DialogQuickLinkBinding.inflate(layoutInflater)
        dialogBinding.editQuickLinkLabel.setText(existing?.label.orEmpty())
        dialogBinding.editQuickLinkUrl.setText(existing?.url.orEmpty())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.quick_link_new_title else R.string.quick_link_edit_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.save_label, null)
            .apply {
                if (existing != null && index != null) {
                    setNeutralButton(R.string.quick_link_delete_label) { _, _ ->
                        quickLinks.removeAt(index)
                        quickLinksStore.save(quickLinks)
                        WidgetRefresh.notifyQuickLinkWidgets(this@MainActivity)
                        renderQuickLinks()
                        showStatus(getString(R.string.quick_link_deleted))
                    }
                }
            }
            .show()

        applyDialogPanelBackgrounds(dialog)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val label = dialogBinding.editQuickLinkLabel.text?.toString().orEmpty().trim()
            val url = dialogBinding.editQuickLinkUrl.text?.toString().orEmpty().trim()
            if (label.isBlank() || url.isBlank()) {
                showStatus(getString(R.string.quick_link_invalid))
                return@setOnClickListener
            }

            val normalizedUrl = normalizeUrl(url)
            val newLink = QuickLink(label = label, url = normalizedUrl)
            if (index == null) {
                quickLinks.add(newLink)
            } else {
                quickLinks[index] = newLink
            }
            quickLinksStore.save(quickLinks)
            WidgetRefresh.notifyQuickLinkWidgets(this@MainActivity)
            renderQuickLinks()
            showStatus(getString(R.string.quick_link_saved))
            dialog.dismiss()
        }
    }

    private fun launchAttachmentPicker(onPicked: (Uri?) -> Unit) {
        pendingAttachmentSelection = onPicked
        attachmentPickerLauncher.launch(arrayOf("*/*"))
    }

    private fun runWithRecordAudioPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
            return
        }
        pendingRecordAudioAction = onGranted
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecordingAttachment(
        audioRecorder: TaskAudioRecorder,
        dialogBinding: DialogTaskEditorBinding,
        onStarted: () -> Unit,
        onFailed: () -> Unit
    ) {
        val outputFile = createRecordingOutputFile()
        if (!audioRecorder.start(outputFile)) {
            onFailed()
            return
        }
        dialogBinding.buttonRecordAttachment.setImageResource(R.drawable.stop_rec_btn)
        dialogBinding.buttonRecordAttachment.contentDescription =
            getString(R.string.task_attachment_stop_record)
        onStarted()
    }

    private fun stopRecordingAttachment(
        audioRecorder: TaskAudioRecorder,
        dialogBinding: DialogTaskEditorBinding,
        onRecorded: (uri: String, name: String, mimeType: String) -> Unit,
        onResetUi: () -> Unit
    ) {
        val recordedFile = audioRecorder.stop()
        onResetUi()
        if (recordedFile == null) {
            showStatus(getString(R.string.task_attachment_record_failed))
            return
        }
        val uri = attachmentUriForRecordingFile(recordedFile)
        val name = recordedFile.name
        val mimeType = contentResolver.getType(uri)
            ?: TaskContentSupport.normalizeMimeType("audio/mp4", name)
        onRecorded(uri.toString(), name, mimeType)
    }

    private fun createRecordingOutputFile(): File {
        val directory = File(filesDir, "task_attachments").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(directory, "recording_$timestamp.m4a")
    }

    private fun attachmentUriForRecordingFile(file: File): Uri {
        return FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
    }

    private fun persistReadPermission(uri: Uri): Boolean {
        return try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveAttachmentName(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    private fun showAttachmentDialog(
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?
    ) {
        val rawUri = attachmentUri ?: return
        val uri = Uri.parse(rawUri)
        val displayName = attachmentName ?: uri.lastPathSegment ?: rawUri
        val normalizedMimeType = TaskContentSupport.normalizeMimeType(attachmentMimeType, displayName)
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.getDefault())

        when {
            TaskContentSupport.isPreviewableImage(normalizedMimeType, displayName) -> {
                showImageAttachmentDialog(uri, displayName, normalizedMimeType)
                return
            }
            normalizedMimeType.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "mov") -> {
                showVideoAttachmentDialog(uri, displayName, normalizedMimeType)
                return
            }
            normalizedMimeType.startsWith("audio/") || ext in listOf("mp3", "m4a", "wav", "ogg", "flac") -> {
                showAudioAttachmentDialog(uri, displayName, normalizedMimeType)
                return
            }
            normalizedMimeType.startsWith("text/") || ext in listOf("txt", "csv") -> {
                showTextAttachmentDialog(uri, displayName, normalizedMimeType)
                return
            }
            normalizedMimeType == "application/pdf" || ext == "pdf" -> {
                showPdfPreviewDialog(uri, displayName, normalizedMimeType)
                return
            }
            else -> {
                val info = buildString {
                    append(getString(R.string.task_attachment_name_label, displayName))
                    if (normalizedMimeType.isNotBlank()) {
                        append("\n")
                        append(getString(R.string.task_attachment_type_label, normalizedMimeType))
                    }
                }

                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.task_attachment_title)
                    .setMessage(info)
                    .setNegativeButton(R.string.cancel_label, null)
                    .setPositiveButton(R.string.link_open_label) { _, _ ->
                        openAttachment(uri, normalizedMimeType)
                    }
                    .show()
                    .also(::applyDialogPanelBackgrounds)
            }
        }
    }

    private fun showImageAttachmentDialog(uri: Uri, displayName: String, mimeType: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * resources.displayMetrics.density).toInt())
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.task_attachment_name_label, displayName)
            setTextColor(getColor(R.color.terminal_text))
        }

        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            setImageURI(uri)
        }

        container.addView(titleView)
        container.addView(imageView.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
        })

        if (mimeType.isNotBlank()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.task_attachment_type_label, mimeType)
                setTextColor(getColor(R.color.terminal_text_muted))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (12 * resources.displayMetrics.density).toInt()
                }
            })
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_attachment_title)
            .setView(container)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.link_open_label) { _, _ ->
                openAttachment(uri, mimeType)
            }
            .show()
            .also(::applyDialogPanelBackgrounds)
    }

    private fun showVideoAttachmentDialog(uri: Uri, displayName: String, mimeType: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * resources.displayMetrics.density).toInt())
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.task_attachment_name_label, displayName)
            setTextColor(getColor(R.color.terminal_text))
        }

        val videoView = VideoView(this).apply {
            setVideoURI(uri)
            val mc = MediaController(this@MainActivity)
            mc.setAnchorView(this)
            setMediaController(mc)
        }

        container.addView(titleView)
        container.addView(videoView.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_attachment_title)
            .setView(container)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.link_open_label) { _, _ ->
                openAttachment(uri, mimeType)
            }
            .show()
            .also(::applyDialogPanelBackgrounds)

        // Adjust VideoView height to keep aspect ratio and start playback when prepared
        try {
            videoView.setOnPreparedListener { mp ->
                try {
                    val vw = mp.videoWidth
                    val vh = mp.videoHeight
                    if (vw > 0 && vh > 0) {
                        val metrics = resources.displayMetrics
                        val horizontalPadding = (16 * metrics.density).toInt() // container padding + margins
                        val availableWidth = metrics.widthPixels - horizontalPadding
                        val desiredHeight = (availableWidth.toFloat() * vh / vw).toInt()
                        videoView.layoutParams = videoView.layoutParams.apply {
                            height = desiredHeight
                        }
                        videoView.requestLayout()
                    }
                } catch (_: Exception) {}
                try { videoView.start() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun showAudioAttachmentDialog(uri: Uri, displayName: String, mimeType: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * resources.displayMetrics.density).toInt())
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.task_attachment_name_label, displayName)
            setTextColor(getColor(R.color.terminal_text))
        }

        val descriptionView = TextView(this).apply {
            setTextColor(getColor(R.color.terminal_text_muted))
            text = mimeType
            setPadding((6 * resources.displayMetrics.density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
        }

        val playButton = ImageButton(this).apply {
            setImageResource(R.drawable.play_btn)
            background = null
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.terminal_green))
            contentDescription = getString(R.string.play_label)
            val size = (44 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
        }
        var player: MediaPlayer? = null

        playButton.setOnClickListener {
            if (player == null) {
                player = MediaPlayer().apply {
                    setDataSource(this@MainActivity, uri)
                    setOnPreparedListener {
                        try {
                            start()
                            playButton.setImageResource(R.drawable.stop_btn)
                            playButton.contentDescription = getString(R.string.stop_label)
                        } catch (_: Exception) { }
                    }
                    setOnCompletionListener {
                        playButton.setImageResource(R.drawable.play_btn)
                        playButton.contentDescription = getString(R.string.play_label)
                        try { stop(); reset(); release(); } catch (_: Exception) {}
                        player = null
                    }
                    prepareAsync()
                }
            } else {
                try { player?.stop(); player?.release() } catch (_: Exception) {}
                player = null
                playButton.setImageResource(R.drawable.play_btn)
                playButton.contentDescription = getString(R.string.play_label)
            }
        }

        container.addView(titleView)
        container.addView(descriptionView)
        controls.addView(playButton.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
        container.addView(controls)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_attachment_title)
            .setView(container)
            .setNegativeButton(R.string.cancel_label) { _, _ ->
                try { player?.stop(); player?.release() } catch (_: Exception) {}
            }
            .setPositiveButton(R.string.link_open_label) { _, _ ->
                try { player?.stop(); player?.release() } catch (_: Exception) {}
                openAttachment(uri, mimeType)
            }
            .show()
            .also(::applyDialogPanelBackgrounds)

        // ensure resources are released if dialog is dismissed by other means
        dialog.setOnDismissListener {
            try { player?.stop(); player?.release() } catch (_: Exception) {}
        }
    }

    private fun showTextAttachmentDialog(uri: Uri, displayName: String, mimeType: String) {
        val content = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { it.readText() }
            } ?: ""
        } catch (e: Exception) {
            ""
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * resources.displayMetrics.density).toInt())
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.task_attachment_name_label, displayName)
            setTextColor(getColor(R.color.terminal_text))
        }

        val textView = TextView(this).apply {
            setTextColor(getColor(R.color.terminal_text))
            text = if (content.length > 8000) content.substring(0, 8000) + "\n\n..." else content
            setPadding((6 * resources.displayMetrics.density).toInt())
        }

        val scroll = ScrollView(this).apply {
            addView(textView)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (300 * resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
        }

        container.addView(titleView)
        container.addView(scroll)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_attachment_title)
            .setView(container)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.link_open_label) { _, _ ->
                openAttachment(uri, mimeType)
            }
            .show()
            .also(::applyDialogPanelBackgrounds)
    }

    private fun showPdfPreviewDialog(uri: Uri, displayName: String, mimeType: String) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var currentPage = 0

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * resources.displayMetrics.density).toInt())
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.task_attachment_name_label, displayName)
            setTextColor(getColor(R.color.terminal_text))
        }

        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val pageIndicator = TextView(this).apply {
            setTextColor(getColor(R.color.terminal_text_muted))
            textSize = 12f
            gravity = Gravity.CENTER
        }

        val prevBtn = ImageButton(this).apply {
            setImageResource(R.drawable.prev_btn)
            background = null
            contentDescription = getString(R.string.previous_label)
            val size = (44 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        val nextBtn = ImageButton(this).apply {
            setImageResource(R.drawable.next_btn)
            background = null
            contentDescription = getString(R.string.next_label)
            val size = (44 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(prevBtn)
            addView(pageIndicator.apply {
                layoutParams = LinearLayout.LayoutParams(
                    (124 * resources.displayMetrics.density).toInt(),
                    (44 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginStart = (12 * resources.displayMetrics.density).toInt()
                    marginEnd = (12 * resources.displayMetrics.density).toInt()
                }
            })
            addView(nextBtn)
        }

        container.addView(titleView)
        container.addView(imageView.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (400 * resources.displayMetrics.density).toInt()).apply {
                topMargin = (12 * resources.displayMetrics.density).toInt()
            }
        })
        container.addView(controls)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.task_attachment_title)
            .setView(container)
            .setNegativeButton(R.string.cancel_label, null)
            .setPositiveButton(R.string.link_open_label) { _, _ -> openAttachment(uri, mimeType) }
            .show()
            .also(::applyDialogPanelBackgrounds)

        try {
            pfd = contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                showStatus(getString(R.string.task_attachment_open_failed))
                dialog.dismiss()
                return
            }
            renderer = PdfRenderer(pfd)

            val pageCount = renderer.pageCount
            fun renderPage(index: Int) {
                val page = renderer.openPage(index)
                val metrics = resources.displayMetrics
                val desiredWidth = (metrics.widthPixels * 0.8).toInt()
                val scale = desiredWidth.toFloat() / page.width.toFloat()
                val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE) // Set white background to prevent transparency
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                imageView.setImageBitmap(bmp)
                page.close()
                pageIndicator.text = getString(R.string.pdf_page_indicator, index + 1, pageCount)
                prevBtn.isEnabled = index > 0
                nextBtn.isEnabled = index < pageCount - 1
            }

            renderPage(0)

            prevBtn.setOnClickListener {
                if (currentPage > 0) {
                    currentPage--
                    renderPage(currentPage)
                }
            }
            nextBtn.setOnClickListener {
                if (renderer != null && currentPage < renderer.pageCount - 1) {
                    currentPage++
                    renderPage(currentPage)
                }
            }

        } catch (e: Exception) {
            showStatus(getString(R.string.task_attachment_no_preview))
        }

        dialog.setOnDismissListener {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun openAttachment(uri: Uri, mimeType: String?) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType?.ifBlank { "*/*" } ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showStatus(getString(R.string.task_attachment_open_failed))
        }
    }

    private fun openUrl(rawUrl: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(rawUrl))))
        } catch (_: ActivityNotFoundException) {
            showStatus(getString(R.string.link_missing))
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        return if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
    }

    private fun ensureNotificationPermissionIfNeeded(reminderAt: Long?) {
        if (reminderAt == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun computeReminderForPreset(dueDate: Long?, preset: ReminderPreset): Long? {
        val dueLocalDate = dueDate
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            ?: return null

        // Las tareas solo tienen fecha. Para presets relativos usamos 09:00 local
        // como ancla estable del dia de vencimiento.
        val anchorDateTime = dueLocalDate.atTime(9, 0)
        val reminderDateTime = when (preset) {
            ReminderPreset.ONE_HOUR -> anchorDateTime.minusHours(1)
            ReminderPreset.ONE_DAY -> anchorDateTime.minusDays(1)
            ReminderPreset.ONE_WEEK -> anchorDateTime.minusWeeks(1)
            ReminderPreset.ONE_MONTH -> anchorDateTime.minusMonths(1)
        }
        return reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun isFutureReminder(reminderAt: Long?): Boolean {
        return reminderAt != null && reminderAt > System.currentTimeMillis()
    }

    private fun applyDialogPanelBackgrounds(dialog: AlertDialog) {
        val panelColor = getColor(R.color.terminal_panel)
        val panelIds = listOf(
            androidx.appcompat.R.id.topPanel,
            androidx.appcompat.R.id.contentPanel,
            androidx.appcompat.R.id.customPanel,
            androidx.appcompat.R.id.buttonPanel
        )
        panelIds.forEach { panelId ->
            dialog.findViewById<View>(panelId)?.setBackgroundColor(panelColor)
        }

        // Apply selected typeface to dialog content if available
        try {
            val contentView = dialog.findViewById<View>(android.R.id.content)
                ?: dialog.window?.decorView
            if (contentView != null && selectedTypeface != null) {
                applyTypefaceRecursively(contentView, selectedTypeface!!)
            }
        } catch (_: Exception) {
            // ignore failures when customizing system dialog internals
        }
    }

    private fun toPickerSelection(localMillis: Long): Long {
        val localDate = Instant.ofEpochMilli(localMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private fun fromPickerSelection(utcMillis: Long): Long {
        val localDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun showStatus(message: String) {
        statusToast?.cancel()

        val toastView = TextView(this).apply {
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_terminal_panel)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terminal_yellow))
            text = message
            textSize = 14f
            typeface = selectedTypeface ?: typeface
            gravity = Gravity.CENTER
            setPadding(18.dp, 12.dp, 18.dp, 12.dp)
            maxWidth = (resources.displayMetrics.widthPixels * 0.88f).toInt()
            elevation = 8f
        }

        statusToast = Toast(this).apply {
            duration = Toast.LENGTH_LONG
            view = toastView
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 96.dp)
            show()
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

}
