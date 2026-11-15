package com.qingluan.schedule.data

/** Reset only timetable presentation; preserve theme, startup, reminders and user data. */
fun AppSettings.resetScheduleAppearance(): AppSettings {
    val d = AppSettings()
    return copy(
        rowHeight = d.rowHeight,
        courseFontScale = d.courseFontScale,
        courseCornerRadius = d.courseCornerRadius,
        courseOpacity = d.courseOpacity,
        showGridLines = d.showGridLines,
        showSectionTime = d.showSectionTime,
        showDates = d.showDates,
        showTeacher = d.showTeacher,
        showLocation = d.showLocation,
        centerCourseText = d.centerCourseText,
        timeColumnWidth = d.timeColumnWidth,
        dayHeaderHeight = d.dayHeaderHeight,
        courseOuterPadding = d.courseOuterPadding,
        courseInnerPadding = d.courseInnerPadding,
        centerCourseVertically = d.centerCourseVertically,
        boldCourseNames = d.boldCourseNames,
        showCourseBorders = d.showCourseBorders,
        showCourseStartTime = d.showCourseStartTime,
    )
}
