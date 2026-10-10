package dev.flowstate.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FlowDatabase =
        Room.databaseBuilder(context, FlowDatabase::class.java, "flowstate.db")
            .addMigrations(
                FlowDatabase.MIGRATION_1_2,
                FlowDatabase.MIGRATION_2_3,
                FlowDatabase.MIGRATION_3_4,
            )
            .build()

    @Provides
    @Singleton
    fun coordinator(database: FlowDatabase, @ApplicationContext context: Context) =
        Coordinator(database, context)
}
