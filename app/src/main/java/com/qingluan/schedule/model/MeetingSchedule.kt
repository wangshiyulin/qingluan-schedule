package com.qingluan.schedule.model

private data class MeetingPattern(
    val weekday: Int,
    val weeks: Set<Int>,
    val teacher: String,
    val location: String,
)

/** Only equivalent weekly patterns merge. Referenced IDs and their bounds stay untouched. */
fun Course.coalesced(protectedIds: Set<String> = emptySet()): Course {
    if (meetings.size < 2) return this
    val (protected, free) = meetings.partition { it.id in protectedIds }
    val merged =
        free
            .groupBy {
                MeetingPattern(
                    it.weekday,
                    it.weeks,
                    it.teacher.ifBlank { teacher },
                    it.location.ifBlank { location },
                )
            }
            .values
            .flatMap { group ->
                val result = mutableListOf<Meeting>()
                for (next in group.sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))) {
                    val last = result.lastOrNull()
                    if (last != null && next.startPeriod <= last.endPeriod + 1)
                        result[result.lastIndex] =
                            last.copy(endPeriod = maxOf(last.endPeriod, next.endPeriod))
                    else result += next
                }
                result
            }
    return copy(
        meetings =
            (protected + merged).sortedWith(
                compareBy({ it.weekday }, { it.startPeriod }, { it.endPeriod })
            )
    )
}

/**
 * Validate untrusted snapshots before calling; reuse already-normalized immutable course objects.
 */
fun Snapshot.coalesced(previous: Snapshot? = null): Snapshot {
    val protected =
        exceptions
            .groupBy { it.courseId }
            .mapValues { (_, changes) -> changes.map { it.meetingId }.toSet() }
    val previousProtected =
        previous
            ?.exceptions
            ?.groupBy { it.courseId }
            ?.mapValues { (_, changes) -> changes.map { it.meetingId }.toSet() }
            .orEmpty()
    val previousCourses = previous?.courses?.associateBy { it.id }.orEmpty()
    return copy(
        courses =
            courses.map { course ->
                if (
                    course == previousCourses[course.id] &&
                        protected[course.id] == previousProtected[course.id]
                )
                    course
                else course.coalesced(protected[course.id].orEmpty())
            }
    )
}

fun periodLabel(start: Int, end: Int): String =
    if (start == end) "第 $start 节" else "第 $start–$end 节"
