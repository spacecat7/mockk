package io.mockk

import kotlin.reflect.KClass
import io.mockk.InternalPlatform.toStr
import io.mockk.MockKGateway.*

/**
 * Exception thrown by library
 */
class MockKException(message: String, ex: Throwable? = null) : Throwable(message, ex)

/**
 * DSL entry points.
 */
@Suppress("NOTHING_TO_INLINE")
object MockKDsl {
    /**
     * Builds a new mock for specified class
     */
    inline fun <reified T : Any> internalMockk(name: String? = null, vararg moreInterfaces: KClass<*>): T = MockKGateway.implementation().mockFactory.mockk(T::class, name, moreInterfaces)

    /**
     * Builds a new spy for specified class. Initializes object via default constructor.
     */
    inline fun <T : Any> internalSpyk(objToCopy: T, name: String? = null, vararg moreInterfaces: KClass<*>): T = MockKGateway.implementation().mockFactory.spyk(null, objToCopy, name, moreInterfaces)

    /**
     * Builds a new spy for specified class. Copies fields from provided object
     */
    inline fun <reified T : Any> internalSpyk(name: String? = null, vararg moreInterfaces: KClass<*>): T = MockKGateway.implementation().mockFactory.spyk(T::class, null, name, moreInterfaces)

    /**
     * Creates new capturing slot
     */
    inline fun <reified T : Any> internalSlot() = CapturingSlot<T>()

    /**
     * Starts a block of stubbing. Part of DSL.
     */
    inline fun <T> internalEvery(noinline stubBlock: MockKMatcherScope.() -> T): MockKStubScope<T> = MockKGateway.implementation().stubbingRecorder.every(stubBlock, null)

    /**
     * Starts a block of stubbing for coroutines. Part of DSL.
     */
    inline fun <T> internalCoEvery(noinline stubBlock: suspend MockKMatcherScope.() -> T): MockKStubScope<T> = MockKGateway.implementation().stubbingRecorder.every(null, stubBlock)

    /**
     * Verifies calls happened in the past. Part of DSL
     */
    inline fun internalVerify(ordering: Ordering = Ordering.UNORDERED,
                              inverse: Boolean = false,
                              atLeast: Int = 1,
                              atMost: Int = Int.MAX_VALUE,
                              exactly: Int = -1,
                              noinline verifyBlock: MockKVerificationScope.() -> Unit) {

        internalCheckExactlyAtMostAtLeast(exactly, atLeast, atMost, ordering)

        val min = if (exactly != -1) exactly else atLeast
        val max = if (exactly != -1) exactly else atMost

        MockKGateway.implementation().verifyingRecorder.verify(
                VerificationParameters(ordering, min, max, inverse),
                verifyBlock,
                null)
    }

    /**
     * Verify for coroutines
     */
    inline fun internalCoVerify(ordering: Ordering = Ordering.UNORDERED,
                                inverse: Boolean = false,
                                atLeast: Int = 1,
                                atMost: Int = Int.MAX_VALUE,
                                exactly: Int = -1,
                                noinline verifyBlock: suspend MockKVerificationScope.() -> Unit) {

        internalCheckExactlyAtMostAtLeast(exactly, atLeast, atMost, ordering)

        val min = if (exactly != -1) exactly else atLeast
        val max = if (exactly != -1) exactly else atMost

        MockKGateway.implementation().verifyingRecorder.verify(
                VerificationParameters(ordering, min, max, inverse),
                null,
                verifyBlock)
    }

    @PublishedApi
    internal fun internalCheckExactlyAtMostAtLeast(exactly: Int, atLeast: Int, atMost: Int, ordering: Ordering) {
        if (exactly != -1 && (atLeast != 1 || atMost != Int.MAX_VALUE)) {
            throw MockKException("specify either atLeast/atMost or exactly")
        }
        if (exactly < -1) {
            throw MockKException("exactly should be positive")
        }
        if (atLeast < 0) {
            throw MockKException("atLeast should be positive")
        }
        if (atMost < 0) {
            throw MockKException("atMost should be positive")
        }
        if (atLeast > atMost) {
            throw MockKException("atLeast should less or equal atMost")
        }

        if (ordering != Ordering.UNORDERED) {
            if (atLeast != 1 || atMost != Int.MAX_VALUE || exactly != -1) {
                throw MockKException("atLeast, atMost, exactly is only allowed in unordered verify block")
            }
        }
    }

