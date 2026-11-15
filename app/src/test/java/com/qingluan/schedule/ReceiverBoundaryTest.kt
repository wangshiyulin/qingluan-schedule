package com.qingluan.schedule

import android.content.*
import com.qingluan.schedule.system.RefreshReceiver
import com.qingluan.schedule.system.SystemCoordinator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceiverBoundaryTest {
    @Test
    fun unrelatedOrEmptyBroadcastDoesNotTouchApplicationState() {
        val context =
            object : ContextWrapper(null) {
                override fun getApplicationContext(): Context =
                    error("Unrelated broadcast touched application")
            }
        RefreshReceiver().onReceive(context, Intent())
        RefreshReceiver().onReceive(context, Intent("unrelated.action"))
    }

    @Test
    fun scheduledRefreshReachesApplicationInsteadOfBeingDiscarded() {
        val context =
            object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = error("accepted refresh")
            }
        val failure = runCatching {
            RefreshReceiver().onReceive(context, Intent(SystemCoordinator.REFRESH_ACTION))
        }.exceptionOrNull()
        assertEquals("accepted refresh", failure?.message)
    }
}
