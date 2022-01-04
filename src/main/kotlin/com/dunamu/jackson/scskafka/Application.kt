package com.dunamu.jackson.scskafka

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.bind.annotation.*
import java.util.function.Consumer

data class PushRequest(
    val uuid: String,
    val receiverId: String,
    val message: String,
    val succeed: Boolean = false,
    val retryCount: Int = 0,
    val additionalData: Any? = null
)

@RestController
@RequestMapping("/api")
class Apis(private val streamBridge: StreamBridge) {

    @PostMapping("/push/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun sendRequest(@RequestBody request: PushRequest) {
        streamBridge.send(
            "push-request-out-0",
            MessageBuilder
                .withPayload(request)
                .setHeader("partitionKey", request.receiverId)
                .build()
        )
    }

}

@SpringBootApplication
class Application {

    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var streamBridge: StreamBridge

    fun doSend(request: PushRequest): Int {
        // FIXME 실제 푸시 정송 처리 후 결과를 리턴 (1: 성공, 2: 재시도, 3: 실패)
        val result = request.receiverId.hashCode() % 3
        if (result == 2) {
            return if (canRetry(request)) {
                2
            } else {
                log.info("Maximum retries(3) reached. this request will be failed.")
                3
            }
        }
        return result
    }

    fun canRetry(request: PushRequest, maxCount: Int = 3): Boolean {
        return request.retryCount < maxCount
    }

    @Bean
    fun sendPush(): Consumer<Message<PushRequest>> = Consumer { message ->
        val partitionId = message.headers[KafkaHeaders.RECEIVED_PARTITION_ID] as Int
        val payload = message.payload

        log.info("PushRequest received: [partition=$partitionId] $payload")
        when (doSend(payload)) {
            1 -> {
                log.info("Push send succeed.")
                streamBridge.send(
                    "push-send-succeed-out-0",
                    MessageBuilder
                        .withPayload(payload.copy(succeed = true))
                        .setHeader("partitionKey", payload.receiverId)
                        .build()
                )
            }
            2 -> {
                log.info("Push send will be retry.")
                streamBridge.send(
                    "push-send-retry-out-0",
                    MessageBuilder
                        .withPayload(payload.copy(succeed = false, retryCount = payload.retryCount + 1))
                        .setHeader("partitionKey", payload.receiverId)
                        .build()
                )
            }
            else -> {
                log.info("Push send failed.")
                streamBridge.send(
                    "push-send-failed-out-0",
                    MessageBuilder
                        .withPayload(payload.copy(succeed = false))
                        .setHeader("partitionKey", payload.receiverId)
                        .build()
                )
            }
        }
    }

    @Bean
    fun postSendSucceed(): Consumer<Message<PushRequest>> = Consumer { message ->
        val partitionId = message.headers[KafkaHeaders.RECEIVED_PARTITION_ID] as Int
        val payload = message.payload

        log.info("Push send succeed received: [partition=$partitionId] $payload")
    }

    @Bean
    fun postSendFailed(): Consumer<Message<PushRequest>> = Consumer { message ->
        val partitionId = message.headers[KafkaHeaders.RECEIVED_PARTITION_ID] as Int
        val payload = message.payload

        log.info("Push send failed received: [partition=$partitionId] $payload")
    }

}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}