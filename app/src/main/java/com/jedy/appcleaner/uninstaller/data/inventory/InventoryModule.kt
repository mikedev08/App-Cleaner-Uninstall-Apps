package com.jedy.appcleaner.uninstaller.data.inventory

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InventoryModule {
    @Binds @Singleton
    abstract fun bindAppInventory(impl: PackageManagerAppInventory): AppInventory
}