    /**
     * Shortcut for ordered calls verification
     */
    inline fun internalVerifyOrder(inverse: Boolean = false,
                                   noinline verifyBlock: MockKVerificationScope.() -> Unit) {
        internalVerify(Ordering.ORDERED, inverse, verifyBlock = verifyBlock)
    }

    /**
     * Shortcut for all calls verification
     */
    inline fun internalVerifyAll(inverse: Boolean = false,
                                 noinline verifyBlock: MockKVerificationScope.() -> Unit) {
        internalVerify(Ordering.ALL, inverse, verifyBlock = verifyBlock)
    }

    /**
     * Shortcut for sequence calls verification
     */
    inline fun internalVerifySequence(inverse: Boolean = false,
                                      noinline verifyBlock: MockKVerificationScope.() -> Unit) {
        internalVerify(Ordering.SEQUENCE, inverse, verifyBlock = verifyBlock)
    }

    /**
     * Resets information associated with mock
     */
    inline fun internalClearMocks(vararg mocks: Any, answers: Boolean = true, recordedCalls: Boolean = true, childMocks: Boolean = true) {
        MockKGateway.implementation().mockFactory.clear(
                mocks = mocks,
                answers = answers,
                recordedCalls = recordedCalls,
                childMocks = childMocks)
    }

    /**
     * Registers instance factory and returns object able to do deregistration.
     */
    inline fun <reified T : Any> internalRegisterInstanceFactory(noinline instanceFactory: () -> T): Deregisterable {
        val factoryObj = object : MockKGateway.InstanceFactory {
            override fun instantiate(cls: KClass<*>): Any? {
                if (T::class == cls) {
                    return instanceFactory()
                }
                return null
            }
        }

        MockKGateway.implementation().instanceFactoryRegistry.registerFactory(factoryObj)
        return object : Deregisterable {
            override fun deregister() {
                MockKGateway.implementation().instanceFactoryRegistry.deregisterFactory(factoryObj)
            }
        }
    }

    /**
     * Executes block of code with registering and unregistering instance factory.
     */
    inline fun <reified T : Any, R> internalWithInstanceFactory(noinline instanceFactory: () -> T, block: () -> R): R {
        return internalRegisterInstanceFactory(instanceFactory).use {
            block()
        }
    }

    /**
     * Declares static mockk.
     */
    inline fun <reified T : Any> internalStaticMockk(): MockKStaticScope = MockKStaticScope(T::class)

    /**
     * Declares static mockk.
     */
    inline fun internalStaticMockk(vararg kClass: KClass<out Any>): MockKStaticScope = MockKStaticScope(*kClass)
}

/**
 * Verification orderding
 */
enum class Ordering {
    /**
     * Order is not important. Some calls just should happen
     */
    UNORDERED,
    /**
     * Order is not important. All calls should happen
     */
    ALL,
    /**
     * Order is important, but not all calls are checked
     */
    ORDERED,
    /**
     * Order is important and all calls should be specified
     */
    SEQUENCE
}


/**
 * Basic stub/verification scope. Part of DSL.
 *
 * Inside of the scope you can interact with mocks.
 * You can chain calls to the mock, put argument matchers instead of arguments,
 * capture arguments, combine matchers in and/or/not expressions.
 *
 * It's not required to specify all arguments as matchers,
 * if the argument value is constant it's automatically replaced with eq() matcher.
 * .
 * Handling arguments that have defaults fetched from function (alike System.currentTimeMillis())
 * can be an issue, because it's not a constant. Such arguments can always be replaced
 * with some matcher.
 *
 * Provided information is gathered and associated with mock
 */
