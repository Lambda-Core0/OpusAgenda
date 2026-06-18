package com.lambda.opusagenda.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.lambda.opusagenda.util.TaskTagUtils

/**
 * Barra compacta para representar tags sin mostrar su texto en la lista.
 * Cada tag se dibuja como una barra fina individual, separada por un pequeno gap.
 */
class TagStripeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tags: List<String> = emptyList()

    fun setTags(tags: List<String>) {
        this.tags = tags.distinctBy { it.lowercase() }
        contentDescription = if (this.tags.isEmpty()) null else this.tags.joinToString(", ")
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = when {
            tags.isEmpty() -> 0
            else -> {
                val barCount = tags.size
                val totalBars = barCount * BAR_WIDTH_DP.dp
                val totalGaps = ((barCount - 1).coerceAtLeast(0)) * BAR_GAP_DP.dp
                totalBars + totalGaps + paddingLeft + paddingRight
            }
        }

        val desiredHeight = if (tags.isEmpty()) {
            0
        } else {
            BAR_HEIGHT_DP.dp + paddingTop + paddingBottom
        }

        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (tags.isEmpty()) return

        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val barWidth = BAR_WIDTH_DP.dp.toFloat()
        val barGap = BAR_GAP_DP.dp.toFloat()
        val barHeight = availableHeight.toFloat()
        val top = paddingTop.toFloat()

        tags.forEachIndexed { index, tag ->
            paint.color = TaskTagUtils.colorForTag(context, tag)
            val left = paddingLeft + index * (barWidth + barGap)
            val right = left + barWidth
            canvas.drawRoundRect(
                left,
                top,
                right,
                top + barHeight,
                BAR_RADIUS_DP.dp.toFloat(),
                BAR_RADIUS_DP.dp.toFloat(),
                paint
            )
        }
    }

    private companion object {
        private const val BAR_WIDTH_DP = 4
        private const val BAR_HEIGHT_DP = 24
        private const val BAR_GAP_DP = 2
        private const val BAR_RADIUS_DP = 1
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
