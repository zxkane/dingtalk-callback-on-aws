
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.local.main.ServerRunner
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.dingtalk.oapi.lib.aes.Utils
import com.github.zxkane.dingtalk.*
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.kotlintest.Spec
import io.kotlintest.extensions.TopLevelTest
import io.kotlintest.specs.StringSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParametersRequest
import software.amazon.awssdk.services.ssm.model.GetParametersResponse
import software.amazon.awssdk.services.ssm.model.Parameter
import java.io.IOException
import java.net.ServerSocket
import java.util.*




@Suppress("UNCHECKED_CAST")
@Throws(Exception::class)
fun setEnv(newenv: Map<String, String>) {
    try {
        val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
        val theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment")
        theEnvironmentField.isAccessible = true
        val env = theEnvironmentField.get(null) as MutableMap<String, String>
        env.putAll(newenv)
        val theCaseInsensitiveEnvironmentField =
            processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment")
        theCaseInsensitiveEnvironmentField.isAccessible = true
        val cienv = theCaseInsensitiveEnvironmentField.get(null) as MutableMap<String, String>
        cienv.putAll(newenv)
    } catch (e: NoSuchFieldException) {
        val classes = Collections::class.java.getDeclaredClasses()
        val env = System.getenv()
        for (cl in classes) {
            if ("java.util.Collections\$UnmodifiableMap" == cl.getName()) {
                val field = cl.getDeclaredField("m")
                field.setAccessible(true)
                val obj = field.get(env)
                val map = obj as MutableMap<String, String>
                map.clear()
                map.putAll(newenv)
            }
        }
    }
}

@Throws(IOException::class)
fun getAvailablePort(): String {
    val serverSocket = ServerSocket(0)
    return serverSocket.localPort.toString()
}

abstract class AbstractTest : StringSpec() {

    val context: Context = Mockito.mock(Context::class.java)
    val token: String = Utils.getRandomStr(8)
    val aesKey: String = Utils.getRandomStr(43)
    val nonce: String = Utils.getRandomStr(6)
    val timestamp = System.currentTimeMillis()
    lateinit var dynamoDBProxyServer: DynamoDBProxyServer
    val dynamoDBPort = getAvailablePort()
    lateinit var dynamoDBClient: AmazonDynamoDB
    lateinit var callback: Callback

    private val CORP = "mycorp"

    override fun beforeSpecClass(spec: Spec, tests: List<TopLevelTest>) {
        super.beforeSpecClass(spec, tests)
        //Need to set the SQLite4Java library path to avoid a linker error
        System.setProperty("sqlite4java.library.path", "./build/libs/")

        // Create an in-memory and in-process instance of DynamoDB Local that runs over HTTP
        val localArgs = arrayOf("-inMemory", "-port", dynamoDBPort)

        dynamoDBProxyServer = ServerRunner.createServerFromCommandLineArgs(localArgs)
        dynamoDBProxyServer.start()

        dynamoDBClient = createAmazonDynamoDBClient()

        val ssmClient = mock<SsmClient>()
        whenever(ssmClient.getParameters(ArgumentMatchers.any(GetParametersRequest::class.java)))
            .thenReturn(GetParametersResponse.builder().parameters(
                Parameter.builder().name(token).value(token).build(),
                Parameter.builder().name(aesKey).value(aesKey).build(),
                Parameter.builder().name(CORP).value(CORP).build()
            ).build())
        callback = Callback(DynamoDB(dynamoDBClient), ssmClient)
    }

    override fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        dynamoDBProxyServer.stop()
    }

    fun init() {
        setEnv(mapOf(TOKEN_NAME to token, AES_KEY_NAME to aesKey, CORPID_NAME to CORP,
            TABLE_NAME to "table-name"))
        whenever(context.logger).thenReturn(mock(LambdaLogger::class.java))
    }

    private fun createAmazonDynamoDBClient(): AmazonDynamoDB {
        return AmazonDynamoDBClientBuilder.standard()
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(
                "http://localhost:$dynamoDBPort", "us-west-2"))
            .build()
    }

    private fun createMyTables(client: AmazonDynamoDB) {
        //Create task tables
        val mapper = DynamoDBMapper(client)
        val tableRequest = mapper.generateCreateTableRequest(Event.BPMEvent::class.java)
        tableRequest.provisionedThroughput = ProvisionedThroughput(1L, 1L)
        client.createTable(tableRequest)

    }
}