open class MockKMatcherScope(val callRecorder: CallRecorder,
                             val lambda: CapturingSlot<Function<*>>) {

    inline fun <reified T : Any> match(matcher: Matcher<T>): T {
        return callRecorder.matcher(matcher, T::class)
    }

    inline fun <reified T : Any> match(noinline matcher: (T) -> Boolean): T = matchNullable {
        if (it == null) {
            false
        } else {
            matcher(it)
        }
    }

    inline fun <reified T : Any> matchNullable(noinline matcher: (T?) -> Boolean): T = match(FunctionMatcher(matcher, T::class))
    inline fun <reified T : Any> eq(value: T, inverse: Boolean = false): T = match(EqMatcher(value, inverse = inverse))
    inline fun <reified T : Any> refEq(value: T, inverse: Boolean = false): T = match(EqMatcher(value, ref = true, inverse = inverse))
    inline fun <reified T : Any> any(): T = match(ConstantMatcher(true))
    inline fun <reified T : Any> capture(lst: MutableList<T>): T = match(CaptureMatcher(lst, T::class))
    inline fun <reified T : Any> capture(lst: CapturingSlot<T>): T = match(CapturingSlotMatcher(lst, T::class))
    inline fun <reified T : Any> captureNullable(lst: MutableList<T?>): T = match(CaptureNullableMatcher(lst, T::class))
    inline fun <reified T : Comparable<T>> cmpEq(value: T): T = match(ComparingMatcher(value, 0, T::class))
    inline fun <reified T : Comparable<T>> more(value: T, andEquals: Boolean = false): T = match(ComparingMatcher(value, if (andEquals) 2 else 1, T::class))
    inline fun <reified T : Comparable<T>> less(value: T, andEquals: Boolean = false): T = match(ComparingMatcher(value, if (andEquals) -2 else -1, T::class))
    inline fun <reified T : Any> and(left: T, right: T) = match(AndOrMatcher(true, left, right))
    inline fun <reified T : Any> or(left: T, right: T) = match(AndOrMatcher(false, left, right))
    inline fun <reified T : Any> not(value: T) = match(NotMatcher(value))
    inline fun <reified T : Any> isNull(inverse: Boolean = false) = match(NullCheckMatcher<T>(inverse))
    inline fun <reified T : Any, R : T> ofType(cls: KClass<R>) = match(OfTypeMatcher<T>(cls))

    inline fun <reified T : () -> R, R> invoke() = match(InvokeMatcher<T> { it() })
    inline fun <reified T : (A1) -> R, R, A1> invoke(arg1: A1) = match(InvokeMatcher<T> { it(arg1) })
    inline fun <reified T : (A1, A2) -> R, R, A1, A2> invoke(arg1: A1, arg2: A2) = match(InvokeMatcher<T> { it(arg1, arg2) })
    inline fun <reified T : (A1, A2, A3) -> R, R, A1, A2, A3> invoke(arg1: A1, arg2: A2, arg3: A3) = match(InvokeMatcher<T> { it(arg1, arg2, arg3) })
    inline fun <reified T : (A1, A2, A3, A4) -> R, R, A1, A2, A3, A4> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4) })
    inline fun <reified T : (A1, A2, A3, A4, A5) -> R, R, A1, A2, A3, A4, A5> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6) -> R, R, A1, A2, A3, A4, A5, A6> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7) -> R, R, A1, A2, A3, A4, A5, A6, A7> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20, arg21: A21) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21) })
    inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20, arg21: A21, arg22: A22) = match(InvokeMatcher<T> { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22) })

    inline fun <reified T : suspend () -> R, R> coInvoke() = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it() } })
    inline fun <reified T : suspend (A1) -> R, R, A1> coInvoke(arg1: A1) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1) } })
    inline fun <reified T : suspend (A1, A2) -> R, R, A1, A2> coInvoke(arg1: A1, arg2: A2) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2) } })
    inline fun <reified T : suspend (A1, A2, A3) -> R, R, A1, A2, A3> coInvoke(arg1: A1, arg2: A2, arg3: A3) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3) } })
    inline fun <reified T : suspend (A1, A2, A3, A4) -> R, R, A1, A2, A3, A4> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5) -> R, R, A1, A2, A3, A4, A5> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6) -> R, R, A1, A2, A3, A4, A5, A6> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7) -> R, R, A1, A2, A3, A4, A5, A6, A7> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20, arg21: A21) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21) } })
    inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22> coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20, arg21: A21, arg22: A22) = match(InvokeMatcher<T> { InternalPlatform.runCoroutine { it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22) } })

    inline fun <reified T : Any> allAny(): T = match(AllAnyMatcher())

    @Suppress("NOTHING_TO_INLINE")
    inline fun <R, T : Any> R.hint(cls: KClass<T>, n: Int = 1): R {
        MockKGateway.implementation().callRecorder.hintNextReturnType(cls, n)
        return this
    }

    /**
     * Captures lambda function. Captured lambda<(A1, A2, ...) -> R>().invoke(...) can be used in answer scope.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Function<*>> captureLambda(): T {
        val matcher = CapturingSlotMatcher(lambda as CapturingSlot<T>, T::class)
        return callRecorder.matcher(matcher, T::class)
    }

    /**
     * Captures coroutine. Captured coroutine<suspend (A1, A2, ...) -> R>().coInvoke(...) can be used in answer scope.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> captureCoroutine(): T {
        val matcher = CapturingSlotMatcher(lambda as CapturingSlot<T>, T::class)
        return callRecorder.matcher(matcher, T::class)
    }

    inline fun <reified T : Any> coMatch(noinline matcher: suspend (T) -> Boolean): T = match {
        InternalPlatform.runCoroutine {
            matcher(it)
        }
    }

    inline fun <reified T : Any> coMatchNullable(noinline matcher: suspend (T?) -> Boolean): T = matchNullable {
        InternalPlatform.runCoroutine {
            matcher(it)
        }
    }
}

/**
 * Part of DSL. Additional operations for verification scope.
 */
