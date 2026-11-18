package com.xiaosi555.yovoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Small Material-like outline icons drawn locally; no emoji or generated artwork. */
class LineIconView(context: Context, private val kind: String, private val tint: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val side = minOf(w, h) * 0.78f
        val ox = (w - side) / 2f
        val oy = (h - side) / 2f
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(side / 24f, side / 24f)
        val path = Path()
        when (kind) {
            "home" -> {
                path.moveTo(3f, 11f); path.lineTo(12f, 3.5f); path.lineTo(21f, 11f)
                path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20f); path.lineTo(18.5f, 20f); path.lineTo(18.5f, 9.5f)
                path.moveTo(9.5f, 20f); path.lineTo(9.5f, 14f); path.lineTo(14.5f, 14f); path.lineTo(14.5f, 20f)
            }
            "wave" -> {
                path.moveTo(2f, 13f); path.lineTo(5f, 13f); path.lineTo(8f, 6f); path.lineTo(12f, 18f)
                path.lineTo(15f, 9f); path.lineTo(18f, 13f); path.lineTo(22f, 13f)
            }
            "grid" -> {
                canvas.drawRoundRect(3f, 3f, 10f, 10f, 1.5f, 1.5f, paint)
                canvas.drawRoundRect(14f, 3f, 21f, 10f, 1.5f, 1.5f, paint)
                canvas.drawRoundRect(3f, 14f, 10f, 21f, 1.5f, 1.5f, paint)
                canvas.drawRoundRect(14f, 14f, 21f, 21f, 1.5f, 1.5f, paint)
            }
            "folder" -> {
                path.moveTo(3f, 7f); path.lineTo(9.5f, 7f); path.lineTo(11.5f, 9.5f)
                path.lineTo(21f, 9.5f); path.lineTo(21f, 19f); path.lineTo(3f, 19f); path.close()
                canvas.drawRoundRect(3f, 5f, 10f, 9f, 1.2f, 1.2f, paint)
            }
            "settings" -> {
                canvas.drawCircle(12f, 12f, 3.2f, paint)
                val angles = floatArrayOf(0f,45f,90f,135f,180f,225f,270f,315f)
                angles.forEach { a ->
                    val r = Math.toRadians(a.toDouble())
                    val x1 = 12f + kotlin.math.cos(r).toFloat() * 6.2f
                    val y1 = 12f + kotlin.math.sin(r).toFloat() * 6.2f
                    val x2 = 12f + kotlin.math.cos(r).toFloat() * 9f
                    val y2 = 12f + kotlin.math.sin(r).toFloat() * 9f
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
            }
        }
        if (kind != "grid" && kind != "settings") canvas.drawPath(path, paint)
        canvas.restore()
    }
}
