package com.example.oneclickorder.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.oneclickorder.data.room.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    // Insert a new order, returns the row ID of the inserted item
    @Insert(onConflict = OnConflictStrategy.IGNORE)  // Change to IGNORE or ABORT
    suspend fun insertOrder(order: OrderEntity): Long

    // Get all orders
    @Query("SELECT * FROM orders")
    fun getOrders(): Flow<List<OrderEntity>>

    // Get all orders that haven't been sent (isSent = false)
    @Query("SELECT * FROM orders WHERE isSent = 0")
    fun getUnsentOrders(): Flow<List<OrderEntity>>

    // Delete a specific order by its order ID
    @Query("DELETE FROM orders WHERE orderId = :orderId")
    suspend fun deleteOrderById(orderId: Int)
    // Update the order's sent status (only the isSent field)
    @Query("UPDATE orders SET isSent = :isSent WHERE orderId = :orderId")
    suspend fun updateOrderStatus(orderId: Int, isSent: Boolean)
}