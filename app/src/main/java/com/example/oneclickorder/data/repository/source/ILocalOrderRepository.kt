package com.example.oneclickorder.data.repository.source

import com.example.oneclickorder.data.room.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

interface ILocalOrderRepository {

    // Save a new order
    suspend fun saveOrder(orderString: String, createdBy: String): Long

    // Get all orders that are unsent (isSent = false)
    fun getUnsentOrders(): Flow<List<OrderEntity>>

    // Delete a specific order by its orderId
    suspend fun deleteOrder(orderId: Int)

    // Update order sent status by orderId
    suspend fun updateOrderStatus(orderId: Int, isSent: Boolean)
}