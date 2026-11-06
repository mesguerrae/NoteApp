package com.mee.noteapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.mee.noteapp.db.NoteStyle

data class OverlayNote(
    val id: Long,
    var text: String,
    var xPercent: Float,
    var yPercent: Float,
    var style: String = NoteStyle.GENERAL,
    var sizeScale: Float = 1f,
    val createdAt: Long = 0L
)

/**
 * Transparent layer drawn on top of a page (MHTML render or PDF bitmap).
 * - Single tap on empty space -> [onRequestNewNote] with the tapped page-percent position.
 * - Tap on an existing note -> [onRequestEditNote].
 * - Drag the body of an existing note -> moves it, reported via [onNoteMoved].
 * - Drag the small handle at its bottom-right corner -> resizes it (box AND text grow or
 *   shrink together), reported via [onNoteResized].
 *
 * Positions are stored as percentages (0f..1f) of the PAGE, not of the screen. When
 * [pageMatrix]/[pageSize] are set (PDF viewer, which supports pinch-zoom/pan) note
 * positions are mapped through the current zoom/pan matrix so they stay glued to the
 * exact spot of the page they were written on. When left null (MHTML viewer, no zoom
 * layer of its own) the page is assumed to fill this view 1:1.
 */
class AnnotationOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var notes: List<OverlayNote> = emptyList()
        set(value) { field = value; invalidate() }

    var onRequestNewNote: ((xPercent: Float, yPercent: Float) -> Unit)? = null
    var onRequestEditNote: ((OverlayNote) -> Unit)? = null
    var onNoteMoved: ((OverlayNote, xPercent: Float, yPercent: Float) -> Unit)? = null
    var onNoteResized: ((OverlayNote, sizeScale: Float) -> Unit)? = null
    var onRequestDeleteNote: ((OverlayNote) -> Unit)? = null

    /** Current zoom/pan transform of the page content (bitmap space -> screen space). */
    var pageMatrix: Matrix? = null
        set(value) { field = value; invalidate() }

    /** Size, in bitmap pixels, of the page content that [pageMatrix] maps from. */
    var pageSize: Pair<Int, Int>? = null

    /**
     * When set (used in the PDF viewer), any touch that is NOT a tap/drag starting on an
     * existing note gets forwarded here so two-finger zoom and one-finger pan keep working.
     */
    var zoomPanTarget: View? = null

    /**
     * Used only when [pageMatrix] is null (MHTML viewer, which zooms via the WebView's own
     * engine instead of a matrix) to find out the current zoom level so notes can be scaled
     * to match, e.g. `{ webView.scale }`.
     */
    var externalScaleProvider: (() -> Float)? = null

    /**
     * The [pageMatrix] scale factor that corresponds to "no zoom" (e.g. the fit-to-screen
     * scale `ZoomableImageView` starts at). Needed because the raw matrix scale depends on
     * how many bitmap pixels fit the view, not on how zoomed-in the user actually is.
     */
    var baseScale: Float = 1f

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 2f
    }
    private val deleteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#E74C3C")
    }
    private val deleteIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }

    private fun applyStyleColors(noteStyle: String) {
        if (noteStyle == NoteStyle.IMPORTANT) {
            bg.color = Color.parseColor("#F5B7B1")
            border.color = Color.parseColor("#C0392B")
            handlePaint.color = Color.parseColor("#C0392B")
        } else {
            bg.color = Color.parseColor("#F9E79F")
            border.color = Color.parseColor("#B7950B")
            handlePaint.color = Color.parseColor("#B7950B")
        }
    }

    // Reference sizes at 1x zoom and 1x note size; everything else is derived by multiplying
    // by currentScale() (the page's zoom) and the note's own [OverlayNote.sizeScale].
    private val baseTextSize = 34f
    private val baseBoxHeight = 60f
    private val baseBorderWidth = 3f
    private val baseCornerRadius = 12f
    private val baseHorizontalPadding = 24f
    private val baseHandleRadius = 16f
    private val minLegibleScale = 0.6f
    private val maxLegibleScale = 3f
    private val minNoteSize = 0.5f
    private val maxNoteSize = 4f
    private val matrixValuesArr = FloatArray(9)

    /** Current zoom factor of the page (1f = no zoom), clamped so notes never become unreadable. */
    private fun currentScale(): Float {
        val matrix = pageMatrix
        val raw = if (matrix != null) {
            matrix.getValues(matrixValuesArr)
            matrixValuesArr[Matrix.MSCALE_X] / baseScale.coerceAtLeast(0.0001f)
        } else {
            externalScaleProvider?.invoke() ?: 1f
        }
        return raw.coerceIn(minLegibleScale, maxLegibleScale)
    }

    private var draggingNote: OverlayNote? = null
    private var resizingNote: OverlayNote? = null
    private var deletingNote: OverlayNote? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val inverse = Matrix()
    private val pt = FloatArray(2)

    /** Note's page-space position (percent) converted to this view's screen pixels. */
    private fun toScreen(n: OverlayNote): Pair<Float, Float> {
        val (pw, ph) = pageSize ?: (width to height)
        val m = pageMatrix
        pt[0] = n.xPercent * pw; pt[1] = n.yPercent * ph
        m?.mapPoints(pt)
        return pt[0] to pt[1]
    }

    /** Screen pixel tap converted to page-space percent (0f..1f). */
    private fun toPagePercent(x: Float, y: Float): Pair<Float, Float> {
        val (pw, ph) = pageSize ?: (width to height)
        val m = pageMatrix
        pt[0] = x; pt[1] = y
        if (m != null) {
            m.invert(inverse)
            inverse.mapPoints(pt)
        }
        return (pt[0] / pw).coerceIn(0f, 1f) to (pt[1] / ph).coerceIn(0f, 1f)
    }

    /** The note's box, top-left anchored at its page position, sized by zoom scale * its own sizeScale. */
    private fun noteRect(n: OverlayNote, scale: Float): RectF {
        val (cx, cy) = toScreen(n)
        val factor = scale * n.sizeScale
        textPaint.textSize = baseTextSize * factor
        val padding = baseHorizontalPadding * factor
        val h = baseBoxHeight * factor
        val w = (textPaint.measureText(n.text.take(24)) + padding).coerceAtLeast(h)
        return RectF(cx, cy, cx + w, cy + h)
    }

    private fun handleCenter(rect: RectF): Pair<Float, Float> = rect.right to rect.bottom

    private fun handleRadius(scale: Float, n: OverlayNote) = baseHandleRadius * scale * n.sizeScale

    private fun deleteButtonCenter(rect: RectF): Pair<Float, Float> = rect.right to rect.top

    private fun deleteButtonRadius(scale: Float, n: OverlayNote) = baseHandleRadius * scale * n.sizeScale

    /** Draws a small trash-can glyph centered at ([cx], [cy]) with the given [radius]. */
    private fun drawTrashIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        deleteIconPaint.strokeWidth = radius * 0.18f
        val w = radius * 0.9f
        val h = radius * 0.9f
        val top = cy - h / 2f
        val bottom = cy + h / 2f
        val left = cx - w / 2f
        val right = cx + w / 2f
        // Lid
        canvas.drawLine(left - w * 0.15f, top, right + w * 0.15f, top, deleteIconPaint)
        canvas.drawLine(cx - w * 0.2f, top, cx - w * 0.2f, top - h * 0.2f, deleteIconPaint)
        canvas.drawLine(cx + w * 0.2f, top, cx + w * 0.2f, top - h * 0.2f, deleteIconPaint)
        canvas.drawLine(cx - w * 0.2f, top - h * 0.2f, cx + w * 0.2f, top - h * 0.2f, deleteIconPaint)
        // Body
        canvas.drawLine(left, top, left, bottom, deleteIconPaint)
        canvas.drawLine(right, top, right, bottom, deleteIconPaint)
        canvas.drawLine(left, bottom, right, bottom, deleteIconPaint)
        // Ridges
        canvas.drawLine(cx, top + h * 0.15f, cx, bottom - h * 0.1f, deleteIconPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = currentScale()
        for (n in notes) {
            applyStyleColors(n.style)
            val factor = scale * n.sizeScale
            textPaint.textSize = baseTextSize * factor
            border.strokeWidth = baseBorderWidth * factor
            val corner = baseCornerRadius * factor
            val r = noteRect(n, scale)
            canvas.drawRoundRect(r, corner, corner, bg)
            canvas.drawRoundRect(r, corner, corner, border)
            canvas.drawText(n.text, r.left + (baseHorizontalPadding / 2f) * factor, r.top + r.height() * 0.68f, textPaint)

            val (hx, hy) = handleCenter(r)
            val hRadius = handleRadius(scale, n)
            canvas.drawCircle(hx, hy, hRadius, handlePaint)
            canvas.drawCircle(hx, hy, hRadius, handleBorderPaint)

            val (dx, dy) = deleteButtonCenter(r)
            val dRadius = deleteButtonRadius(scale, n)
            canvas.drawCircle(dx, dy, dRadius, deleteBgPaint)
            canvas.drawCircle(dx, dy, dRadius, handleBorderPaint)
            drawTrashIcon(canvas, dx, dy, dRadius)
        }
    }

    private fun findResizeHandleAt(x: Float, y: Float): OverlayNote? {
        val scale = currentScale()
        // Iterate in reverse so the most recently drawn (topmost) note wins on overlap.
        for (n in notes.asReversed()) {
            val r = noteRect(n, scale)
            val (hx, hy) = handleCenter(r)
            val radius = handleRadius(scale, n) * 1.6f // slightly bigger touch target than the visual dot
            val dx = x - hx; val dy = y - hy
            if (dx * dx + dy * dy <= radius * radius) return n
        }
        return null
    }

    private fun findDeleteButtonAt(x: Float, y: Float): OverlayNote? {
        val scale = currentScale()
        for (n in notes.asReversed()) {
            val r = noteRect(n, scale)
            val (cx, cy) = deleteButtonCenter(r)
            val radius = deleteButtonRadius(scale, n) * 1.6f
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy <= radius * radius) return n
        }
        return null
    }

    private fun findNoteAt(x: Float, y: Float): OverlayNote? {
        val scale = currentScale()
        return notes.asReversed().firstOrNull { noteRect(it, scale).contains(x, y) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isMultiTouch = event.pointerCount > 1
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; moved = false
                if (isMultiTouch) {
                    draggingNote = null; resizingNote = null; deletingNote = null
                } else {
                    deletingNote = findDeleteButtonAt(event.x, event.y)
                    resizingNote = if (deletingNote == null) findResizeHandleAt(event.x, event.y) else null
                    draggingNote = if (deletingNote == null && resizingNote == null) findNoteAt(event.x, event.y) else null
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> { draggingNote = null; resizingNote = null; deletingNote = null }
            MotionEvent.ACTION_MOVE -> {
                val resizing = resizingNote
                val dragging = draggingNote
                if (resizing != null && !isMultiTouch) {
                    moved = true
                    val (_, cy) = toScreen(resizing)
                    val scale = currentScale()
                    val desired = (event.y - cy) / (baseBoxHeight * scale)
                    resizing.sizeScale = desired.coerceIn(minNoteSize, maxNoteSize)
                    invalidate()
                } else if (dragging != null && !isMultiTouch) {
                    if (kotlin.math.abs(event.x - downX) > 8 || kotlin.math.abs(event.y - downY) > 8) {
                        moved = true
                        val (px, py) = toPagePercent(event.x, event.y)
                        dragging.xPercent = px; dragging.yPercent = py
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val deleting = deletingNote
                val resizing = resizingNote
                val dragging = draggingNote
                if (deleting != null) {
                    // Only fires if the finger is lifted back over the trash icon (standard
                    // button behavior) — dragging away from it cancels the deletion.
                    if (findDeleteButtonAt(event.x, event.y) === deleting) {
                        onRequestDeleteNote?.invoke(deleting)
                    }
                } else if (resizing != null) {
                    if (moved) onNoteResized?.invoke(resizing, resizing.sizeScale)
                    else onRequestEditNote?.invoke(resizing)
                } else if (dragging != null) {
                    if (moved) onNoteMoved?.invoke(dragging, dragging.xPercent, dragging.yPercent)
                    else onRequestEditNote?.invoke(dragging)
                } else if (!moved) {
                    val (px, py) = toPagePercent(event.x, event.y)
                    onRequestNewNote?.invoke(px, py)
                }
                draggingNote = null
                resizingNote = null
                deletingNote = null
                moved = false
            }
        }
        // Forward to the zoomable image (pinch-zoom/pan) whenever we're not manipulating a note.
        if (draggingNote == null && resizingNote == null && deletingNote == null) {
            zoomPanTarget?.dispatchTouchEvent(event)
        }
        return true
    }
}