class MockKVerificationScope(callRecorder: CallRecorder,
                             lambda: CapturingSlot<Function<*>>) : MockKMatcherScope(callRecorder, lambda) {
    inline fun <reified T : Any> assert(msg: String? = null, noinline assertion: (T) -> Boolean): T = match(AssertMatcher({ assertion(it as T) }, msg, T::class))
    inline fun <reified T : Any> assertNullable(msg: String? = null, noinline assertion: (T?) -> Boolean): T = match(AssertMatcher(assertion, msg, T::class, nullable = true))
    inline fun <reified T : Any> run(noinline captureBlock: MockKAssertScope.(T) -> Unit): T = match {
        MockKAssertScope(it).captureBlock(it)
        true
    }

    inline fun <reified T : Any> runNullable(noinline captureBlock: MockKAssertScope.(T?) -> Unit): T = matchNullable {
        MockKAssertScope(it).captureBlock(it)
        true
    }

    inline fun <reified T : Any> coAssert(msg: String? = null, noinline assertion: suspend (T) -> Boolean): T = assert(msg) {
        InternalPlatform.runCoroutine {
            assertion(it)
        }
    }

    inline fun <reified T : Any> coAssertNullable(msg: String? = null, noinline assertion: suspend (T?) -> Boolean): T = assertNullable(msg) {
        InternalPlatform.runCoroutine {
            assertion(it)
        }
    }

    inline fun <reified T : Any> coRun(noinline captureBlock: suspend MockKAssertScope.(T) -> Unit): T = run {
        InternalPlatform.runCoroutine {
            captureBlock(it)
        }
    }

    inline fun <reified T : Any> coRunNullable(noinline captureBlock: suspend MockKAssertScope.(T?) -> Unit): T = runNullable {
        InternalPlatform.runCoroutine {
            captureBlock(it)
        }
    }

    infix fun Any.wasNot(called: Called) {
        listOf(this) wasNot called
    }

    @Suppress("UNUSED_PARAMETER")
    infix fun List<Any>.wasNot(called: Called) {
        callRecorder.wasNotCalled(this)
    }
}

/**
 * Part of DSL. Object to represent phrase "wasNot Called"
 */
object Called

class MockKAssertScope(val actual: Any?) {
    fun assertEquals(expected: Any?) {
        if (!InternalPlatform.deepEquals(expected, actual)) {
            throw AssertionError(format(actual, expected))
        }
    }

    fun assertEquals(msg: String, expected: Any?) {
        if (!InternalPlatform.deepEquals(expected, actual)) {
            throw AssertionError(format(actual, expected, msg))
        }
    }

    private fun format(actual: Any?, expected: Any?, message: String? = null): String {
        val msgFormatted = if (message != null) "$message " else ""

        return "${msgFormatted}expected [$expected] but found [$actual]"
    }

}

/**
 * Part of DSL. Object to represent phrase "just Runs"
 */
object Runs

/**
 * Stub scope. Part of DSL
 *
 * Allows to specify function result
 */
class MockKStubScope<T>(val callRecorder: CallRecorder,
                        private val lambda: CapturingSlot<Function<*>>) {
    infix fun answers(answer: Answer<T>) = callRecorder.answer(answer)

    infix fun returns(returnValue: T) = answers(ConstantAnswer(returnValue))

    infix fun returnsMany(values: List<T>) = answers(ManyAnswersAnswer(values))

    fun returnsMany(vararg values: T) = returnsMany(values.toList())

    infix fun throws(ex: Throwable) = answers(ThrowingAnswer(ex))

    infix fun answers(answer: MockKAnswerScope<T>.(MatchedCall) -> T) =
            answers(FunctionAnswer({ MockKAnswerScope<T>(lambda, it).answer(it) }))


    infix fun coAnswers(answer: suspend MockKAnswerScope<T>.(MatchedCall) -> T) = answers {
        InternalPlatform.runCoroutine {
            answer(it)
        }
    }

    @Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
    infix fun just(runs: Runs) = answers(ConstantAnswer<T?>(null) as ConstantAnswer<T>)
}

