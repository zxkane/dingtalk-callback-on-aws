package com.github.zxkane.dingtalk

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec
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
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParametersRequest
import software.amazon.awssdk.services.ssm.model.Parameter
import java.time.ZonedDateTime
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaType

const val TOKEN_NAME = "PARA_DD_TOKEN"
const val AES_KEY_NAME = "PARA_DD_AES_KEY"
const val CORPID_NAME = "PARA_DD_CORPID"

const val QUERY_PARAMETER_SIGNATURE = "signature"
const val QUERY_PARAMETER_TIMESTAMP = "timestamp"
const val QUERY_PARAMETER_NONCE = "nonce"

const val TABLE_NAME = "TABLE_NAME"

const val RESPONSE_MSG = "success"

const val NONCE_LENGTH = 12
const val STATUS_CODE = 200

val objectMapper = ObjectMapper().registerModules(JavaTimeModule()).registerKotlinModule()

class Callback(dynamoDb: DynamoDB? = null, ssmclient: SsmClient? = null) : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    companion object {

        internal val logger = LogManager.getLogger(Callback::class.java)

        init {
            objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        }
    }

    var dynamoDb: DynamoDB
    var dingTalkEncryptor: DingTalkEncryptor

    init {
        this.dynamoDb = dynamoDb ?: DynamoDB(AmazonDynamoDBClientBuilder.defaultClient())
        val ssmClient = ssmclient ?: SsmClient.builder().build()
        val dingtalkParameters = ssmClient.getParameters(GetParametersRequest.builder().names(
            System.getenv(TOKEN_NAME),
            System.getenv(AES_KEY_NAME),
            System.getenv(CORPID_NAME)).withDecryption(true).build()).parameters()
         dingTalkEncryptor = DingTalkEncryptor(
             getParameter(dingtalkParameters, System.getenv(TOKEN_NAME)),
             getParameter(dingtalkParameters, System.getenv(AES_KEY_NAME)),
             getParameter(dingtalkParameters, System.getenv(CORPID_NAME)))
    }

    private fun getParameter(parameters: List<Parameter>, name: String): String {
        return parameters.first { p -> p.name() == name }.value()
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
                serializeEvent(event)
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

    private fun serializeEvent(event: Event) {
        val item = Item()

        event.javaClass.kotlin.declaredMemberProperties.forEach { prop ->
            when (prop.returnType.javaType.typeName) {
                String::class.java.typeName -> {
                    val value = prop.get(event) as String?
                    item.withString(prop.name, value ?: "null")
                }
                "java.util.List<java.lang.String>" -> {
                    val value = prop.get(event) as List<*>?
                    item.withList(prop.name, value)
                }
                Long::class.java.typeName -> {
                    item.withLong(prop.name, prop.get(event) as Long)
                }
                ZonedDateTime::class.java.typeName -> {
                    val value = prop.get(event) as ZonedDateTime?
                    item.withString(prop.name, value?.toLocalDateTime().toString())
                }
                else -> logger.warn("Unrecognized prop '${prop.name}' with type ${prop.returnType}.")
            }
        }

        dynamoDb.getTable(System.getProperty(TABLE_NAME)!!).putItem(PutItemSpec().withItem(item))
    }
}
