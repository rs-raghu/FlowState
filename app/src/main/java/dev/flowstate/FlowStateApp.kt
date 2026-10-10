package dev.flowstate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.flowstate.data.Coordinator
import dev.flowstate.data.FlowDatabase
import javax.inject.Inject

@HiltAndroidApp
class FlowStateApp : Application() {
    @Inject lateinit var database: FlowDatabase
    @Inject lateinit var coordinator: Coordinator
}
