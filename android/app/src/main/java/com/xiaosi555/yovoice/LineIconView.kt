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
            "tts" -> {
                path.moveTo(3f, 10f); path.lineTo(7f, 10f); path.lineTo(11f, 6f); path.lineTo(11f, 18f); path.lineTo(7f, 14f); path.lineTo(3f, 14f); path.close()
                canvas.drawArc(8f, 5f, 19f, 19f, -55f, 110f, false, paint)
                canvas.drawArc(8f, 2f, 23f, 22f, -55f, 110f, false, paint)
            }
            "clone" -> {
                canvas.drawCircle(8f, 9f, 4f, paint); canvas.drawCircle(16f, 15f, 4f, paint)
                path.moveTo(3f, 20f); path.lineTo(6f, 17f); path.lineTo(9f, 20f)
                path.moveTo(15f, 4f); path.lineTo(18f, 7f); path.lineTo(21f, 4f)
            }
            "story" -> {
                path.moveTo(3f, 5f); path.lineTo(10f, 5f); path.quadTo(12f, 5f, 12f, 8f); path.lineTo(12f, 20f); path.quadTo(12f, 17f, 9f, 17f); path.lineTo(3f, 17f); path.close()
                path.moveTo(21f, 5f); path.lineTo(14f, 5f); path.quadTo(12f, 5f, 12f, 8f); path.lineTo(12f, 20f); path.quadTo(12f, 17f, 15f, 17f); path.lineTo(21f, 17f); path.close()
                path.moveTo(6f, 9f); path.lineTo(9f, 9f); path.moveTo(15f, 9f); path.lineTo(18f, 9f)
            }
            "asr" -> {
                path.moveTo(3f, 12f); path.lineTo(6f, 12f); path.lineTo(8f, 7f); path.lineTo(11f, 17f); path.lineTo(14f, 9f); path.lineTo(17f, 14f); path.lineTo(21f, 14f)
                path.moveTo(5f, 20f); path.lineTo(19f, 20f)
            }
            "music" -> {
                path.moveTo(14f, 17f); path.lineTo(14f, 5f); path.lineTo(21f, 3f); path.lineTo(21f, 15f)
                canvas.drawCircle(10f, 18f, 3f, paint); canvas.drawCircle(17f, 16f, 3f, paint)
            }
            "stems" -> {
                canvas.drawRoundRect(3f, 4f, 21f, 9f, 2f, 2f, paint)
                canvas.drawRoundRect(3f, 11f, 21f, 16f, 2f, 2f, paint)
                canvas.drawRoundRect(3f, 18f, 21f, 22f, 2f, 2f, paint)
                path.moveTo(7f, 6.5f); path.lineTo(10f, 6.5f); path.moveTo(7f, 13.5f); path.lineTo(15f, 13.5f); path.moveTo(7f, 20f); path.lineTo(12f, 20f)
            }
            "midi" -> {
                canvas.drawRoundRect(3f, 4f, 21f, 20f, 2f, 2f, paint)
                path.moveTo(7f, 4f); path.lineTo(7f, 20f); path.moveTo(11f, 4f); path.lineTo(11f, 20f); path.moveTo(15f, 4f); path.lineTo(15f, 20f)
                path.moveTo(5f, 11f); path.lineTo(5f, 14f); path.moveTo(9f, 11f); path.lineTo(9f, 14f); path.moveTo(13f, 11f); path.lineTo(13f, 14f); path.moveTo(17f, 11f); path.lineTo(17f, 14f)
            }
            "assistant" -> {
                path.moveTo(12f, 2f); path.lineTo(14f, 9f); path.lineTo(21f, 12f); path.lineTo(14f, 14f); path.lineTo(12f, 22f); path.lineTo(10f, 14f); path.lineTo(3f, 12f); path.lineTo(10f, 9f); path.close()
                canvas.drawCircle(19f, 5f, 1.3f, paint); canvas.drawCircle(5f, 19f, 1.2f, paint)
            }
            "import" -> {
                path.moveTo(12f, 3f); path.lineTo(12f, 15f); path.moveTo(7f, 10f); path.lineTo(12f, 15f); path.lineTo(17f, 10f)
                path.moveTo(4f, 17f); path.lineTo(4f, 21f); path.lineTo(20f, 21f); path.lineTo(20f, 17f)
            }
            "models" -> {
                path.moveTo(12f, 3f); path.lineTo(21f, 8f); path.lineTo(12f, 13f); path.lineTo(3f, 8f); path.close()
                path.moveTo(3f, 8f); path.lineTo(3f, 18f); path.lineTo(12f, 23f); path.lineTo(21f, 18f); path.lineTo(21f, 8f)
                path.moveTo(12f, 13f); path.lineTo(12f, 23f)
            }
            "back" -> {
                path.moveTo(19f, 12f); path.lineTo(5f, 12f); path.moveTo(11f, 6f); path.lineTo(5f, 12f); path.lineTo(11f, 18f)
            }
            "chevron" -> {
                path.moveTo(9f, 5f); path.lineTo(16f, 12f); path.lineTo(9f, 19f)
            }
            "download" -> {
                path.moveTo(12f, 3f); path.lineTo(12f, 16f); path.moveTo(6f, 11f); path.lineTo(12f, 17f); path.lineTo(18f, 11f)
                path.moveTo(4f, 20f); path.lineTo(20f, 20f)
            }
            "play" -> {
                path.moveTo(8f, 4f); path.lineTo(19f, 12f); path.lineTo(8f, 20f); path.close()
            }
            "pause" -> {
                path.moveTo(8f, 5f); path.lineTo(8f, 19f); path.moveTo(16f, 5f); path.lineTo(16f, 19f)
            }
            "check" -> {
                path.moveTo(4f, 12f); path.lineTo(9f, 17f); path.lineTo(20f, 6f)
            }
            "refresh" -> {
                canvas.drawArc(4f, 4f, 20f, 20f, 45f, 285f, false, paint)
                path.moveTo(16f, 3f); path.lineTo(21f, 4f); path.lineTo(20f, 9f)
            }
            "info" -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                path.moveTo(12f, 10.5f); path.lineTo(12f, 17f)
                canvas.drawCircle(12f, 7f, 0.7f, paint)
            }
            "close" -> {
                path.moveTo(5f, 5f); path.lineTo(19f, 19f); path.moveTo(19f, 5f); path.lineTo(5f, 19f)
            }
        }
        if (kind != "grid" && kind != "settings" && kind != "refresh") canvas.drawPath(path, paint)
        canvas.restore()
    }
}
