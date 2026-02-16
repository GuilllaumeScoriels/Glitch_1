package di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import Calendars.DefaultCurrentUserProvider
import Calendars.CurrentUserProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCurrentUserProvider(
        impl: DefaultCurrentUserProvider
    ): CurrentUserProvider
}
