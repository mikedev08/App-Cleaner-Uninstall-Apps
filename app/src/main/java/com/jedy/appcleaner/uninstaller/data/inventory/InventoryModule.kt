package com.jedy.appcleaner.uninstaller.data.inventory

import com.jedy.appcleaner.uninstaller.core.startup.AppStartup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InventoryModule {
    @Binds @Singleton
    abstract fun bindAppInventory(impl: PackageManagerAppInventory): AppInventory

    /** Same singleton as [AppInventory]; see [InventoryHealth] for why it is a separate type. */
    @Binds @Singleton
    abstract fun bindInventoryHealth(impl: PackageManagerAppInventory): InventoryHealth

    @Binds @IntoSet
    abstract fun bindInventoryStartup(impl: InventoryStartup): AppStartup
}
