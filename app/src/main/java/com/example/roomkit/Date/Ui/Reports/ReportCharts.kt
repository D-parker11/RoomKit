package com.example.roomkit.Date.Reports

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

class PaymentTrendView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var points: List<TrendPoint> = emptyList()
    private val ink = Color.rgb(17, 24, 39)
    private val muted = Color.rgb(100, 116, 139)
    private val density = resources.displayMetrics.density
    private val labelFormat = DateTimeFormatter.ofPattern("MMM", Locale.US)

    fun submit(value: List<TrendPoint>) {
        points = value
        contentDescription = "Payments received in KHR. " + value.joinToString(". ") {
            "${it.month}: ${ReportFormatter.money(it.amount)}"
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty() || width == 0 || height == 0) return
        val font = 11f * resources.displayMetrics.scaledDensity
        val left = 16f * density
        val right = width - 16f * density
        val top = font + 12f * density
        val bottom = height - font - 16f * density
        if (bottom <= top || right <= left) return
        val max = points.maxOf { it.amount }.coerceAtLeast(1L)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textSize = font
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.color = muted
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(if (points.all { it.amount == 0L }) "No payments" else ReportFormatter.money(max),
            right, font, paint)
        paint.color = Color.rgb(226,232,240)
        paint.strokeWidth = density
        for (step in 0..2) {
            val y = top + (bottom - top) * step / 2f
            canvas.drawLine(left, y, right, y, paint)
        }
        val coords = points.mapIndexed { i, p ->
            PointF(left + (right-left) * i / (points.size-1).coerceAtLeast(1),
                bottom - (bottom-top) * (p.amount.toDouble()/max).toFloat())
        }
        val line = Path().apply {
            moveTo(coords[0].x, coords[0].y)
            coords.drop(1).forEach { lineTo(it.x,it.y) }
        }
        val fill = Path(line).apply {
            lineTo(coords.last().x,bottom); lineTo(coords.first().x,bottom); close()
        }
        paint.shader = LinearGradient(0f,top,0f,bottom,
            Color.argb(35,17,24,39),Color.argb(0,17,24,39),Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawPath(fill,paint)
        paint.shader = null
        paint.color = ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(line,paint)
        paint.style = Paint.Style.FILL
        coords.forEachIndexed { i,p ->
            paint.color = ink
            canvas.drawCircle(p.x,p.y,if (i==coords.lastIndex) 4f*density else 2.5f*density,paint)
            paint.color = muted
            paint.textAlign = when (i) { 0 -> Paint.Align.LEFT; coords.lastIndex -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            canvas.drawText(points[i].month.format(labelFormat),p.x,height-4f*density,paint)
        }
    }
}

class OccupancyRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var occupied = 0L
    private var total = 0L
    fun submit(occupied: Long, total: Long) {
        this.total = total.coerceAtLeast(0)
        this.occupied = occupied.coerceIn(0,this.total)
        contentDescription = "${this.occupied} occupied out of ${this.total} rooms"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val size = min(width,height).toFloat()
        val stroke = 12f*d
        val radius = (size-stroke)/2f-2*d
        if (radius <= 0) return
        val cx=width/2f;val cy=height/2f
        val rect=RectF(cx-radius,cy-radius,cx+radius,cy+radius)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=stroke
        paint.strokeCap=Paint.Cap.ROUND;paint.color=Color.rgb(229,231,235)
        canvas.drawOval(rect,paint)
        val fraction=if(total==0L) 0f else occupied.toDouble().div(total).toFloat()
        paint.color=Color.rgb(17,24,39)
        if(fraction>0) canvas.drawArc(rect,-90f,360f*fraction,false,paint)
        paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        paint.textSize=min(27f*resources.displayMetrics.scaledDensity,size*.25f)
        val label=if(total==0L) "—" else "${(fraction*100).toInt()}%"
        canvas.drawText(label,cx,cy-(paint.ascent()+paint.descent())/2,paint)
    }
}
