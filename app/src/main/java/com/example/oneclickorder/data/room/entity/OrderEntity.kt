package com.example.oneclickorder.data.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val orderId: Int = 0,
    val orderString: String,
    val createdAt: String,
    val isSent: Boolean = false  // Default to false, meaning order is unsent
)