package com.example.oneclickorder.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.room.Room
import com.example.oneclickorder.data.BLEManager
import com.example.oneclickorder.data.repository.BLERepositoryImpl
import com.example.oneclickorder.data.repository.LocalOrderRepositoryImpl
import com.example.oneclickorder.data.repository.source.IBLERepository
import com.example.oneclickorder.data.repository.source.ILocalOrderRepository
import com.example.oneclickorder.data.room.AppDatabase
import com.example.oneclickorder.data.room.OrderDao
import com.example.oneclickorder.domain.BLEUseCase
import com.example.oneclickorder.domain.LocalOrderUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideBluetoothManager(
        @ApplicationContext context: Context
    ): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    @Provides
    fun provideBluetoothAdapter(
        bluetoothManager: BluetoothManager
    ): BluetoothAdapter {
        return bluetoothManager.adapter
    }
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        // Providing CoroutineScope with SupervisorJob for managing BLE connections
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideBLEManager(
        scope: CoroutineScope
    ): BLEManager {
        return BLEManager(scope)
    }
    // Provide the BLERepository implementation
    @Provides
    @Singleton
    fun provideBLERepository(
        bleManager: BLEManager
    ): IBLERepository {
        return BLERepositoryImpl(bleManager)
    }
    // Provide the BLEUseCase
    @Provides
    @Singleton
    fun provideBLEUseCase(
        repository: IBLERepository
    ): BLEUseCase {
        return BLEUseCase(repository)
    }
    // **Room Database related providers**

    // Provide the Room Database
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "order_database"
        ).build()
    }

    // Provide OrderDao
    @Provides
    @Singleton
    fun provideOrderDao(appDatabase: AppDatabase): OrderDao {
        return appDatabase.orderDao()
    }
    @Provides
    @Singleton
    fun provideLocalOrderUseCase(
        localOrderRepository: ILocalOrderRepository
    ): LocalOrderUseCase {
        return LocalOrderUseCase(localOrderRepository)
    }
    @Provides
    @Singleton
    fun provideLocalOrderRepository(
        orderDao: OrderDao
    ): ILocalOrderRepository {
        return LocalOrderRepositoryImpl(orderDao)
    }
}