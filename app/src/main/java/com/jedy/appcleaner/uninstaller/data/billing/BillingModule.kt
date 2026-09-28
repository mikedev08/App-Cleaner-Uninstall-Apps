package com.jedy.appcleaner.uninstaller.data.billing

import com.jedy.appcleaner.uninstaller.core.startup.AppStartup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds @Singleton
    abstract fun bindPremium(impl: RevenueCatPremium): Premium

    @Binds @Singleton
    abstract fun bindBillingRepository(impl: RevenueCatBilling): BillingRepository

    @Binds @IntoSet
    abstract fun bindBillingStartup(impl: BillingStartup): AppStartup
}

/**
 * Configures RevenueCat at process start (PRD §2) — only when a key was built in, so a keyless
 * debug build starts silently. Also builds the billing singleton early, which seeds the
 * persisted entitlement before any screen or worker asks for it.
 */
class BillingStartup @Inject constructor(
    private val billing: RevenueCatBilling,
) : AppStartup {
    override fun start() = billing.start()
}
