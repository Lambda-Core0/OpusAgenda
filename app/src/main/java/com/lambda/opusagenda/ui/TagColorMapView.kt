package com.lambda.opusagenda.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.lambda.opusagenda.R

/**
 * Mapa HSV compacto para elegir color con un arrastre 2D:
 * horizontal = saturacion, vertical = brillo.
 */
class TagColorMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val contentRect = RectF()
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whiteOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blackOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = FloatArray(3)

    private var hue: Float = 0f
    private var saturation: Float = 1f
    private var value: Float = 1f
    private var onColorChangedListener: ((Int) -> Unit)? = null

    private val cornerRadius = 14.dp.toFloat()
    private val pointerRadius = 12.dp.toFloat()

    init {
        setPadding(12.dp, 12.dp, 12.dp, 12.dp)
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 1.dp.toFloat()
        framePaint.color = ContextCompat.getColor(context, R.color.terminal_line)

        pointerOuterPaint.style = Paint.Style.STROKE
        pointerOuterPaint.strokeWidth = 2.dp.toFloat()
        pointerOuterPaint.color = Color.BLACK

        pointerInnerPaint.style = Paint.Style.STROKE
        pointerInnerPaint.strokeWidth = 2.dp.toFloat()
        pointerInnerPaint.color = Color.WHITE

        pointerFillPaint.style = Paint.Style.FILL
    }

    fun setOnColorChangedListener(listener: ((Int) -> Unit)?) {
        onColorChangedListener = listener
    }

    fun setColor(color: Int, notify: Boolean = false) {
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        invalidate()
        if (notify) {
            onColorChangedListener?.invoke(getColor())
        }
    }

    fun setHue(newHue: Float, notify: Boolean = false) {
        hue = normalizeHue(newHue)
        invalidate()
        if (notify) {
            onColorChangedListener?.invoke(getColor())
        }
    }

    fun getColor(): Int {
        return Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (contentRect.width() <= 0f || contentRect.height() <= 0f) return

        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        basePaint.shader = null
        basePaint.color = hueColor
        canvas.drawRoundRect(contentRect, cornerRadius, cornerRadius, basePaint)

        whiteOverlayPaint.shader = LinearGradient(
            contentRect.left,
            contentRect.top,
            contentRect.right,
            contentRect.top,
            Color.WHITE,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(contentRect, cornerRadius, cornerRadius, whiteOverlayPaint)

        blackOverlayPaint.shader = LinearGradient(
            contentRect.left,
            contentRect.top,
            contentRect.left,
            contentRect.bottom,
            Color.TRANSPARENT,
            Color.BLACK,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(contentRect, cornerRadius, cornerRadius, blackOverlayPaint)

        canvas.drawRoundRect(contentRect, cornerRadius, cornerRadius, framePaint)

        val x = contentRect.left + contentRect.width() * saturation
        val y = contentRect.top + contentRect.height() * (1f - value)

        canvas.drawCircle(x, y, pointerRadius, pointerOuterPaint)
        canvas.drawCircle(x, y, pointerRadius - 3.dp.toFloat(), pointerInnerPaint)
        pointerFillPaint.color = getColor()
        canvas.drawCircle(x, y, pointerRadius - 6.dp.toFloat(), pointerFillPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        contentRect.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (w - paddingRight).toFloat(),
            (h - paddingBottom).toFloat()
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || contentRect.width() <= 0f || contentRect.height() <= 0f) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE -> updateFromTouch(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                updateFromTouch(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            updateFromTouch(event.x, event.y)
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float, y: Float) {
        val clampedX = x.coerceIn(contentRect.left, contentRect.right)
        val clampedY = y.coerceIn(contentRect.top, contentRect.bottom)

        saturation = ((clampedX - contentRect.left) / contentRect.width()).coerceIn(0f, 1f)
        value = (1f - ((clampedY - contentRect.top) / contentRect.height())).coerceIn(0f, 1f)
        invalidate()
        onColorChangedListener?.invoke(getColor())
    }

    private fun normalizeHue(newHue: Float): Float {
        val normalized = newHue % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
