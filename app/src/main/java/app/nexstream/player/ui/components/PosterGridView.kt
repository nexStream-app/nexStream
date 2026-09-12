package app.nexstream.player.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load

// ── Data model ────────────────────────────────────────────────────────────────

data class PosterItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val badge: String? = null,
    val showProgressBadge: Boolean = false,
    val isBookmarked: Boolean = false,
    val certification: String? = null,
    val rating: String? = null
)

// ── Callbacks ─────────────────────────────────────────────────────────────────

interface PosterGridCallbacks {
    fun onItemClick(item: PosterItem, index: Int)
    fun onItemLongClick(item: PosterItem, index: Int)
    fun onItemFocused(index: Int)
    fun onLeftEdge()
    fun onTopEdge()
    fun onBottomEdge() {}
}

// ── Grid spacing ──────────────────────────────────────────────────────────────

class GridSpacingDecoration(
    private val spanCount: Int,
    private val spacingPx: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position: Int = parent.getChildAdapterPosition(view)
        val column: Int   = position % spanCount
        outRect.left   = spacingPx - column * spacingPx / spanCount
        outRect.right  = (column + 1) * spacingPx / spanCount
        outRect.top    = spacingPx
        outRect.bottom = 0
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class PosterAdapter(
    private val onItemClick: (PosterItem, Int) -> Unit,
    private val onItemLongClick: (PosterItem, Int) -> Unit,
    private val onItemFocused: (Int) -> Unit,
    private val onFocusChanged: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    private var _items: List<PosterItem> = emptyList()

    val items: List<PosterItem> get() = _items
    fun submitList(newItems: List<PosterItem>) { _items = newItems; notifyDataSetChanged() }

    var primaryColor: Int = 0xFF6200EE.toInt()
    var onPrimaryColor: Int = 0xFFFFFFFF.toInt()
    var tertiaryColor: Int = 0xFF018786.toInt()
    var onTertiaryColor: Int = 0xFFFFFFFF.toInt()
    var surfaceVariantColor: Int = 0xFF333333.toInt()
    var surfaceColor: Int = 0xFF0D0D1A.toInt()
    var onSurfaceColor: Int = 0xFFC8D8FF.toInt()
    var typeface: Typeface = Typeface.DEFAULT
    var columnCount: Int = 1
    var onLeftEdge: (() -> Unit)? = null
    var onTopEdge: (() -> Unit)? = null
    var onBottomEdge: (() -> Unit)? = null

    override fun getItemCount() = _items.size

    fun setItemsFocusable(focusable: Boolean) {
        _itemsFocusable = focusable
        // No notifyItemRangeChanged — PosterGridView updates currently attached views directly
        // to avoid the rebind flash (image reload + scale reset on every item).
    }
    private var _itemsFocusable = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = PosterItemView(parent.context)
        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.view.bind(item, primaryColor, onPrimaryColor, tertiaryColor, onTertiaryColor, surfaceVariantColor, surfaceColor, onSurfaceColor, typeface)
        holder.view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) onItemClick(items[pos], pos)
        }
        holder.view.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) onItemLongClick(items[pos], pos)
            true
        }
        holder.view.setOnFocusChangeListener { _, hasFocus ->
            val pos = holder.bindingAdapterPosition
            if (hasFocus && pos != RecyclerView.NO_ID.toInt()) onItemFocused(pos)
            onFocusChanged(position, hasFocus)
        }
        // Items are focusable but traversal is blocked at PosterGridView level
        holder.view.isFocusable = _itemsFocusable
        holder.view.isFocusableInTouchMode = false
        holder.view.isClickable = true
        holder.view.isLongClickable = true
    }

    class VH(val view: PosterItemView) : RecyclerView.ViewHolder(view)
}

// ── PosterGridView ────────────────────────────────────────────────────────────

class PosterGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var callbacks: PosterGridCallbacks? = null
        set(value) {
            field = value
            posterAdapter.onLeftEdge   = { value?.onLeftEdge() }
            posterAdapter.onTopEdge    = { value?.onTopEdge() }
            posterAdapter.onBottomEdge = { value?.onBottomEdge() }
        }

    private var gridLayoutManager: GridLayoutManager? = null
    private var focusedIndex: Int = 0
    private var edgeKeyDownTime = 0L
    private var edgeFired = false

    private val posterAdapter = PosterAdapter(
        onItemClick     = { item, idx -> callbacks?.onItemClick(item, idx) },
        onItemLongClick = { item, idx -> callbacks?.onItemLongClick(item, idx) },
        onItemFocused   = { idx ->
            focusedIndex = idx
            callbacks?.onItemFocused(idx)
        },
        onFocusChanged  = { idx, hasFocus ->
            // Access recyclerView via getter to avoid forward-reference issue at init time
            val vh: RecyclerView.ViewHolder? = getChildAt(0)
                ?.let { it as? RecyclerView }
                ?.findViewHolderForAdapterPosition(idx)
            (vh as? PosterAdapter.VH)?.view?.setFocused(hasFocus, primaryColor)
        }
    )

    var primaryColor: Int = 0xFF6200EE.toInt()
        set(value) { field = value; posterAdapter.primaryColor = value }
    var onPrimaryColor: Int = 0xFFFFFFFF.toInt()
        set(value) { field = value; posterAdapter.onPrimaryColor = value }
    var tertiaryColor: Int = 0xFF018786.toInt()
        set(value) { field = value; posterAdapter.tertiaryColor = value }
    var onTertiaryColor: Int = 0xFFFFFFFF.toInt()
        set(value) { field = value; posterAdapter.onTertiaryColor = value }
    var surfaceVariantColor: Int = 0xFF333333.toInt()
        set(value) { field = value; posterAdapter.surfaceVariantColor = value }
    var surfaceColor: Int = 0xFF0D0D1A.toInt()
        set(value) { field = value; posterAdapter.surfaceColor = value }
    var onSurfaceColor: Int = 0xFFC8D8FF.toInt()
        set(value) { field = value; posterAdapter.onSurfaceColor = value }
    var typeface: Typeface = Typeface.DEFAULT
        set(value) { field = value; posterAdapter.typeface = value }
    var alwaysOnLeftEdge: Boolean = false

    private val recyclerView = object : RecyclerView(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_UP) {
                edgeKeyDownTime = 0L
                edgeFired = false
                return super.dispatchKeyEvent(event)
            }
            if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
            val focused = focusedChild ?: return super.dispatchKeyEvent(event)
            val pos = getChildAdapterPosition(focused)
            if (pos == RecyclerView.NO_ID.toInt()) return super.dispatchKeyEvent(event)
            val cols = posterAdapter.columnCount
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    edgeKeyDownTime = 0L; edgeFired = false
                    if (alwaysOnLeftEdge || pos % cols == 0) {
                        posterAdapter.onLeftEdge?.invoke()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (pos / cols == 0) {
                        if (event.repeatCount == 0) {
                            posterAdapter.onTopEdge?.invoke()
                        }
                        return true
                    } else {
                        edgeKeyDownTime = 0L; edgeFired = false
                        // Check target row is bound before allowing focus to move up
                        val targetPos = pos - cols
                        if (targetPos >= 0) {
                            val targetView = findViewHolderForAdapterPosition(targetPos)?.itemView
                            if (targetView == null) {
                                scrollToPosition(targetPos)
                                return true
                            }
                        }
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val totalItems: Int = posterAdapter.itemCount
                    val lastRow: Int = if (totalItems == 0) 0 else (totalItems - 1) / cols
                    if (pos / cols == lastRow) {
                        // Bottom row — consume entirely, never let focus escape downward
                        return true
                    } else {
                        edgeKeyDownTime = 0L; edgeFired = false
                        // Check the target position is already bound — if not, consume
                        // the key to prevent focus escaping while RecyclerView is still
                        // drawing the next row during fast scrolling
                        val targetPos = pos + cols
                        if (targetPos < totalItems) {
                            val targetView = findViewHolderForAdapterPosition(targetPos)?.itemView
                            if (targetView == null) {
                                // Target not yet bound — scroll toward it and consume
                                scrollToPosition(targetPos)
                                return true
                            }
                        }
                    }
                }
                else -> { edgeKeyDownTime = 0L; edgeFired = false }
            }
            return super.dispatchKeyEvent(event)
        }
    }

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        recyclerView.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        recyclerView.clipToPadding = false
        recyclerView.setPadding(dpToPx(32), dpToPx(20), dpToPx(32), dpToPx(16))
        recyclerView.isFocusable = false
        recyclerView.isFocusableInTouchMode = false
        recyclerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        recyclerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS)
        recyclerView.adapter = posterAdapter
        addView(recyclerView)
    }

    val itemCount: Int get() = posterAdapter.itemCount

    fun blockFocus() {
        posterAdapter.setItemsFocusable(false)
        for (i in 0 until recyclerView.childCount) recyclerView.getChildAt(i)?.isFocusable = false
        recyclerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    fun setColumnCount(columns: Int) {
        val lm = GridLayoutManager(context, columns)
        gridLayoutManager = lm
        recyclerView.layoutManager = lm
        posterAdapter.columnCount = columns
        while (recyclerView.itemDecorationCount > 0) recyclerView.removeItemDecorationAt(0)
        recyclerView.addItemDecoration(GridSpacingDecoration(columns, dpToPx(8)))
    }

    fun setItems(newItems: List<PosterItem>) {
        posterAdapter.submitList(newItems)
    }

    fun scrollToIndex(index: Int) {
        recyclerView.scrollToPosition(index)
    }

    // Scroll so the row containing index appears at the top of the visible area
    fun scrollToIndexTop(index: Int) {
        (recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.scrollToPositionWithOffset(index, 0)
            ?: recyclerView.scrollToPosition(index)
    }

    fun requestItemFocus(index: Int) {
        recyclerView.post {
            val ok = requestItemFocusNow(index)
            android.util.Log.d("PosterGridFocus", "posted requestItemFocusNow($index)=$ok childCount=${recyclerView.childCount}")
        }
    }

    fun requestItemFocusNow(index: Int): Boolean {
        posterAdapter.setItemsFocusable(true)
        for (i in 0 until recyclerView.childCount) recyclerView.getChildAt(i)?.isFocusable = true
        recyclerView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val vh = recyclerView.findViewHolderForAdapterPosition(index)
        android.util.Log.d("PosterGridFocus", "requestItemFocusNow($index) childCount=${recyclerView.childCount} vh=${vh != null}")
        if (vh == null) return false
        val focused = vh.itemView.requestFocus()
        android.util.Log.d("PosterGridFocus", "requestFocus($index)=$focused isFocused=${vh.itemView.isFocused}")
        return focused
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusable = false
        isFocusableInTouchMode = false
    }

    // Block Android traversal from entering the grid — only explicit requestItemFocus() calls allowed
    override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean = false

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}

// ── PosterItemView ────────────────────────────────────────────────────────────

class PosterItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val posterImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val gradientOverlay = View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00000000, 0xCC000000.toInt())
        )
    }

    private val titleText = TextView(context).apply {
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 16f
        setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.BOTTOM
        }
    }

    private val seasonBadge = TextView(context).apply {
        textSize = 10f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dpToPx(5), dpToPx(3), dpToPx(5), dpToPx(3))
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            it.topMargin = dpToPx(6); it.rightMargin = dpToPx(6)
        }
        visibility = View.GONE
    }

    private val continueBadge = TextView(context).apply {
        text = "👁"
        textSize = 9f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dpToPx(5), dpToPx(3), dpToPx(5), dpToPx(3))
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            it.topMargin = dpToPx(6); it.rightMargin = dpToPx(6)
        }
        visibility = View.GONE
    }

    private val bookmarkIcon = TextView(context).apply {
        text = "⊟"  // replaced with actual bookmark drawable in bind()
        textSize = 14f
        setPadding(0, 0, dpToPx(6), dpToPx(6))
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
        }
        visibility = View.GONE
    }

    private val certBadge = TextView(context).apply {
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dpToPx(5), dpToPx(2), dpToPx(5), dpToPx(2))
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            it.rightMargin  = dpToPx(6)
            it.bottomMargin = dpToPx(6)
        }
        visibility = View.GONE
    }

    private val ratingBadge = TextView(context).apply {
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dpToPx(5), dpToPx(2), dpToPx(5), dpToPx(2))
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            it.topMargin  = dpToPx(6)
            it.leftMargin = dpToPx(6)
        }
        visibility = View.GONE
    }

    private var bgColor: Int = 0xFF333333.toInt()

    private val normalBg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(8).toFloat()
        setColor(bgColor)
    }
    private val focusBg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(8).toFloat()
        setColor(bgColor)
        setStroke(dpToPx(3), 0xFF6200EE.toInt())
    }

    init {
        clipToOutline = true
        background = normalBg
        posterImage.isClickable = false
        gradientOverlay.isClickable = false
        titleText.isClickable = false
        seasonBadge.isClickable = false
        continueBadge.isClickable = false
        bookmarkIcon.isClickable = false
        certBadge.isClickable = false
        ratingBadge.isClickable = false
        addView(posterImage)
        addView(gradientOverlay)
        addView(titleText)
        addView(seasonBadge)
        addView(continueBadge)
        addView(bookmarkIcon)
        addView(certBadge)
        addView(ratingBadge)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = w * 3 / 2
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    fun bind(
        item: PosterItem,
        primaryColor: Int,
        onPrimaryColor: Int,
        tertiaryColor: Int,
        onTertiaryColor: Int,
        surfaceVariantColor: Int,
        surfaceColor: Int = 0xFF0D0D1A.toInt(),
        onSurfaceColor: Int = 0xFFC8D8FF.toInt(),
        typeface: Typeface = Typeface.DEFAULT
    ) {
        titleText.text = item.name
        titleText.typeface = typeface
        seasonBadge.typeface = typeface
        continueBadge.typeface = typeface

        posterImage.load(item.posterUrl) {
            size(300, 450)
            crossfade(false)
            memoryCacheKey(item.posterUrl ?: item.id)
            diskCacheKey(item.posterUrl ?: item.id)
        }

        bgColor = surfaceVariantColor
        normalBg.setColor(surfaceVariantColor)
        focusBg.setColor(surfaceVariantColor)

        val badgeText = item.badge
        if (badgeText != null) {
            seasonBadge.text = badgeText
            seasonBadge.setTextColor(onSurfaceColor)
            seasonBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(4).toFloat()
                setColor(surfaceColor and 0x00FFFFFF or -0x15000000)
            }
            seasonBadge.visibility = View.VISIBLE
        } else {
            seasonBadge.visibility = View.GONE
        }

        val cert = item.certification
        if (cert != null) {
            certBadge.text = cert
            certBadge.typeface = typeface
            certBadge.setTextColor(0xFFFFFFFF.toInt())
            certBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(0xFFE53935.toInt())
            }
            certBadge.visibility = View.VISIBLE
        } else {
            certBadge.visibility = View.GONE
        }

        val ratingStr = item.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
        if (ratingStr != null) {
            ratingBadge.text = "★ $ratingStr"
            ratingBadge.typeface = typeface
            ratingBadge.setTextColor(0xFFFFFFFF.toInt())
            ratingBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(0xFFE65100.toInt())
            }
            (ratingBadge.layoutParams as? LayoutParams)?.topMargin = dpToPx(6)
            ratingBadge.visibility = View.VISIBLE
        } else {
            ratingBadge.visibility = View.GONE
        }

        if (item.showProgressBadge) {
            continueBadge.setTextColor(onSurfaceColor)
            continueBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(4).toFloat()
                setColor(surfaceColor and 0x00FFFFFF or -0x15000000)
            }
            continueBadge.visibility = View.VISIBLE
        } else {
            continueBadge.visibility = View.GONE
        }

        bookmarkIcon.setTextColor(primaryColor)
        bookmarkIcon.visibility = if (item.isBookmarked) View.VISIBLE else View.GONE
    }

    fun setFocused(focused: Boolean, primaryColor: Int) {
        if (focused) {
            focusBg.setStroke(dpToPx(4), primaryColor)
            focusBg.setColor(bgColor)
            background = focusBg
            scaleX = 1.08f; scaleY = 1.08f
            val b = dpToPx(4)
            (posterImage.layoutParams as? LayoutParams)?.also { lp ->
                lp.setMargins(b, b, b, b)
                posterImage.layoutParams = lp
            }
            posterImage.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(4).toFloat()
                setColor(android.graphics.Color.TRANSPARENT)
            }
            posterImage.clipToOutline = true
            elevation = dpToPx(16).toFloat()
            titleText.alpha = 1f
        } else {
            background = normalBg
            scaleX = 1f; scaleY = 1f
            (posterImage.layoutParams as? LayoutParams)?.also { lp ->
                lp.setMargins(0, 0, 0, 0)
                posterImage.layoutParams = lp
            }
            posterImage.clipToOutline = false
            posterImage.background = null
            elevation = dpToPx(2).toFloat()
            titleText.alpha = 0.85f
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}