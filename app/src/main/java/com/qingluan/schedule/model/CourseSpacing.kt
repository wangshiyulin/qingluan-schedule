package com.qingluan.schedule.model

/** Preserve readable text space when overlapping courses share a narrow column. */
data class CourseSpacing(val outer: Float, val inner: Float) {
    fun textWidth(columnWidth: Float) = (columnWidth - 2 * (outer + inner)).coerceAtLeast(1f)
}

fun courseSpacing(
    columnWidth: Float,
    outer: Int,
    inner: Int,
    minimumTextWidth: Float,
): CourseSpacing {
    if (!columnWidth.isFinite() || columnWidth <= 0f) return CourseSpacing(0f, 0f)
    val safeOuter =
        outer
            .coerceAtLeast(0)
            .toFloat()
            .coerceAtMost(
                ((columnWidth - minimumTextWidth.coerceAtLeast(1f)) / 2).coerceAtLeast(0f)
            )
    val maxInner =
        ((columnWidth - 2 * safeOuter - minimumTextWidth.coerceAtLeast(1f)) / 2).coerceAtLeast(0f)
    return CourseSpacing(safeOuter, inner.coerceAtLeast(0).toFloat().coerceAtMost(maxInner))
}
