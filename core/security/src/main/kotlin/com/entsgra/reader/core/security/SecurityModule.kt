package com.entsgra.reader.core.security

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SecurityModule {
	@Binds
	abstract fun bindCryptoPrimitives(impl: KeystoreCryptoPrimitives): CryptoPrimitives
}
