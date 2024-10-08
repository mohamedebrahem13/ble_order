package com.example.oneclickorder.domain

import com.example.oneclickorder.data.repository.source.ILocalOrderRepository
import com.example.oneclickorder.data.room.entity.OrderEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalOrderUseCase @Inject constructor(
    private val localOrderRepository: ILocalOrderRepository // Interact with the repository interface
) {

    // Save a new order and return the order ID
    suspend fun saveOrder(orderString: String, createdBy: String): Long {
        return localOrderRepository.saveOrder(orderString, createdBy)
    }

    // Get all unsent orders (isSent = false)
    fun getUnsentOrders(): Flow<List<OrderEntity>> {
        return localOrderRepository.getUnsentOrders()
    }

    // Delete a specific order by orderId
    suspend fun deleteOrder(orderId: Int) {
        localOrderRepository.deleteOrder(orderId)
    }

    // Update order's sent status by orderId
    suspend fun updateOrderStatus(orderId: Int, isSent: Boolean) {
        localOrderRepository.updateOrderStatus(orderId, isSent)
    }
}