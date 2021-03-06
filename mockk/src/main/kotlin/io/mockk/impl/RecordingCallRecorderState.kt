package io.mockk.impl

import io.mockk.InternalPlatform
import io.mockk.Invocation
import io.mockk.Matcher
import io.mockk.MockKException
import kotlin.reflect.KClass

internal abstract class RecordingCallRecorderState(recorder: CallRecorderImpl) : CallRecorderState(recorder) {
    private var callRoundBuilder: CallRoundBuilder? = null
    private val callRounds = mutableListOf<CallRound>()
    val childMocks = ChildMocks()

    override fun catchArgs(round: Int, n: Int) {
        val builder = callRoundBuilder
        if (builder != null) {
            callRounds.add(builder.build())
        }

        callRoundBuilder = recorder.factories.callRoundBuilder()
        recorder.childHinter = recorder.factories.childHinter()

        if (round == n) {
            signMatchers()
            mockRealChilds()
        }
    }

    private fun signMatchers() {
        recorder.calls.clear()
        val detector = recorder.factories.signatureMatcherDetector(callRounds, childMocks.mocks)
        recorder.calls.addAll(detector.detect())
    }

    override fun <T : Any> matcher(matcher: Matcher<*>, cls: KClass<T>): T {
        val signatureValue = recorder.signatureValueGenerator.signatureValue(cls) {
            recorder.instantiator.instantiate(cls)
        }

        builder().addMatcher(matcher, InternalPlatform.packRef(signatureValue)!!)

        return signatureValue
    }

    override fun call(invocation: Invocation): Any? {
        childMocks.requireNoArgIsChildMock(invocation.args)

        val retType = recorder.childHinter.nextChildType { invocation.method.returnType }

        builder().addSignedCall(retType, invocation)

        return recorder.anyValueGenerator.anyValue(retType) {
            childMocks.childMock(retType) {
                recorder.mockFactory.childMock(retType)
            }
        }
    }

    fun mockRealChilds() {
        val mocker = RealChildMocker(recorder.stubRepo, recorder.calls)
        mocker.mock()

        recorder.calls.clear()
        recorder.calls.addAll(mocker.resultCalls)

        log.trace { "Mocked childs" }
    }

    override fun nCalls(): Int = callRoundBuilder?.signedCalls?.size ?: 0

    /**
     * Main idea is to have enough random information
     * to create signature for the argument.
     *
     * Max 40 calls looks like reasonable compromise
     */
    override fun estimateCallRounds(): Int {
        return builder().signedCalls
                .flatMap { it.invocation.args }
                .filterNotNull()
                .map(this::typeEstimation)
                .max() ?: 1
    }

    private fun typeEstimation(it: Any): Int {
        return when (it::class) {
            Boolean::class -> 40
            Byte::class -> 8
            Char::class -> 4
            Short::class -> 4
            Int::class -> 2
            Float::class -> 2
            else -> 1
        }
    }

    private fun builder(): CallRoundBuilder = callRoundBuilder
            ?: throw MockKException("Call builder is not initialized. Bad state")

    companion object {
        val log = Logger<RecordingCallRecorderState>()
    }
}