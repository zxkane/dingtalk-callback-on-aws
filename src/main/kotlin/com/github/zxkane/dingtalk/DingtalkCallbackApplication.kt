package com.github.zxkane.dingtalk

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.messaging.Message
import java.util.function.Function

@SpringBootApplication
open class DingtalkCallbackApplication {

    @Bean
    open fun dingtalkCallback(): Function<Message<EncryptedEvent>, Map<String, String>> {
        val callback = Callback()
        return Function {
            callback.handleRequest(it)
        }
    }
}
fun main(args: Array<String>) {
    SpringApplication.run(DingtalkCallbackApplication::class.java, *args)
}
