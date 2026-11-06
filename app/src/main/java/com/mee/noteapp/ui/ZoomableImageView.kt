package com.mee.noteapp.ui

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView with pinch-to-zoom and pan, using an explicit transform [Matrix] so a
 * sibling overlay (see [AnnotationOverlayView]) can be kept perfectly aligned with the
 * rendered page by applying the very same matrix to it.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val matrixValues = FloatArray(9)
    private var minScale = 1f
    private var maxScale = 6f
    var onMatrixChanged: ((Matrix) -> Unit)? = null

    /** The matrix scale that represents "no zoom" (the page fit to the view). */
    val fitScale: Float get() = minScale

    init {
        scaleType = ScaleType.MATRIX
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            imageMatrix.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]
            val newScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
            val factor = newScale / currentScale
            val m = Matrix(imageMatrix)
            m.postScale(factor, factor, detector.focusX, detector.focusY)
            imageMatrix = m
            onMatrixChanged?.invoke(m)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val m = Matrix(imageMatrix)
            m.postTranslate(-dx, -dy)
            imageMatrix = m
            onMatrixChanged?.invoke(m)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            imageMatrix.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]
            val target = if (currentScale > minScale * 1.5f) minScale else minScale * 3f
            val factor = target / currentScale
            val m = Matrix(imageMatrix)
            m.postScale(factor, factor, e.x, e.y)
            imageMatrix = m
            onMatrixChanged?.invoke(m)
            return true
        }
    })

    /** Fits the bitmap inside the view and remembers that as the minimum (1x) scale. */
    fun resetToFit(bitmapW: Int, bitmapH: Int) {
        post {
            val vw = width.toFloat(); val vh = height.toFloat()
            if (vw <= 0 || vh <= 0) return@post
            val scale = kotlin.math.min(vw / bitmapW, vh / bitmapH)
            minScale = scale
            val dx = (vw - bitmapW * scale) / 2f
            val dy = (vh - bitmapH * scale) / 2f
            val m = Matrix()
            m.setScale(scale, scale)
            m.postTranslate(dx, dy)
            imageMatrix = m
            onMatrixChanged?.invoke(m)
        }
    }

    /** Zooms by [factor] (e.g. 1.25f to zoom in, 0.8f to zoom out) centered on the view. */
    fun zoomBy(factor: Float) {
        imageMatrix.getValues(matrixValues)
        val currentScale = matrixValues[Matrix.MSCALE_X]
        val newScale = (currentScale * factor).coerceIn(minScale, maxScale)
        val appliedFactor = newScale / currentScale
        val m = Matrix(imageMatrix)
        m.postScale(appliedFactor, appliedFactor, width / 2f, height / 2f)
        imageMatrix = m
        onMatrixChanged?.invoke(m)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }
}
