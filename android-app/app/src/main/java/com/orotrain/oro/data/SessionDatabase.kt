package com.orotrain.oro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.orotrain.oro.data.entities.SessionEntity
import com.orotrain.oro.data.entities.StrokeEntity

@Database(
    entities = [SessionEntity::class, StrokeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: SessionDatabase? = null

        fun getInstance(context: Context): SessionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SessionDatabase::class.java,
                    "oro_sessions.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
