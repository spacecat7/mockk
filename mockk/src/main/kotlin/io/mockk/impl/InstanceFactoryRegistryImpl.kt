package io.mockk.impl

import io.mockk.InternalPlatform
import io.mockk.MockKGateway.InstanceFactory
import io.mockk.MockKGateway.InstanceFactoryRegistry

internal class InstanceFactoryRegistryImpl : InstanceFactoryRegistry {
    private val factories = InternalPlatform.synchronizedMutableList<InstanceFactory>()

    val instanceFactories: List<InstanceFactory>
        get() = synchronized(factories) {
            factories.toList()
        }

    override fun registerFactory(factory: InstanceFactory) {
        factories.add(factory)
    }

    override fun deregisterFactory(factory: InstanceFactory) {
        factories.remove(factory)
    }
}
