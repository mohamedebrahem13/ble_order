package com.example.oneclickorder.data.repository

import com.example.oneclickorder.data.repository.source.ILocalOrderRepository
import com.example.oneclickorder.data.room.OrderDao
import com.example.oneclickorder.data.room.entity.OrderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class LocalOrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao // Injecting OrderDao via constructor
) : ILocalOrderRepository {

    // Save a new order and return the order ID
    override suspend fun saveOrder(orderString: String, createdBy: String): Long {
        val order = OrderEntity(
            orderString = orderString,
            createdAt = createdBy
        )
        return orderDao.insertOrder(order)
    }

    // Get all unsent orders (isSent = false)
    override fun getUnsentOrders(): Flow<List<OrderEntity>> {
        return orderDao.getUnsentOrders()
    }

    // Delete a specific order by orderId
    override suspend fun deleteOrder(orderId: Int) {
        orderDao.deleteOrderById(orderId)
    }

    // Update order's sent status by orderId
    override suspend fun updateOrderStatus(orderId: Int, isSent: Boolean) {
        orderDao.updateOrderStatus(orderId, isSent)
    }
}