package com.example.oneclickorder.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.oneclickorder.data.BLEManager
import com.example.oneclickorder.data.repository.BLERepositoryImpl
import com.example.oneclickorder.data.repository.source.IBLERepository
import com.example.oneclickorder.domain.BLEUseCase
import com.example.oneclickorder.domain.state.BLEStateMonad
import com.example.oneclickorder.ui.BLEState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
    @Provides
    @Singleton
    fun provideMutableStateFlow(): MutableStateFlow<BLEState> {
        return MutableStateFlow(BLEState())  // Initialize with a default BLEState
    }
    @Provides
    @Singleton
    fun provideBLEStateMonad(stateFlow: MutableStateFlow<BLEState>): BLEStateMonad {
        return BLEStateMonad(stateFlow)  // Inject the state flow into the monad
    }}