package com.qingluan.schedule

import android.app.Application
import com.qingluan.schedule.data.Repository
import com.qingluan.schedule.system.SystemCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(kotlinx.coroutines.FlowPreview::class)
class ScheduleApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy { Repository(this, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            combine(repository.state.filterNotNull(), repository.settings) { s, p -> s to p }
                .debounce(350)
                .collect { (s, p) ->
                    runCatching { SystemCoordinator.refresh(this@ScheduleApplication, s, p) }
                }
        }
    }
}
