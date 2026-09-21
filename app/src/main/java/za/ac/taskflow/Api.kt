package za.ac.taskflow

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TaskFlowApi {
    @POST("api/auth/register") suspend fun register(@Body body: AuthRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body body: AuthRequest): AuthResponse
    @POST("api/auth/logout") suspend fun logout()
    @GET("api/me") suspend fun me(): User
    @PUT("api/me") suspend fun settings(@Body body: SettingsRequest): User
    @GET("api/boards") suspend fun boards(): List<Board>
    @POST("api/boards") suspend fun createBoard(@Body body: BoardRequest): Board
    @PUT("api/boards/{id}") suspend fun renameBoard(@Path("id") id: String, @Body body: BoardRequest): Board
    @DELETE("api/boards/{id}") suspend fun deleteBoard(@Path("id") id: String)
    @GET("api/boards/{id}/tasks") suspend fun tasks(@Path("id") id: String): List<Task>
    @POST("api/tasks") suspend fun createTask(@Body body: Task): Task
    @PUT("api/tasks/{id}") suspend fun updateTask(@Path("id") id: String, @Body body: Task): Task
    @DELETE("api/tasks/{id}") suspend fun deleteTask(@Path("id") id: String)
    @GET("api/stats") suspend fun stats(): Stats
}

// Retrofit's typed service keeps HTTP details outside the Compose screens.
// Reference: https://github.com/square/retrofit
fun createApi(baseUrl: String, token: () -> String?): TaskFlowApi {
    val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            token()?.let { request.header("Authorization", "Bearer $it") }
            chain.proceed(request.build())
        }.build()
    return Retrofit.Builder().baseUrl(baseUrl).client(client).addConverterFactory(GsonConverterFactory.create())
        .build().create(TaskFlowApi::class.java)
}
