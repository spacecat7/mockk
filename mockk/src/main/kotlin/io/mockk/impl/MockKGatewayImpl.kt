package io.mockk.impl

import io.mockk.MockKGateway
import io.mockk.MockKGateway.*
import io.mockk.Ordering
import io.mockk.Ref
import io.mockk.impl.JvmLogging.adaptor
import io.mockk.jvm.JvmAnyValueGenerator
import io.mockk.jvm.JvmSignatureValueGenerator
import io.mockk.proxy.MockKInstrumentation
import io.mockk.proxy.MockKInstrumentationLoader
import io.mockk.proxy.MockKProxyMaker
import java.util.*

class MockKGatewayImpl : MockKGateway {
    internal val instanceFactoryRegistryIntrnl = InstanceFactoryRegistryImpl()
    override val instanceFactoryRegistry: InstanceFactoryRegistry = instanceFactoryRegistryIntrnl

    val stubRepo = StubRepository()
    internal val instantiator = Instantiator(MockKProxyMaker.INSTANCE, instanceFactoryRegistryIntrnl)
    internal val anyValueGenerator = JvmAnyValueGenerator()
    internal val signatureValueGenerator = JvmSignatureValueGenerator(Random())


    override val mockFactory: MockFactory = MockFactoryImpl(
            MockKProxyMaker.INSTANCE,
            instantiator,
            stubRepo)

    internal val unorderedVerifier = UnorderedCallVerifierImpl(stubRepo)
    internal val allVerifier = AllCallVerifierImpl(stubRepo)
    internal val orderedVerifier = OrderedCallVerifierImpl(stubRepo)
    internal val sequenceVerifier = SequenceCallVerifierImpl(stubRepo)

    override fun verifier(ordering: Ordering): CallVerifier =
            when (ordering) {
                Ordering.UNORDERED -> unorderedVerifier
                Ordering.ALL -> allVerifier
                Ordering.ORDERED -> orderedVerifier
                Ordering.SEQUENCE -> sequenceVerifier
            }

    internal fun signatureMatcherDetectorFactory(callRounds: List<CallRound>, mocks: List<Ref>): SignatureMatcherDetector {
        return SignatureMatcherDetector(callRounds, mocks, ::ChainedCallDetector)
    }

    internal val callRecorderFactories = CallRecorderFactories(
            this::signatureMatcherDetectorFactory,
            ::CallRoundBuilder,
            ::ChildHinter,
            this::verifier,
            ::AnsweringCallRecorderState,
            ::StubbingCallRecorderState,
            ::VerifyingCallRecorderState,
            ::StubbingAwaitingAnswerCallRecorderState)

    private val callRecorderTL = object : ThreadLocal<CallRecorderImpl>() {
        override fun initialValue() = CallRecorderImpl(
                stubRepo,
                instantiator,
                signatureValueGenerator,
                mockFactory,
                anyValueGenerator,
                callRecorderFactories)
    }

    override val callRecorder: CallRecorder
        get() = callRecorderTL.get()

    override val stubbingRecorder: Stubber = StubberImpl(callRecorderTL::get)
    override val verifyingRecorder: Verifier = VerifierImpl(callRecorderTL::get, stubRepo)

    companion object {
        private var log: Logger

        init {
            Logger.loggerFactory = JvmLogging.slf4jOrJulLogging()

            log = Logger<MockKGatewayImpl>()

            log.trace {
                "Starting Java MockK implementation. " +
                        "Java version = ${System.getProperty("java.version")}. "
            }

            MockKProxyMaker.log = Logger<MockKProxyMaker>().adaptor()
            MockKInstrumentationLoader.log = Logger<MockKInstrumentationLoader>().adaptor()
            MockKInstrumentation.log = Logger<MockKInstrumentation>().adaptor()

            MockKInstrumentation.init()
        }

        val defaultImplementation = MockKGatewayImpl()
        val defaultImplementationBuilder = { defaultImplementation }

        inline fun <T> useImpl(block: () -> T): T {
            MockKGateway.implementation = defaultImplementationBuilder
            return block()
        }
    }

}


