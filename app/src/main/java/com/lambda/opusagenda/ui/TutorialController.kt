package com.lambda.opusagenda.ui

import android.graphics.RectF
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.lambda.opusagenda.R
import com.lambda.opusagenda.databinding.DialogTutorialOverlayBinding

/**
 * Controla el overlay de tutorial de primer inicio.
 */
class TutorialController(
    private val activity: AppCompatActivity,
    private val onDismissed: () -> Unit
) {

    private data class TutorialStep(
        @IdRes val targetViewId: Int,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int
    )

    private val steps = listOf(
        TutorialStep(
            targetViewId = 0,
            titleRes = R.string.tutorial_step_welcome_title,
            bodyRes = R.string.tutorial_step_welcome_body,
        ),
        TutorialStep(
            targetViewId = 0,
            titleRes = R.string.tutorial_step_interface_title,
            bodyRes = R.string.tutorial_step_interface_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonFont,
            titleRes = R.string.tutorial_step_font_title,
            bodyRes = R.string.tutorial_step_font_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonImport,
            titleRes = R.string.tutorial_step_import_title,
            bodyRes = R.string.tutorial_step_import_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonExport,
            titleRes = R.string.tutorial_step_export_title,
            bodyRes = R.string.tutorial_step_export_body
        ),
        TutorialStep(
            targetViewId = R.id.layoutQuickLinks,
            titleRes = R.string.tutorial_step_quick_links_title,
            bodyRes = R.string.tutorial_step_quick_links_body
        ),
        TutorialStep(
            targetViewId = 0,
            titleRes = R.string.tutorial_step_filters_title,
            bodyRes = R.string.tutorial_step_filters_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonAll,
            titleRes = R.string.tutorial_step_all_title,
            bodyRes = R.string.tutorial_step_all_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonToday,
            titleRes = R.string.tutorial_step_today_title,
            bodyRes = R.string.tutorial_step_today_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonNew,
            titleRes = R.string.tutorial_step_create_title,
            bodyRes = R.string.tutorial_step_create_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonNew,
            titleRes = R.string.tutorial_step_create_tips,
            bodyRes = R.string.tutorial_step_create_tips_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonNew,
            titleRes = R.string.tutorial_step_importance_title,
            bodyRes = R.string.tutorial_step_importance_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonNew,
            titleRes = R.string.tutorial_step_reminders_title,
            bodyRes = R.string.tutorial_step_reminders_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonNew,
            titleRes = R.string.tutorial_step_persistence_title,
            bodyRes = R.string.tutorial_step_persistence_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonNewCategory,
            titleRes = R.string.tutorial_step_categories_title,
            bodyRes = R.string.tutorial_step_categories_body
        ),
        TutorialStep(
            targetViewId = R.id.buttonNewCategory,
            titleRes = R.string.tutorial_step_categories_tips,
            bodyRes = R.string.tutorial_step_categories_tips_body
        ),
        TutorialStep(
            targetViewId = R.id.editSearch,
            titleRes = R.string.tutorial_step_search_title,
            bodyRes = R.string.tutorial_step_search_body
        ),
        TutorialStep(
            targetViewId = R.id.editSearch,
            titleRes = R.string.tutorial_step_tags_title,
            bodyRes = R.string.tutorial_step_tags_body
        ),
        TutorialStep(
            targetViewId = R.id.textSummary,
            titleRes = R.string.tutorial_step_summary_title,
            bodyRes = R.string.tutorial_step_summary_body
        ),
        TutorialStep(
            targetViewId = R.id.textSummary,
            titleRes = R.string.tutorial_step_widgets_title,
            bodyRes = R.string.tutorial_step_widgets_body
        ),
        TutorialStep(
            targetViewId = 0,
            titleRes = R.string.tutorial_step_end_title,
            bodyRes = R.string.tutorial_step_end_body
        )
    )

    private var overlayBinding: DialogTutorialOverlayBinding? = null
    private var currentStepIndex = 0

    val isShowing: Boolean
        get() = overlayBinding != null

    fun show(force: Boolean = false) {
        overlayBinding?.let {
            if (force) {
                currentStepIndex = 0
                renderStep()
            }
            return
        }

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val binding = DialogTutorialOverlayBinding.inflate(LayoutInflater.from(activity), contentRoot, false)
        overlayBinding = binding
        contentRoot.addView(
            binding.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss(markSeen = true)
                true
            } else {
                false
            }
        }

        binding.buttonTutorialPrev.setOnClickListener {
            if (currentStepIndex > 0) {
                currentStepIndex--
                renderStep()
            }
        }
        binding.buttonTutorialNext.setOnClickListener {
            if (currentStepIndex < steps.lastIndex) {
                currentStepIndex++
                renderStep()
            }
        }
        binding.buttonTutorialFinish.setOnClickListener {
            if (currentStepIndex == steps.lastIndex) {
                dismiss(markSeen = true)
            }
        }

        binding.root.post {
            currentStepIndex = 0
            renderStep()
        }
    }

    fun dismiss(markSeen: Boolean) {
        val binding = overlayBinding ?: return
        val parent = binding.root.parent as? ViewGroup
        parent?.removeView(binding.root)
        overlayBinding = null
        if (markSeen) {
            onDismissed()
        }
    }

    private fun renderStep() {
        val binding = overlayBinding ?: return
        val step = steps.getOrNull(currentStepIndex) ?: return

        binding.textTutorialTitle.setText(step.titleRes)
        binding.textTutorialBody.setText(step.bodyRes)
        binding.textTutorialCounter.text = activity.getString(
            R.string.tutorial_counter_format,
            currentStepIndex + 1,
            steps.size
        )
        binding.buttonTutorialPrev.isEnabled = currentStepIndex > 0
        binding.buttonTutorialNext.isEnabled = currentStepIndex < steps.lastIndex
        binding.buttonTutorialFinish.isEnabled = currentStepIndex == steps.lastIndex
        binding.buttonTutorialFinish.alpha = if (binding.buttonTutorialFinish.isEnabled) 1f else 0.45f
        binding.buttonTutorialNext.alpha = if (binding.buttonTutorialNext.isEnabled) 1f else 0.45f
        binding.buttonTutorialPrev.alpha = if (binding.buttonTutorialPrev.isEnabled) 1f else 0.45f
        binding.buttonTutorialNext.visibility = View.VISIBLE
        binding.buttonTutorialFinish.visibility = View.VISIBLE

        val targetView = activity.findViewById<View>(step.targetViewId)
        binding.root.post {
            val rect = if (step.targetViewId == 0) {
                null
            } else {
                resolveTargetRect(binding.viewTutorialSpotlight, targetView)
            }
            binding.viewTutorialSpotlight.setHighlightRect(rect)
        }
    }

    private fun resolveTargetRect(overlayView: View, targetView: View?): RectF? {
        if (targetView == null || targetView.width <= 0 || targetView.height <= 0) {
            return defaultRect(overlayView)
        }

        val overlayLocation = IntArray(2)
        val targetLocation = IntArray(2)
        overlayView.getLocationOnScreen(overlayLocation)
        targetView.getLocationOnScreen(targetLocation)

        val density = activity.resources.displayMetrics.density
        val padding = 10f * density
        return RectF(
            (targetLocation[0] - overlayLocation[0] - padding),
            (targetLocation[1] - overlayLocation[1] - padding),
            (targetLocation[0] - overlayLocation[0] + targetView.width + padding),
            (targetLocation[1] - overlayLocation[1] + targetView.height + padding)
        )
    }

    private fun defaultRect(overlayView: View): RectF? {
        if (overlayView.width <= 0 || overlayView.height <= 0) return null
        val width = overlayView.width.toFloat()
        val height = overlayView.height.toFloat()
        return RectF(width * 0.15f, height * 0.15f, width * 0.85f, height * 0.28f)
    }
}
