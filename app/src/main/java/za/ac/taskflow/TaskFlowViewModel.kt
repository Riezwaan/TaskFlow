package za.ac.taskflow

import android.app.Application
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI

class TaskFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val debugBuild = application.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val store = SessionStore(application)
    private var token: String? = store.read()
    var endpoint by mutableStateOf(store.baseUrl); private set
    private var api = createApi(endpoint) { token }
    var user by mutableStateOf<User?>(null); private set
    var boards by mutableStateOf(emptyList<Board>()); private set
    var selectedBoard by mutableStateOf<Board?>(null); private set
    var tasks by mutableStateOf(emptyList<Task>()); private set
    var stats by mutableStateOf(Stats()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var page by mutableStateOf("Board")
    var hasSession by mutableStateOf(token != null); private set

    init { if (hasSession) refresh() }
    fun clearError() { error = null }
    fun report(message: String) { error = message }
    private fun operation(label: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try { action(); Log.i("TaskFlow", "$label succeeded") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.w("TaskFlow", "$label failed: ${e.javaClass.simpleName}")
                error = when (e) {
                    is HttpException -> {
                        if (e.code() == 401) clearSession()
                        try { JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject["error"].asString }
                        catch (_: Exception) { "Server request failed (${e.code()}). Please try again." }
                    }
                    is IOException -> "Cannot reach the server. Check your connection and try again. Your form is still here."
                    else -> "Unable to complete that action. Please try again."
                }
            } finally { busy = false }
        }
    }
    fun setEndpoint(value: String): Boolean {
        val url = value.trim().trimEnd('/') + "/"
        val valid = try {
            val uri = URI(url)
            uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
                (uri.scheme == "https" || (debugBuild && uri.scheme == "http"))
        } catch (_: Exception) { false }
        if (!valid) { error = "Enter a valid ${if (debugBuild) "HTTP or HTTPS" else "HTTPS"} server address."; return false }
        clearSession(); endpoint = url; store.baseUrl = url; api = createApi(url) { token }; error = null
        return true
    }
    fun authenticate(email: String, password: String, name: String?, done: () -> Unit) {
        Validation.credentials(email,password,name)?.let { error = it; return }
        operation("Authentication") {
            val body = AuthRequest(email.trim(),password,name?.trim())
            val response = if (name == null) api.login(body) else api.register(body)
            store.save(response.token); token = response.token; hasSession = true; user = response.user
            done(); load()
        }
    }
    private suspend fun load() {
        user = api.me()
        val loadedBoards = api.boards()
        val nextBoard = loadedBoards.firstOrNull { it.id == selectedBoard?.id } ?: loadedBoards.firstOrNull()
        val loadedTasks = nextBoard?.let { api.tasks(it.id) } ?: emptyList()
        val loadedStats = api.stats()
        boards = loadedBoards; selectedBoard = nextBoard; tasks = loadedTasks; stats = loadedStats
    }
    fun refresh() = operation("Refresh") { load() }
    fun selectBoard(board: Board) = operation("Load board") {
        val loaded = api.tasks(board.id); selectedBoard = board; tasks = loaded
    }
    fun saveBoard(name: String, editing: Board?, done: () -> Unit) {
        if (name.isBlank() || name.trim().length > 80) { error = "Enter a board name (1–80 characters)."; return }
        operation("Save board") {
            val board = if (editing == null) api.createBoard(BoardRequest(name.trim())) else api.renameBoard(editing.id,BoardRequest(name.trim()))
            selectedBoard = board; done(); load()
        }
    }
    fun deleteBoard(board: Board, done: () -> Unit) = operation("Delete board") { api.deleteBoard(board.id); done(); load() }
    fun saveTask(task: Task, done: () -> Unit) {
        Validation.task(task)?.let { error = it; return }
        operation("Save task") {
            val normalized = task.copy(title = task.title.trim())
            val saved = if (task.id.isEmpty()) api.createTask(normalized) else api.updateTask(task.id,normalized)
            tasks = listOf(saved) + tasks.filterNot { it.id == saved.id }; done(); stats = api.stats()
        }
    }
    fun deleteTask(task: Task, done: () -> Unit) = operation("Delete task") {
        api.deleteTask(task.id); tasks = tasks.filterNot { it.id == task.id }; done()
    }
    fun saveSettings(name: String, theme: String, reminders: Boolean, priority: String) {
        if (name.isBlank() || name.trim().length > 80) { error = "Enter your name (1–80 characters)."; return }
        operation("Save settings") { user = api.settings(SettingsRequest(name.trim(),theme,reminders,priority)) }
    }
    private fun clearSession() {
        token = null; store.clear(); hasSession = false; user = null; boards = emptyList(); tasks = emptyList(); selectedBoard = null; stats = Stats(); page = "Board"
    }
    fun signOut() = operation("Sign out") { api.logout(); clearSession() }
    fun forgetSession() { if (!busy) clearSession() }
}