/**
 * Scope for answering functions. Part of DSL
 */
class MockKAnswerScope<T>(@PublishedApi
                          internal val lambda: CapturingSlot<Function<*>>,
                          val call: MatchedCall) {

    val invocation = call.invocation
    val matcher = call.matcher

    val self
        get() = invocation.self

    val method
        get() = invocation.method

    val args
        get() = invocation.args

    val nArgs
        get() = invocation.args.size

    inline fun <reified T> firstArg() = invocation.args[0] as T
    inline fun <reified T> secondArg() = invocation.args[1] as T
    inline fun <reified T> thirdArg() = invocation.args[2] as T
    inline fun <reified T> lastArg() = invocation.args.last() as T
    inline fun <reified T> arg(n: Int) = invocation.args[n] as T

    @Suppress("NOTHING_TO_INLINE")
    inline fun <T> MutableList<T>.captured() = last()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Function<*>> lambda() = lambda as CapturingSlot<T>

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> coroutine() = lambda as CapturingSlot<T>

    val nothing = null

    @Suppress("UNCHECKED_CAST")
    fun callOriginal(): T = call.invocation.originalCall.invoke() as T
}

/**
 * Scope for static mockks. Part of DSL
 */
class MockKStaticScope(vararg val staticTypes: KClass<*>) {
    fun mock() {
        for (type in staticTypes) {
            MockKGateway.implementation().mockFactory.staticMockk(type)
        }
    }

    fun unmock() {
        for (type in staticTypes) {
            MockKGateway.implementation().mockFactory.staticUnMockk(type)
        }
    }

    inline fun <reified T : Any> and() = MockKStaticScope(T::class, *staticTypes)

    inline fun <T> use(block: () -> T): T {
        mock()
        return try {
            block()
        } finally {
            unmock()
        }
    }
}

/**
 * Slot allows to capture one value.
 *
 * If this values is lambda then it's possible to invoke it.
 */
class CapturingSlot<T : Any>() {
    var isCaptured = false

    var isNull = false

    lateinit var captured: T

    fun clear() {
        isCaptured = false
        isNull = false
    }

    override fun toString(): String = "slot(${if (isCaptured) "captured=${if (isNull) "null" else captured.toStr()}" else ""})"
}

inline fun <reified T : () -> R, R> CapturingSlot<T>.invoke() = captured.invoke()
inline fun <reified T : (A1) -> R, R, A1> CapturingSlot<T>.invoke(arg1: A1) = captured.invoke(arg1)
inline fun <reified T : (A1, A2) -> R, R, A1, A2> CapturingSlot<T>.invoke(arg1: A1, arg2: A2) = captured.invoke(arg1, arg2)
inline fun <reified T : (A1, A2, A3) -> R, R, A1, A2, A3> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3) = captured.invoke(arg1, arg2, arg3)
inline fun <reified T : (A1, A2, A3, A4) -> R, R, A1, A2, A3, A4> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4) = captured.invoke(arg1, arg2, arg3, arg4)
inline fun <reified T : (A1, A2, A3, A4, A5) -> R, R, A1, A2, A3, A4, A5> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5) = captured.invoke(arg1, arg2, arg3, arg4, arg5)
inline fun <reified T : (A1, A2, A3, A4, A5, A6) -> R, R, A1, A2, A3, A4, A5, A6> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7) -> R, R, A1, A2, A3, A4, A5, A6, A7> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20, arg21: A21) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21)
inline fun <reified T : (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22> CapturingSlot<T>.invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20, arg21: A21, arg22: A22) = captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22)

