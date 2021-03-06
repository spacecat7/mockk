package io.mockk.impl

import io.mockk.Invocation
import kotlin.reflect.KClass

internal class SpyKStub<T : Any>(cls: KClass<T>, name: String) : MockKStub(cls, name) {
    override fun defaultAnswer(invocation: Invocation): Any? {
        return invocation.originalCall()
    }

    override fun toStr(): String = "spyk<" + type.simpleName + ">($name)#$hashCodeStr"
}