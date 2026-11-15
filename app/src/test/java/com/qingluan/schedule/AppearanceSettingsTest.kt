package com.qingluan.schedule

import com.qingluan.schedule.data.*
import org.junit.Assert.*
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun oldSettingsHaveSafeDefaultsAndNewSettingsRoundTrip() {
        val old =
            BackupCodec.json.decodeFromString<AppSettings>("""{"theme":"dark","rowHeight":64}""")
        assertEquals(0, old.startupPage)
        assertTrue(old.showTeacher && old.showLocation)
        val pref =
            old.copy(
                startupPage = 1,
                courseFontScale = 1.25f,
                courseOpacity = .5f,
                courseCornerRadius = 16,
                showGridLines = false,
            )
        val b = Backup(settings = pref)
        val decoded = BackupCodec.decode(BackupCodec.encode(b))
        assertEquals(pref, decoded.settings)
    }

    @Test
    fun invalidAppearanceParametersAreRejected() {
        for (p in
            listOf(
                AppSettings(startupPage = 3),
                AppSettings(courseFontScale = 0f),
                AppSettings(courseOpacity = Float.NaN),
                AppSettings(courseCornerRadius = 25),
            )) assertTrue(runCatching { BackupCodec.validate(Backup(settings = p)) }.isFailure)
    }
}