inline fun <reified T : suspend () -> R, R> CapturingSlot<T>.coInvoke() = InternalPlatform.runCoroutine { captured.invoke() }
inline fun <reified T : suspend (A1) -> R, R, A1> CapturingSlot<T>.coInvoke(arg1: A1) = InternalPlatform.runCoroutine { captured.invoke(arg1) }
inline fun <reified T : suspend (A1, A2) -> R, R, A1, A2> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2) }
inline fun <reified T : suspend (A1, A2, A3) -> R, R, A1, A2, A3> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3) }
inline fun <reified T : suspend (A1, A2, A3, A4) -> R, R, A1, A2, A3, A4> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5) -> R, R, A1, A2, A3, A4, A5> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6) -> R, R, A1, A2, A3, A4, A5, A6> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7) -> R, R, A1, A2, A3, A4, A5, A6, A7> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20, arg21: A21) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21) }
inline fun <reified T : suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22) -> R, R, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22> CapturingSlot<T>.coInvoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10, arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19, arg20: A20, arg21: A21, arg22: A22) = InternalPlatform.runCoroutine { captured.invoke(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22) }


/**
 * Checks if argument is matching some criteria
 */
interface Matcher<in T> {
    fun match(arg: T?): Boolean
}

/**
 * Checks if argument is of specific type
 */
interface TypedMatcher {
    val argumentType: KClass<*>

    fun checkType(arg: Any?): Boolean = argumentType.isInstance(arg)
}

/**
 * Allows to substitute matcher to find correct chained call
 */
interface EquivalentMatcher {
    fun equivalent(): Matcher<Any>
}

/**
 * Captures the argument
 */
interface CapturingMatcher {
    fun capture(arg: Any?)
}

/**
 * Matcher composed from several other matchers.
 *
 * Allows to build matching expressions. Alike "and(eq(5), capture(lst))"
 */
interface CompositeMatcher<T> {
    val operandValues: List<T>

    var subMatchers: List<Matcher<T>>?

}

/**
 * Provides return value for mocked function
 */
interface Answer<out T> {
    fun answer(call: MatchedCall): T
}

/**
 * Provides information about method
 */
data class MethodDescription(val name: String,
                             val returnType: KClass<*>,
                             val declaringClass: KClass<*>,
                             val paramTypes: List<KClass<*>>) {
    override fun toString() = "$name(${argsToStr()})"

    fun argsToStr() = paramTypes.map(this::argToStr).joinToString(", ")

    fun argToStr(argType: KClass<*>) = argType.simpleName


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodDescription) return false

        return when {
            name !== other.name -> false
            returnType != other.returnType -> false
            declaringClass != other.declaringClass -> false
            paramTypes != other.paramTypes -> false
            else -> true
        }

    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + declaringClass.hashCode()
        result = 31 * result + paramTypes.hashCode()
        return result
    }

}

/**
 * Mock invocation
 */
data class Invocation(val self: Any,
                      private val selfStr: String,
                      val method: MethodDescription,
                      val args: List<Any?>,
                      val timestamp: Long,
                      val originalCall: () -> Any?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Invocation) return false

        return when {
            self !== other.self -> false
            method != other.method -> false
            args != other.args -> false
            else -> true
        }
    }

    override fun hashCode(): Int {
        var result = InternalPlatform.identityHashCode(self)
        result = 31 * result + method.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String = "Invocation(self=$selfStr, method=$method, " +
            "args=${args.map({ it.toStr() }).joinToString(", ")})"

}

/**
 * Checks if invocation is matching via number of matchers
 */
data class InvocationMatcher(val self: Any,
                             private val selfStr: String,
                             val method: MethodDescription,
                             val args: List<Matcher<Any>>) {
    fun match(invocation: Invocation): Boolean {
        if (self !== invocation.self) {
            return false
        }
        if (method != invocation.method) {
            return false
        }
        if (args.size != invocation.args.size) {
            return false
        }

        for (i in 0 until args.size) {
            val matcher = args[i]
            val arg = invocation.args[i]

            if (matcher is TypedMatcher) {
                if (!matcher.checkType(arg)) {
                    return false
                }
            }

            if (!matcher.match(arg)) {
                return false
            }
        }

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InvocationMatcher) return false

        return when {
            self !== other.self -> false
            method != other.method -> false
            args != other.args -> false
            else -> true
        }

    }

    override fun hashCode(): Int {
        var result = InternalPlatform.identityHashCode(self)
        result = 31 * result + method.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "InvocationMatcher(self=$selfStr, method=$method, args=$args)"
    }


}

/**
 * Matched invocation
 */
data class MatchedCall(val retType: KClass<*>,
                       val invocation: Invocation,
                       val matcher: InvocationMatcher,
                       val chained: Boolean)

/**
 * Allows to deregister something was registered before
 */
interface Deregisterable {
    fun deregister()
}


inline fun <T : Deregisterable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        try {
            this.deregister()
        } catch (closeException: Throwable) {
            // skip
        }
    }
}
