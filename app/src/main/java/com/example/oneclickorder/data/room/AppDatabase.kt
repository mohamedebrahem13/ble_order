package com.example.oneclickorder.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.oneclickorder.data.room.entity.OrderEntity

@Database(
    entities = [OrderEntity::class], // Include all your entities here
    version = 1, // Increment version number whenever the schema changes
    exportSchema = false // Set to true if you want Room to export the schema
)
abstract class AppDatabase : RoomDatabase() {

    // Provide access to OrderDao
    abstract fun orderDao(): OrderDao
}