package dev.flowstate

import android.app.Application
import androidx.room.Room
import dev.flowstate.data.FlowDatabase

class FlowStateApp : Application() {
    val database by lazy { Room.databaseBuilder(this, FlowDatabase::class.java, "flowstate.db").build() }
}
