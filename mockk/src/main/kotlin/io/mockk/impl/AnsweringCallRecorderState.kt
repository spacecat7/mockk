package io.mockk.impl

import io.mockk.Invocation
import io.mockk.MockKGateway.VerificationParameters

internal class AnsweringCallRecorderState(recorder: CallRecorderImpl) : CallRecorderState(recorder) {
    override fun call(invocation: Invocation): Any? {
        val stub = recorder.stubRepo.stubFor(invocation.self)
        stub.recordCall(invocation.copy(originalCall = { null }))
        val answer = stub.answer(invocation)
        log.debug { "Recorded call: $invocation, answer: ${answerToString(answer)}" }
        return answer
    }

    override fun startStubbing() = recorder.factories.stubbingCallRecorderState(recorder)
    override fun startVerification(params: VerificationParameters) = recorder.factories.verifyingCallRecorderState(recorder, params)

    private fun answerToString(answer: Any?) = recorder.stubRepo[answer]?.toStr() ?: answer.toString()

    companion object {
        val log = Logger<AnsweringCallRecorderState>()
    }
}