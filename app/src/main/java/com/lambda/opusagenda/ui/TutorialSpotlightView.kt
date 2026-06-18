package com.lambda.opusagenda.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.lambda.opusagenda.R

/**
 * Dibuja un scrim oscuro con un hueco transparente sobre el elemento activo.
 */
class TutorialSpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC000000.toInt()
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        color = context.getColor(R.color.terminal_green)
    }

    private val cornerRadius = resources.displayMetrics.density
    private var highlightRect: RectF? = null

    fun setHighlightRect(rect: RectF?) {
        highlightRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        highlightRect?.let { rect ->
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, clearPaint)
        }
        canvas.restoreToCount(saveCount)
        highlightRect?.let { rect ->
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, outlinePaint)
        }
    }
}
