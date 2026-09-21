package za.ac.taskflow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
class ApiTest {
    @Test fun retrofitSendsBearerTokenAndReadsBoards() = runTest {
        val server=MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""[{"id":"1","name":"Study"}]"""))
            val api=createApi(server.url("/").toString()) {"test-session"}
            assertEquals("Study",api.boards().single().name)
            val request=server.takeRequest()
            assertEquals("/api/boards",request.path)
            assertEquals("Bearer test-session",request.getHeader("Authorization"))
        } finally {server.shutdown()}
    }
    @Test fun retrofitPropagatesAuthenticationErrors() = runTest {
        val server=MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Please sign in."}"""))
            val api=createApi(server.url("/").toString()) {null}
            try {api.me();fail("Expected HTTP 401")} catch(e:HttpException) {assertEquals(401,e.code())}
        } finally {server.shutdown()}
    }
}
