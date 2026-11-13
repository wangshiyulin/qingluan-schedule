package com.qingluan.schedule

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingluan.schedule.ui.QingluanApp

class MainActivity : ComponentActivity() {
    private lateinit var model: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model =
            ViewModelProvider(this, viewModelFactory { initializer { AppViewModel(application) } })[
                AppViewModel::class.java]
        setContent { QingluanApp(intent.getIntExtra("tab", 0), model) }
        if (savedInstanceState == null) readIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIncoming(intent)
    }

    private fun readIncoming(intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND)) return
        val uri =
            if (intent.action == Intent.ACTION_VIEW) intent.data
            else intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (uri?.scheme == "content") model.previewImport(uri)
        else
            intent
                .getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let(model::previewText)
    }
}
