package dev.flowstate

import android.app.Application
import androidx.room.Room
import dev.flowstate.data.FlowDatabase
import dev.flowstate.data.Coordinator

class FlowStateApp : Application() {
    val database by lazy { Room.databaseBuilder(this, FlowDatabase::class.java, "flowstate.db").build() }
    val coordinator by lazy { Coordinator(database,this) }
}
