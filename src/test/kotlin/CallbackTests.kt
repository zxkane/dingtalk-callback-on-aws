

import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.github.zxkane.dingtalk.*
import io.kotlintest.data.forall
import io.kotlintest.shouldBe
import io.kotlintest.tables.row
import org.apache.commons.codec.binary.Base64
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*



class CallbackTests : AbstractTest() {

    init {
        super.init()

        "env init" {
            System.getenv(TOKEN_NAME) shouldBe token
            System.getenv(AES_KEY_NAME) shouldBe aesKey
        }

        "check-url callback request" {
            val encryptedMap = callback.dingTalkEncryptor.getEncryptedMap(
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

        "serialize bpm events" {
            forall(
                row(Event.BPMEvent("bpmEvent", 0, "code-xxx",
                    "instance-yyyy", "corpId", ZonedDateTime.now(), ZonedDateTime.now(), "categoryId",
                    "title", "bpm-type", "staff-22222", null, "222", "http://11.com")),
                row(Event.BPMEvent("bpmEvent", 0, "code-xxx",
                    "instance-2222", "corpId", ZonedDateTime.now(), null, "categoryId",
                    "title", "bpm-type", "staff-22222", null, null, null)),
                row(Event.BPMEvent("bpms_instance_change", 0, "PROC-B508DB8F-EAD6-4E89-8DEC-92109D804FA9",
                    "b1a3eb42-3ba6-4c9a-bf6f-27869cd4b971", "ding07511073f402a4e035c2f4657eb6378f",
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(1553564588000L), ZoneOffset.UTC),
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(1553578330000L), ZoneOffset.UTC),
                    "", "xxx提交的费用报销", "finish", "manager686",
                    "agree", null, "https://aflow.dingtalk.com/dingtalk/mobile/homepage.htm"))
            ) { event ->
                callback.serializeEvent(event)

                val nameMap = HashMap<String, String>()
                nameMap["#id"] = "processInstanceId"

                val valueMap = HashMap<String, Any>()
                valueMap[":id"] = event.processInstanceId

                val querySpec = QuerySpec().withKeyConditionExpression("#id = :id").withNameMap(nameMap)
                    .withValueMap(valueMap)
                dynamoDB.getTable(System.getenv(TABLE_NAME)).query(querySpec).count() shouldBe 1
            }
        }

    }
}