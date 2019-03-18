

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.github.zxkane.dingtalk.*
import io.kotlintest.shouldBe
import org.apache.commons.codec.binary.Base64

class CallbackTests : AbstractTest() {

    init {
        super.init()

        val callback = com.github.zxkane.dingtalk.Callback()

        "env init" {
            System.getenv(TOKEN_NAME) shouldBe token
            System.getenv(AES_KEY_NAME) shouldBe aesKey
        }

        "check-url callback request" {
            val encryptedMap = Callback.dingTalkEncryptor.getEncryptedMap(
                """
                    {
                        "EventType" : "check_url"
                    }
                """.trimIndent(),
                timestamp, nonce)
            val encryptedMsg = objectMapper.writeValueAsString(EncryptedEvent(encryptedMap.get("encrypt") as String))

            val apiRequest = APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/callback")
                .withHeaders(mapOf("content-type" to "application/json"))
                .withQueryStringParameters(mapOf(
                    QUERY_PARAMETER_SIGNATURE to encryptedMap.get("msg_signature") as String,
                    QUERY_PARAMETER_NONCE to nonce,
                    QUERY_PARAMETER_TIMESTAMP to timestamp.toString()))
                .withBody(Base64.encodeBase64String(encryptedMsg.toByteArray()))
                .withIsBase64Encoded(true)

            callback.handleRequest(apiRequest, context)
        }

    }
}