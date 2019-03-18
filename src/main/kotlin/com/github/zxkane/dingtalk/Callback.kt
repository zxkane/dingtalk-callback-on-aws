package com.github.zxkane.dingtalk

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.dingtalk.oapi.lib.aes.DingTalkEncryptor
import com.dingtalk.oapi.lib.aes.Utils
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.commons.codec.binary.Base64
import org.apache.logging.log4j.LogManager

const val TOKEN_NAME = "DD_TOKEN"
const val AES_KEY_NAME = "DD_AES_KEY"
const val CORPID_NAME = "DD_CORPID"

const val QUERY_PARAMETER_SIGNATURE = "signature"
const val QUERY_PARAMETER_TIMESTAMP = "timestamp"
const val QUERY_PARAMETER_NONCE = "nonce"

const val BPM_TABLE_NAME = "bpm_raw"
const val BPM_TABLE_PRIMARY_KEY_NAME = "processInstanceId"

const val ORG_TABLE_NAME = "org_raw"
const val ORG_TABLE_PRIMARY_KEY_NAME = "eventType"

const val RESPONSE_MSG = "success"

const val NONCE_LENGTH = 12
const val STATUS_CODE = 200

val objectMapper = ObjectMapper().registerModules(JavaTimeModule()).registerKotlinModule()

class Callback : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    companion object {

        internal val logger = LogManager.getLogger(Callback::class.java)
        val dingTalkEncryptor = DingTalkEncryptor(System.getenv(TOKEN_NAME),
            System.getenv(AES_KEY_NAME),
            System.getenv(CORPID_NAME))

        init {
            objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        }
    }

    override fun handleRequest(request: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        logger.debug("Callback request is $request")

        val encryptedEvent = objectMapper.readValue<EncryptedEvent>(
            if (request.isBase64Encoded) String(Base64.decodeBase64(request.body)) else request.body,
            EncryptedEvent::class.java
        )

        logger.debug("Encrypted callback event is $encryptedEvent.")

        val eventJson = dingTalkEncryptor.getDecryptMsg(
            request.queryStringParameters.get(QUERY_PARAMETER_SIGNATURE),
            request.queryStringParameters.get(QUERY_PARAMETER_TIMESTAMP),
            request.queryStringParameters.get(QUERY_PARAMETER_NONCE),
            encryptedEvent.encrypt)

        logger.debug("Event json is $eventJson.")

        val event = objectMapper.readValue<Event>(eventJson, Event::class.java)

        when (event.type) {
            "check_url" -> {
                logger.debug("Received callback validation request.")
            }
            "bpms_instance_change", "bpms_task_change" -> {
                logger.debug("BPM $event is received.")
            }
            "user_add_org", "user_modify_org", "user_leave_org", "org_admin_add",
                "org_admin_remove", "org_dept_create", "org_dept_modify", "org_dept_remove",
                "org_change" -> {
                logger.debug("Org event $event is received.")
            }
            else -> {
                logger.debug("Unrecognized event $event is received.")
            }
        }

        val response = dingTalkEncryptor.getEncryptedMap(RESPONSE_MSG, System.currentTimeMillis(),
            Utils.getRandomStr(NONCE_LENGTH))

        logger.debug("Callback response is $response.")

        return APIGatewayProxyResponseEvent()
            .withBody(objectMapper.writeValueAsString(response))
            .withHeaders(mapOf("content-type" to "application/json"))
            .withStatusCode(STATUS_CODE)
    }
}
