package app.nexstream.player.ui.screens.epg

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import app.nexstream.player.R
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import androidx.compose.ui.graphics.toArgb
import app.nexstream.player.ui.theme.NexStreamEpgColors

private const val CHANNEL_COL_DP   = 100f
private const val ROW_HEIGHT_DP    = 62f
private const val HEADER_HEIGHT_DP = 56f
private const val DP_PER_MINUTE    = 6f
private const val CELL_RADIUS_DP   = 4f
private const val CELL_MARGIN_DP   = 1f
private const val ICON_SIZE_DP     = 32f
private const val BADGE_SIZE_DP    = 14f

private const val DAYS_BACK    = 1
private const val DAYS_FORWARD = 3

data class EPGProgram(
    val entity: ProgramEntity,
    val isPlaceholder: Boolean
)

interface EPGGridCallbacks {
    fun onProgramSelected(channel: ChannelEntity, program: ProgramEntity, isPlaceholder: Boolean)
    fun onChannelClicked(channel: ChannelEntity)
    fun onBack()
    fun onReady()
    fun onVisibleChannelIdsChanged(ids: List<String>)
    fun onFocusUp() {}
    fun onFocusedProgramChanged(channel: ChannelEntity, program: ProgramEntity?) {}
}

class EPGGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var nexTheme: NexStreamEpgColors? = null
        set(value) { if (value === field) return; field = value; buildPaints(); invalidate() }

    var textScale: Float = 1.0f
        set(value) {
            if (value == field) return
            field = value
            buildPaints()
            // Recompute scroll bounds since row/column sizes are scale-dependent
            maxScrollX = (totalWidthPx - (width - channelColPx)).coerceAtLeast(0f)
            maxScrollY = (channels.size * rowHeightPx - (height - headerHeightPx)).coerceAtLeast(0f)
            clampScroll()
            invalidate()
        }

    var textBold: Boolean = false
        set(value) { if (value == field) return; field = value; buildPaints(); invalidate() }

    var isTV: Boolean = false

    var channels: List<ChannelEntity> = emptyList()
        set(value) {
            if (value === field) return
            val idsChanged = value.map { it.id } != field.map { it.id }
            field = value
            if (idsChanged) {
                logoCache.clear()
                logoCacheOrder.clear()
                initialsBitmapCache.clear()
                maxScrollY = (field.size * rowHeightPx - (height - headerHeightPx)).coerceAtLeast(0f)
                scrollY = scrollY.coerceIn(0f, maxScrollY)
                focusedRowIndex = focusedRowIndex.coerceAtMost((field.size - 1).coerceAtLeast(0))
                lastReportedIds = emptyList()
            }
            invalidate()
        }

    var programsMap: Map<String, List<EPGProgram>> = emptyMap()
        set(value) { if (value === field) return; field = value; invalidate() }

    var reminderIds: Set<String> = emptySet()
        set(value) { if (value === field) return; field = value; invalidate() }

    var callbacks: EPGGridCallbacks? = null

    var playerVisible: Boolean = false

    var recordingChannelUrls: Set<String> = emptySet()
        set(value) {
            val changed = value != field
            field = value
            if (changed) {
                mainHandler.removeCallbacks(recFlashRunnable)
                if (value.isNotEmpty()) mainHandler.postDelayed(recFlashRunnable, 500L)
                invalidate()
            }
        }

    private val dp = context.resources.displayMetrics.density

    private val exo2Regular: Typeface by lazy {
        androidx.core.content.res.ResourcesCompat.getFont(context, R.font.exo2) ?: Typeface.DEFAULT
    }
    private val exo2Bold: Typeface by lazy {
        androidx.core.graphics.TypefaceCompat.create(context,
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.exo2),
            android.graphics.Typeface.BOLD, false)
    }

    // Base (unscaled) dimension constants
    private val channelColBasePx   = CHANNEL_COL_DP   * dp
    private val rowHeightBasePx    = ROW_HEIGHT_DP    * dp
    private val headerHeightBasePx = HEADER_HEIGHT_DP * dp
    private val dpPerMinutePx      = DP_PER_MINUTE    * dp
    private val cellRadius         = CELL_RADIUS_DP   * dp
    private val cellMargin         = CELL_MARGIN_DP   * dp
    private val iconSizePx         = (ICON_SIZE_DP    * dp).toInt()
    private val badgeSizePx        = (BADGE_SIZE_DP   * dp).toInt()

    // These three scale dynamically with textScale so text always fits
    private val channelColPx:   Float get() = channelColBasePx   * textScale.coerceIn(0.85f, 1.5f)
    private val rowHeightPx:    Float get() = rowHeightBasePx    * textScale.coerceIn(0.85f, 1.5f)
    private val headerHeightPx: Float get() = headerHeightBasePx * textScale.coerceIn(0.85f, 1.5f)

    private var startOfWindow = 0L
    private var totalMinutes  = 0
    private var totalWidthPx  = 0f

    private fun computeTimeWindow() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }
        startOfWindow = cal.timeInMillis - DAYS_BACK * 24 * 60 * 60 * 1000L
        totalMinutes  = (DAYS_BACK + DAYS_FORWARD) * 24 * 60
        totalWidthPx  = totalMinutes * dpPerMinutePx
    }

    private val scroller = OverScroller(context)
    private var scrollX  = 0f
    private var scrollY  = 0f
    private var maxScrollX = 0f
    private var maxScrollY = 0f

    private fun clampScroll() {
        scrollX = scrollX.coerceIn(0f, maxScrollX.coerceAtLeast(0f))
        scrollY = scrollY.coerceIn(0f, maxScrollY.coerceAtLeast(0f))
    }

    private var focusedRowIndex     = 0
    private var focusedProgramIndex = -1

    private val recFlashRunnable = object : Runnable {
        override fun run() {
            if (recordingChannelUrls.isNotEmpty()) {
                invalidate()
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    private val paintBg              = Paint()
    private val paintSurface         = Paint()
    private val paintSurfaceVar      = Paint()
    private val paintPrimary         = Paint()
    private val paintPrimaryAlpha    = Paint()
    private val paintPrimaryCont     = Paint()
    private val paintHighlight       = Paint()
    private val paintHeader          = Paint()
    private val paintProgramCell     = Paint()
    private val paintProgramNow      = Paint()
    private val paintNowLine         = Paint()
    private val paintTextHeader      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintTextChannel     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintTextProgram     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintTextNow         = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintTextPlaceholder = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintTextOnPrimary   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintStroke          = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintIcon            = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paintDivider         = Paint().apply { strokeWidth = 0.5f * dp }
    private val paintRowTint         = Paint()
    private val paintHeaderDiv       = Paint()

    private val paintCatchupBg    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintCatchupText  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintReminderBg   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintReminderText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintRecDot       = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.FILL }

    private val rectF    = RectF()
    private val clipRect = Rect()

    private fun buildPaints() {
        val c = nexTheme ?: return
        val s = textScale.coerceIn(0.5f, 2.0f)
        val regularTypeface = if (textBold) exo2Bold else exo2Regular

        paintBg.color           = c.background.toArgb()
        paintSurface.color      = c.channelColumnBg.toArgb()
        paintHeader.color       = c.headerBackground.toArgb()
        paintSurfaceVar.color   = c.programCellPlaceholderBg.toArgb()
        paintPrimary.color      = c.programCellFocusedBorder.toArgb()
        paintPrimaryAlpha.color = c.rowFocusedTint.toArgb()
        paintPrimaryCont.color  = c.programCellFocusedBg.toArgb()
        paintHighlight.color    = c.programCellFocusedBg.toArgb()
        paintProgramCell.color  = c.programCellBg.toArgb()
        paintProgramNow.color   = c.programCellNowBg.toArgb()
        paintNowLine.color      = c.nowLine.toArgb()
        paintNowLine.strokeWidth = 2f * dp
        paintNowLine.style      = Paint.Style.STROKE
        paintStroke.color       = c.programCellFocusedBorder.toArgb()
        paintStroke.strokeWidth = 2f * dp
        paintStroke.style       = Paint.Style.STROKE
        paintDivider.color      = c.divider.toArgb()
        paintRowTint.color      = c.rowFocusedTint.toArgb()
        paintHeaderDiv.color    = c.headerDivider.toArgb()

        paintTextHeader.color    = c.headerTimeText.toArgb()
        paintTextHeader.textSize = 14f * dp * s
        paintTextHeader.typeface = regularTypeface

        paintTextChannel.color     = c.channelNameText.toArgb()
        paintTextChannel.textSize  = 13f * dp * s
        paintTextChannel.textAlign = Paint.Align.CENTER
        paintTextChannel.typeface  = exo2Bold

        paintTextProgram.color    = c.programText.toArgb()
        paintTextProgram.textSize = 13f * dp * s
        paintTextProgram.typeface = regularTypeface

        paintTextNow.color    = c.programTextNow.toArgb()
        paintTextNow.textSize = 13f * dp * s
        paintTextNow.typeface = regularTypeface

        paintTextPlaceholder.color    = c.programTextPlaceholder.toArgb()
        paintTextPlaceholder.textSize = 13f * dp * s
        paintTextPlaceholder.typeface = regularTypeface

        paintTextOnPrimary.color    = c.programTextFocused.toArgb()
        paintTextOnPrimary.textSize = 13f * dp * s
        paintTextOnPrimary.typeface = exo2Bold

        // ── Badge paints — colours were missing, causing invisible text ──────
        paintCatchupBg.color       = c.badgeCatchupBg.toArgb()
        paintCatchupText.color     = c.badgeCatchupText.toArgb()
        paintCatchupText.textSize  = 10f * dp * s
        paintCatchupText.typeface  = exo2Bold
        paintCatchupText.textAlign = Paint.Align.CENTER

        paintReminderBg.color       = c.badgeReminderBg.toArgb()
        paintReminderText.color     = c.badgeReminderText.toArgb()
        paintReminderText.textSize  = 10f * dp * s
        paintReminderText.typeface  = exo2Bold
        paintReminderText.textAlign = Paint.Align.CENTER
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private val logoCache = HashMap<String, Bitmap?>()

    private val imageLoader by lazy { coil.Coil.imageLoader(context) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val nowLineRunnable = object : Runnable {
        override fun run() {
            postInvalidate()
            mainHandler.postDelayed(this, 10_000L)
        }
    }
    private val MAX_LOGO_CACHE = 60
    private val logoCacheOrder = ArrayDeque<String>()

    private fun loadLogosForChannels(channelIds: List<String>) {
        channelIds.forEach { id ->
            if (logoCache.containsKey(id)) return@forEach
            val ch = channels.firstOrNull { it.id == id } ?: return@forEach
            val url = ch.logoUrl ?: run { logoCache[id] = null; return@forEach }
            logoCache[id] = null
            logoCacheOrder.addLast(id)
            while (logoCacheOrder.size > MAX_LOGO_CACHE) {
                val evict = logoCacheOrder.removeFirst()
                logoCache.remove(evict)
            }
            val req = ImageRequest.Builder(context)
                .data(url)
                .size(iconSizePx, iconSizePx)
                .allowHardware(false)
                .target(
                    onSuccess = { drawable ->
                        val bmp = (drawable as? BitmapDrawable)?.bitmap
                        mainHandler.post { logoCache[id] = bmp; invalidate() }
                    },
                    onError = { mainHandler.post { logoCache[id] = null } }
                )
                .build()
            imageLoader.enqueue(req)
        }
    }

    private val initialsBitmapCache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val remove = size > MAX_LOGO_CACHE
            if (remove) eldest?.value?.recycle()
            return remove
        }
    }

    private fun getInitialsBitmap(channel: ChannelEntity): Bitmap {
        return initialsBitmapCache.getOrPut(channel.id) {
            val size = iconSizePx
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val bg = Paint().apply { color = nexTheme?.programCellFocusedBg?.toArgb() ?: Color.DKGRAY }
            val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = nexTheme?.programTextFocused?.toArgb() ?: Color.WHITE
                textSize = size * 0.38f
                textAlign = Paint.Align.CENTER
                typeface = exo2Bold
            }
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 4f * dp, 4f * dp, bg)
            val initials = channel.name.take(2).uppercase()
            val yOff = (txt.descent() - txt.ascent()) / 2 - txt.descent()
            canvas.drawText(initials, size / 2f, size / 2f + yOff, txt)
            bmp
        }
    }

    private val timeFmt       = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val cornerTimeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayFmt        = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    private val MAX_FILLED_CACHE = 80
    private val filledCache = LinkedHashMap<String, List<EPGProgram>>(MAX_FILLED_CACHE, 0.75f, true)

    private fun getFilledPrograms(channel: ChannelEntity): List<EPGProgram> {
        val key = channel.id + "_" + (programsMap[channel.epgChannelId?.takeIf { it.isNotEmpty() }]?.size
            ?: programsMap[channel.id]?.size ?: 0)
        filledCache[key]?.let { return it }
        val raw = programsMap[channel.epgChannelId?.takeIf { it.isNotEmpty() }]
            ?: programsMap[channel.id]
            ?: emptyList()
        val result = buildFilled(raw, channel)
        filledCache[key] = result
        if (filledCache.size > MAX_FILLED_CACHE) filledCache.remove(filledCache.keys.first())
        return result
    }

    private fun buildFilled(programs: List<EPGProgram>, channel: ChannelEntity): List<EPGProgram> {
        val endOfWindow = startOfWindow + totalMinutes * 60_000L
        val chId = channel.epgChannelId ?: channel.id
        if (programs.isEmpty()) return generatePlaceholders(chId)
        val sorted = programs.sortedBy { it.entity.startTime }
        val result = mutableListOf<EPGProgram>()
        var cursor = startOfWindow
        val minMs = 60_000L
        for (p in sorted) {
            val s = maxOf((p.entity.startTime / minMs) * minMs, cursor, startOfWindow)
            val e = maxOf(((p.entity.endTime + minMs - 1) / minMs) * minMs, s + minMs)
            if (s > cursor) result += placeholder(chId, cursor, s)
            if (e > cursor) {
                result += if (p.entity.title.isBlank()) placeholder(chId, s, e)
                else EPGProgram(p.entity.copy(startTime = s, endTime = e), false)
                cursor = e
            }
        }
        if (cursor < endOfWindow) result += placeholder(chId, cursor, endOfWindow)
        return result
    }

    private fun generatePlaceholders(chId: String): List<EPGProgram> =
        (0 until totalMinutes / 60).map { i ->
            placeholder(chId, startOfWindow + i * 60 * 60_000L, startOfWindow + (i + 1) * 60 * 60_000L)
        }

    private fun placeholder(chId: String, start: Long, end: Long) = EPGProgram(
        ProgramEntity("ph-$chId-$start", chId, "No Information Provided", null, start, end, null, null),
        isPlaceholder = true
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maxScrollX = (totalWidthPx - (w - channelColPx)).coerceAtLeast(0f)
        maxScrollY = (channels.size * rowHeightPx - (h - headerHeightPx)).coerceAtLeast(0f)
        clampScroll()
    }

    override fun onDraw(canvas: Canvas) {
        if (nexTheme == null || channels.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val now = System.currentTimeMillis()

        canvas.drawRect(0f, 0f, w, h, paintBg)

        canvas.save()
        canvas.clipRect(channelColPx, headerHeightPx, w, h)
        drawProgramRows(canvas, w, h, now)
        drawNowLine(canvas, h, now)
        canvas.restore()

        canvas.save()
        canvas.clipRect(0f, headerHeightPx, channelColPx, h)
        drawChannelColumn(canvas, h)
        canvas.restore()

        canvas.save()
        canvas.clipRect(channelColPx, 0f, w, headerHeightPx)
        drawTimeHeader(canvas, w)
        canvas.restore()

        canvas.drawRect(0f, 0f, channelColPx, headerHeightPx, paintHeader)

        val todayMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24 * 60 * 60_000L
        val viewportStartMs = startOfWindow + (scrollX / dpPerMinutePx * 60_000f).toLong()
        val currentDayStart = (viewportStartMs / dayMs) * dayMs
        val cornerLabel = when (currentDayStart) {
            todayMs - dayMs -> "Yesterday"
            todayMs         -> "Today"
            todayMs + dayMs -> "Tomorrow"
            else            -> dayFmt.format(Date(currentDayStart))
        }
        val savedTypeface = paintTextHeader.typeface
        paintTextHeader.typeface = exo2Bold
        val cornerLabelW = paintTextHeader.measureText(cornerLabel)
        val cornerDayY = headerHeightPx / 2f - paintTextHeader.textSize / 4f
        canvas.drawText(cornerLabel, (channelColPx - cornerLabelW) / 2f, cornerDayY, paintTextHeader)
        paintTextHeader.typeface = savedTypeface
        val timeNowStr = cornerTimeFmt.format(Date(System.currentTimeMillis()))
        val timeNowW = paintTextHeader.measureText(timeNowStr)
        val cornerTimeY = cornerDayY + paintTextHeader.textSize + 2 * dp
        canvas.drawText(timeNowStr, (channelColPx - timeNowW) / 2f, cornerTimeY, paintTextHeader)

        canvas.drawLine(0f, headerHeightPx, w, headerHeightPx, paintHeaderDiv)

        reportVisibleChannels()
    }

    private fun drawTimeHeader(canvas: Canvas, w: Float) {
        val slotMinutes = 30
        val slotPx = slotMinutes * dpPerMinutePx
        val firstSlot = (scrollX / slotPx).toInt()
        val lastSlot  = ((scrollX + width - channelColPx) / slotPx).toInt() + 1
        val yPx = headerHeightPx / 2f + paintTextHeader.textSize / 3f
        for (i in firstSlot..lastSlot.coerceAtMost(totalMinutes / slotMinutes - 1)) {
            val slotTime = startOfWindow + i.toLong() * slotMinutes * 60_000L
            val xPx = channelColPx + i * slotPx - scrollX + 6 * dp
            canvas.drawText(timeFmt.format(Date(slotTime)), xPx, yPx, paintTextHeader)
        }
    }

    private fun drawChannelColumn(canvas: Canvas, h: Float) {
        val firstRow = (scrollY / rowHeightPx).toInt().coerceAtLeast(0)
        val lastRow  = ((scrollY + h - headerHeightPx) / rowHeightPx).toInt().coerceAtMost(channels.size - 1)

        for (i in firstRow..lastRow) {
            val ch  = channels[i]
            val top = headerHeightPx + i * rowHeightPx - scrollY
            val bot = top + rowHeightPx
            val mid = (top + bot) / 2f

            canvas.drawRect(0f, top, channelColPx, bot,
                if (i == focusedRowIndex) paintRowTint else paintSurface)

            val bmp     = logoCache[ch.id] ?: getInitialsBitmap(ch)
            val bmpSize = iconSizePx.toFloat()
            val bmpLeft = (channelColPx - bmpSize) / 2f
            val bmpTop  = mid - bmpSize / 2f - paintTextChannel.textSize / 2f
            canvas.drawBitmap(bmp, bmpLeft, bmpTop, paintIcon)

            val nameY = bmpTop + bmpSize + paintTextChannel.textSize + 2 * dp
            val name  = ellipsize(ch.name, paintTextChannel, channelColPx - 8 * dp)
            canvas.drawText(name, channelColPx / 2f, nameY.coerceAtMost(bot - 4 * dp), paintTextChannel)

            canvas.drawLine(0f, bot, channelColPx, bot, paintDivider)
        }
    }

    private fun drawProgramRows(canvas: Canvas, w: Float, h: Float, now: Long) {
        val firstRow = (scrollY / rowHeightPx).toInt().coerceAtLeast(0)
        val lastRow  = ((scrollY + h - headerHeightPx) / rowHeightPx).toInt().coerceAtMost(channels.size - 1)
        val flashOn  = (now / 500) % 2 == 0L

        for (rowIdx in firstRow..lastRow) {
            val ch           = channels[rowIdx]
            val rowTop       = headerHeightPx + rowIdx * rowHeightPx - scrollY
            val rowBot       = rowTop + rowHeightPx
            val isFocusedRow = rowIdx == focusedRowIndex
            val isRecording  = ch.streamUrl.isNotEmpty() && ch.streamUrl in recordingChannelUrls

            if (isFocusedRow) canvas.drawRect(channelColPx, rowTop, w, rowBot, paintRowTint)

            val programs = getFilledPrograms(ch)
            for ((progIdx, prog) in programs.withIndex()) {
                val pStart = prog.entity.startTime
                val pEnd   = prog.entity.endTime
                val xStart = channelColPx + (pStart - startOfWindow) / 60_000f * dpPerMinutePx - scrollX
                val xEnd   = channelColPx + (pEnd   - startOfWindow) / 60_000f * dpPerMinutePx - scrollX

                if (xEnd < channelColPx || xStart > w) continue

                val cellLeft   = xStart + cellMargin
                val cellRight  = xEnd   - cellMargin
                val cellTop    = rowTop  + cellMargin
                val cellBottom = rowBot  - cellMargin

                if (cellRight - cellLeft < 1f) continue

                val resolvedIdx = resolvedFocusedProgramIndex(rowIdx, programs)
                val isFocused   = isFocusedRow && progIdx == resolvedIdx
                val isNow       = now in pStart..pEnd

                val bgPaint = when {
                    isFocused          -> paintPrimaryCont
                    isFocusedRow       -> paintPrimaryAlpha
                    prog.isPlaceholder -> paintSurfaceVar
                    isNow              -> paintProgramNow
                    else               -> paintProgramCell
                }
                rectF.set(cellLeft, cellTop, cellRight, cellBottom)
                canvas.drawRoundRect(rectF, cellRadius, cellRadius, bgPaint)
                if (isFocused) canvas.drawRoundRect(rectF, cellRadius, cellRadius, paintStroke)

                val textPaint = when {
                    isFocused          -> paintTextOnPrimary
                    prog.isPlaceholder -> paintTextPlaceholder
                    isNow              -> paintTextNow
                    else               -> paintTextProgram
                }
                // Clamp to visible area — cells that started before the scroll position
                // would otherwise draw their text off-screen to the left.
                val visibleCellLeft = maxOf(cellLeft, channelColPx)
                val textMaxW = (cellRight - visibleCellLeft - 12 * dp).coerceAtLeast(0f)
                if (textMaxW > 20 * dp) {
                    val midY = (cellTop + cellBottom) / 2f
                    val line1 = ellipsize(prog.entity.title, textPaint, textMaxW)
                    canvas.drawText(line1, visibleCellLeft + 6 * dp, midY + textPaint.textSize / 3f, textPaint)
                }

                val remId = "${ch.id}_${prog.entity.startTime}"
                if (!prog.isPlaceholder && pStart > now && remId in reminderIds) {
                    drawReminderBadge(canvas, cellRight, cellTop)
                }

                if (!prog.isPlaceholder && pEnd < now && ch.tvArchive != 0) {
                    drawCatchupBadge(canvas, cellRight, cellBottom)
                }

                // Flashing red dot for channels currently being recorded
                if (isRecording && isNow && !prog.isPlaceholder && flashOn) {
                    val dotR  = 5f * dp
                    val dotX  = (cellRight - dotR - 4 * dp).coerceAtMost(w - dotR - 2 * dp)
                    val dotY  = cellTop + dotR + 4 * dp
                    if (dotX > channelColPx) canvas.drawCircle(dotX, dotY, dotR, paintRecDot)
                }
            }

            canvas.drawLine(channelColPx, rowBot, w, rowBot, paintDivider)
        }
    }

    private fun drawNowLine(canvas: Canvas, h: Float, now: Long) {
        val xNow = channelColPx + (now - startOfWindow) / 60_000f * dpPerMinutePx - scrollX
        if (xNow < channelColPx || xNow > width) return
        canvas.drawLine(xNow, headerHeightPx, xNow, h, paintNowLine)
    }

    private fun drawCatchupBadge(canvas: Canvas, cellRight: Float, cellBottom: Float) {
        val size   = 16f * dp
        val right  = cellRight  - 4 * dp
        val bottom = cellBottom - 4 * dp
        val left   = right  - size
        val top    = bottom - size
        rectF.set(left - 2 * dp, top - 2 * dp, right + 2 * dp, bottom + 2 * dp)
        canvas.drawRoundRect(rectF, 3f * dp, 3f * dp, paintCatchupBg)

        // Draw a replay/history arrow icon using canvas primitives
        val cx     = (left + right) / 2f
        val cy     = (top  + bottom) / 2f
        val radius = size * 0.32f
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = paintCatchupText.color
            style       = Paint.Style.STROKE
            strokeWidth = 1.6f * dp
            strokeCap   = Paint.Cap.ROUND
        }
        // Arc ~300 degrees (leaving a gap at top-right for the arrowhead)
        val arcOval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arcOval, -60f, 300f, false, iconPaint)
        // Arrowhead pointing clockwise at the end of the arc (around -60deg = top-right)
        val arrowX = cx + radius * kotlin.math.cos(Math.toRadians(-60.0)).toFloat()
        val arrowY = cy + radius * kotlin.math.sin(Math.toRadians(-60.0)).toFloat()
        val arrowLen = 3f * dp
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = paintCatchupText.color
            style       = Paint.Style.STROKE
            strokeWidth = 1.6f * dp
            strokeCap   = Paint.Cap.ROUND
        }
        canvas.drawLine(arrowX, arrowY, arrowX + arrowLen, arrowY - arrowLen * 0.3f, arrowPaint)
        canvas.drawLine(arrowX, arrowY, arrowX + arrowLen * 0.3f, arrowY + arrowLen, arrowPaint)
    }

    private fun drawReminderBadge(canvas: Canvas, cellRight: Float, cellTop: Float) {
        val size  = 16f * dp
        val right = cellRight - 4 * dp
        val top   = cellTop   + 4 * dp
        val left  = right - size
        val bot   = top   + size
        rectF.set(left - 2 * dp, top - 2 * dp, right + 2 * dp, bot + 2 * dp)
        canvas.drawRoundRect(rectF, 3f * dp, 3f * dp, paintReminderBg)

        // Draw a bell icon using canvas primitives
        val cx       = (left + right) / 2f
        val cy       = (top  + bot)   / 2f
        val bellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = paintReminderText.color
            style       = Paint.Style.STROKE
            strokeWidth = 1.6f * dp
            strokeCap   = Paint.Cap.ROUND
            strokeJoin  = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paintReminderText.color
            style = Paint.Style.FILL
        }
        val bw = size * 0.38f   // bell body half-width at bottom
        val bh = size * 0.34f   // bell body height
        val bTop = cy - bh * 0.55f

        // Bell dome (arc)
        val domeRect = RectF(cx - bw * 0.9f, bTop, cx + bw * 0.9f, bTop + bh * 1.1f)
        canvas.drawArc(domeRect, 180f, 180f, false, bellPaint)
        // Bell sides down to the rim
        canvas.drawLine(cx - bw * 0.9f, bTop + bh * 0.55f, cx - bw, cy + bh * 0.3f, bellPaint)
        canvas.drawLine(cx + bw * 0.9f, bTop + bh * 0.55f, cx + bw, cy + bh * 0.3f, bellPaint)
        // Rim (horizontal bar at bottom of bell)
        canvas.drawLine(cx - bw * 1.2f, cy + bh * 0.3f, cx + bw * 1.2f, cy + bh * 0.3f, bellPaint)
        // Clapper (small circle at very bottom)
        canvas.drawCircle(cx, cy + bh * 0.55f, 1.4f * dp, fillPaint)
        // Stem at top
        canvas.drawLine(cx, bTop - 1.5f * dp, cx, bTop + 1f * dp, bellPaint)
    }

    private fun resolvedFocusedProgramIndex(rowIdx: Int, programs: List<EPGProgram>): Int {
        if (focusedProgramIndex >= 0 && rowIdx == focusedRowIndex) return focusedProgramIndex
        val now = System.currentTimeMillis()
        return programs.indexOfFirst { now in it.entity.startTime..it.entity.endTime }
            .takeIf { it >= 0 } ?: 0
    }

    private fun focusedPrograms(): List<EPGProgram>? {
        val ch = channels.getOrNull(focusedRowIndex) ?: return null
        return getFilledPrograms(ch)
    }

    private fun notifyFocusedProgramChanged() {
        val ch = channels.getOrNull(focusedRowIndex) ?: return
        val programs = focusedPrograms()
        val pIdx = programs?.let { resolvedFocusedProgramIndex(focusedRowIndex, it) } ?: -1
        val prog = programs?.getOrNull(pIdx)?.entity
        callbacks?.onFocusedProgramChanged(ch, prog)
    }

    fun notifyInitialFocus() {
        if (channels.isEmpty()) return
        notifyFocusedProgramChanged()
    }

    private fun scrollToCenterProgram(progIdx: Int, programs: List<EPGProgram>) {
        val prog = programs.getOrNull(progIdx) ?: return
        val mid = (prog.entity.startTime + prog.entity.endTime) / 2f
        val targetX = (mid - startOfWindow) / 60_000f * dpPerMinutePx - (width - channelColPx) / 2f
        scrollX = targetX.coerceIn(0f, maxScrollX.coerceAtLeast(0f))
    }

    private fun scrollToNow() {
        val now = System.currentTimeMillis()
        val targetX = (now - startOfWindow) / 60_000f * dpPerMinutePx - (width - channelColPx) / 2f
        scrollX = targetX.coerceIn(0f, maxScrollX.coerceAtLeast(0f))
        invalidate()
    }

    private fun ensureRowVisible(rowIdx: Int) {
        val rowTop = rowIdx * rowHeightPx
        val rowBot = rowTop + rowHeightPx
        val viewTop = scrollY
        val viewBot = scrollY + height - headerHeightPx
        when {
            rowTop < viewTop + rowHeightPx -> scrollY = (rowTop - rowHeightPx).coerceAtLeast(0f)
            rowBot > viewBot - rowHeightPx -> scrollY = (rowBot - (height - headerHeightPx) + rowHeightPx)
                .coerceAtMost(maxScrollY.coerceAtLeast(0f))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val programs = focusedPrograms() ?: return super.onKeyDown(keyCode, event)
        val curPIdx  = resolvedFocusedProgramIndex(focusedRowIndex, programs)

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val next = programs.indexOfFirst { it.entity.startTime >= programs[curPIdx].entity.endTime }
                if (next >= 0) { focusedProgramIndex = next; scrollToCenterProgram(next, programs); invalidate(); notifyFocusedProgramChanged(); true }
                else false
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val cur  = programs[curPIdx]
                val prev = programs.indexOfLast { it.entity.endTime <= cur.entity.startTime }
                if (prev >= 0) { focusedProgramIndex = prev; scrollToCenterProgram(prev, programs); invalidate(); notifyFocusedProgramChanged(); true }
                else { callbacks?.onBack(); true }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focusedRowIndex < channels.size - 1) {
                    focusedRowIndex++; focusedProgramIndex = -1
                    ensureRowVisible(focusedRowIndex); clampScroll(); invalidate(); notifyFocusedProgramChanged(); true
                } else false
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (focusedRowIndex > 0) {
                    focusedRowIndex--; focusedProgramIndex = -1
                    ensureRowVisible(focusedRowIndex); clampScroll(); invalidate(); notifyFocusedProgramChanged(); true
                } else {
                    callbacks?.onFocusUp(); true
                }
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                val ch   = channels.getOrNull(focusedRowIndex) ?: return false
                val prog = programs.getOrNull(curPIdx) ?: return false
                callbacks?.onProgramSelected(ch, prog.entity, prog.isPlaceholder); true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!playerVisible) { callbacks?.onBack(); true }
                else false
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastTouchX  = 0f
    private var lastTouchY  = 0f
    private var isDragging  = false
    private val touchSlop   = ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                touchStartX = event.x; touchStartY = event.y
                lastTouchX  = event.x; lastTouchY  = event.y
                isDragging  = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = lastTouchX - event.x
                val dy = lastTouchY - event.y
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) isDragging = true
                if (isDragging) {
                    scrollX = (scrollX + dx).coerceIn(0f, maxScrollX.coerceAtLeast(0f))
                    scrollY = (scrollY + dy).coerceIn(0f, maxScrollY.coerceAtLeast(0f))
                    lastTouchX = event.x; lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> { if (!isDragging) handleTap(event.x, event.y) }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val rowIdx = ((y - headerHeightPx + scrollY) / rowHeightPx).toInt().coerceIn(0, channels.size - 1)
        val ch = channels.getOrNull(rowIdx) ?: return
        if (x < channelColPx) {
            callbacks?.onChannelClicked(ch)
            return
        }
        val timeMs = startOfWindow + ((x - channelColPx + scrollX) / dpPerMinutePx * 60_000f).toLong()
        val programs = getFilledPrograms(ch)
        val prog = programs.firstOrNull { timeMs in it.entity.startTime..it.entity.endTime }
            ?: programs.firstOrNull { System.currentTimeMillis() in it.entity.startTime..it.entity.endTime }
            ?: programs.firstOrNull() ?: return
        val progIdx = programs.indexOf(prog)
        focusedRowIndex = rowIdx
        focusedProgramIndex = progIdx
        invalidate()
        notifyFocusedProgramChanged()
        callbacks?.onProgramSelected(ch, prog.entity, prog.isPlaceholder)
    }

    private var lastReportedIds = emptyList<String>()

    private fun reportVisibleChannels() {
        val h = height.toFloat()
        val first = (scrollY / rowHeightPx).toInt().coerceAtLeast(0)
        val last  = ((scrollY + h - headerHeightPx) / rowHeightPx).toInt().coerceAtMost(channels.size - 1)
        val ids = ((first - 3).coerceAtLeast(0)..(last + 3).coerceAtMost(channels.size - 1))
            .mapNotNull { channels.getOrNull(it)?.epgChannelId?.takeIf { id -> id.isNotEmpty() } }
        if (ids != lastReportedIds) {
            lastReportedIds = ids
            callbacks?.onVisibleChannelIdsChanged(ids)
            val visibleChannelIds = ((first - 5).coerceAtLeast(0)..(last + 5).coerceAtMost(channels.size - 1))
                .mapNotNull { channels.getOrNull(it)?.id }
            loadLogosForChannels(visibleChannelIds)
        }
    }

    fun jumpToNow()             { scrollToNow(); focusedProgramIndex = -1; invalidate() }
    fun scrollToTop()           { scrollY = 0f; focusedRowIndex = 0; focusedProgramIndex = -1; invalidate() }
    fun invalidateFilledCache() { filledCache.clear(); invalidate() }

    fun scrollToChannelByName(name: String) {
        val rowIdx = channels.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (rowIdx < 0) return
        focusedRowIndex = rowIdx
        focusedProgramIndex = -1
        scrollToNow()
        val rowTop = rowIdx * rowHeightPx
        val viewH  = (height - headerHeightPx).coerceAtLeast(1f)
        scrollY = (rowTop - viewH / 2f + rowHeightPx / 2f).coerceIn(0f, maxScrollY.coerceAtLeast(0f))
        invalidate()
    }

    fun syncFocusedChannelByUrl(streamUrl: String) {
        val rowIdx = channels.indexOfFirst { it.streamUrl == streamUrl }
        if (rowIdx < 0) return
        focusedRowIndex = rowIdx
        focusedProgramIndex = -1
        ensureRowVisible(focusedRowIndex)
        clampScroll()
        invalidate()
    }
    fun requestGridFocus() {
        android.util.Log.d("FocusRestore", "requestGridFocus called, isFocusable=$isFocusable, windowFocused=${hasWindowFocus()}")
        mainHandler.post {
            val result = requestFocus()
            android.util.Log.d("FocusRestore", "requestFocus result=$result, isFocused=$isFocused")
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(nowLineRunnable)
        mainHandler.removeCallbacks(recFlashRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        computeTimeWindow()
        isFocusable = true
        isFocusableInTouchMode = true
        //requestFocus()
        mainHandler.post(nowLineRunnable)
        setOnFocusChangeListener { _, hasFocus ->
            android.util.Log.d("FocusRestore", "EPGGridView focus changed: hasFocus=$hasFocus")
            if (!hasFocus) {
                android.util.Log.d("FocusRestore", "EPGGridView LOST focus - stack: ${Thread.currentThread().stackTrace.take(8).joinToString(" <- ") { it.methodName }}")
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            maxScrollX = (totalWidthPx - (width - channelColPx)).coerceAtLeast(0f)
            maxScrollY = (channels.size * rowHeightPx - (height - headerHeightPx)).coerceAtLeast(0f)
            clampScroll()
            mainHandler.post { scrollToNow(); callbacks?.onReady() }
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis  = "..."
        val ellipsisW = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end)) + ellipsisW > maxWidth) end--
        return text.substring(0, end) + ellipsis
    }
}