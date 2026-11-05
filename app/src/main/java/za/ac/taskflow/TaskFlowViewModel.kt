package za.ac.taskflow

import android.app.Application
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import com.google.gson.JsonParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.net.URI
import java.util.UUID
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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
    var attachments by mutableStateOf(emptyList<TaskAttachment>()); private set
    var stats by mutableStateOf(Stats()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var page by mutableStateOf("Board")
    var hasSession by mutableStateOf(token != null); private set
    var offlineSnapshot by mutableStateOf(false); private set
    var pendingOfflineChanges by mutableIntStateOf(0); private set
    private val gson=Gson()

    init { if (hasSession) { restoreLocalSnapshot(); refresh() } }
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
                    is IOException -> { offlineSnapshot = user != null; "Cannot reach the server. Check your connection and try again. Your last saved workspace is available for viewing." }
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
            store.save(response.token); store.cachedOwnerId=response.user.id; token = response.token; hasSession = true; user = response.user
            done(); load()
        }
    }
    private suspend fun load() {
        user = api.me()
        syncPendingTasks()
        val loadedBoards = api.boards()
        val nextBoard = loadedBoards.firstOrNull { it.id == selectedBoard?.id } ?: loadedBoards.firstOrNull()
        val loadedTasks = nextBoard?.let { api.tasks(it.id) } ?: emptyList()
        val loadedStats = api.stats()
        boards = loadedBoards; selectedBoard = nextBoard; tasks = loadedTasks; stats = loadedStats
        offlineSnapshot=false
        saveLocalSnapshot()
    }
    private suspend fun syncPendingTasks() {
        val owner=user?.id ?: return
        LocalWorkspaceStore.pending(getApplication(),owner).forEach { mutation ->
            val pendingTask=gson.fromJson(mutation.taskJson,Task::class.java)
            try {
                when(mutation.action) {
                    "CREATE" -> api.createTask(pendingTask)
                    "UPDATE" -> api.updateTask(mutation.taskId,pendingTask.copy(id=mutation.taskId))
                    "DELETE" -> api.deleteTask(mutation.taskId)
                }
                LocalWorkspaceStore.removePending(getApplication(),mutation.id)
                pendingOfflineChanges=LocalWorkspaceStore.pendingCount(getApplication(),owner)
            } catch(e:HttpException) {
                if(mutation.action=="DELETE" && e.code()==404) {
                    LocalWorkspaceStore.removePending(getApplication(),mutation.id)
                    pendingOfflineChanges=LocalWorkspaceStore.pendingCount(getApplication(),owner)
                } else throw e
            }
        }
    }
    private fun saveLocalSnapshot() {
        val currentUser=user ?: return
        try {
            LocalWorkspaceStore.write(getApplication(),currentUser.id,gson.toJson(currentUser),gson.toJson(boards),gson.toJson(tasks),gson.toJson(stats),selectedBoard?.id ?: "")
        } catch (e:Exception) { Log.w("TaskFlow","Local NoSQL cache write failed: ${e.javaClass.simpleName}") }
    }
    private fun restoreLocalSnapshot() {
        val owner=store.cachedOwnerId ?: return
        val cached=LocalWorkspaceStore.read(getApplication(),owner) ?: return
        try {
            user=gson.fromJson(cached.userJson,User::class.java)
            val boardType=object:TypeToken<List<Board>>() {}.type
            val taskType=object:TypeToken<List<Task>>() {}.type
            boards=gson.fromJson(cached.boardsJson,boardType) ?: emptyList()
            tasks=gson.fromJson(cached.tasksJson,taskType) ?: emptyList()
            stats=gson.fromJson(cached.statsJson,Stats::class.java) ?: Stats()
            selectedBoard=boards.firstOrNull { it.id==cached.selectedBoardId } ?: boards.firstOrNull()
            pendingOfflineChanges=LocalWorkspaceStore.pendingCount(getApplication(),owner)
            offlineSnapshot=true
        } catch (e:Exception) {
            Log.w("TaskFlow","Local NoSQL cache read failed: ${e.javaClass.simpleName}")
            user=null; boards=emptyList(); tasks=emptyList(); selectedBoard=null; stats=Stats()
        }
    }
    fun refresh() = operation("Refresh") { load() }
    fun selectBoard(board: Board) = operation("Load board") {
        val loaded = api.tasks(board.id); selectedBoard = board; tasks = loaded; saveLocalSnapshot()
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
        if(offlineSnapshot) { queueOfflineTask(task,done); return }
        operation("Save task") {
            val normalized = task.copy(id=task.id.ifEmpty { UUID.randomUUID().toString() },title = task.title.trim())
            val saved = try {
                if (task.id.isEmpty()) api.createTask(normalized) else api.updateTask(task.id,normalized)
            } catch(e:IOException) {
                queueOfflineTask(normalized,done,task.id.isEmpty())
                return@operation
            }
            tasks = listOf(saved) + tasks.filterNot { it.id == saved.id }; done(); stats = api.stats(); saveLocalSnapshot()
        }
    }
    private fun queueOfflineTask(task:Task,done:()->Unit,creating:Boolean=task.id.isEmpty()) {
        val owner=user?.id ?: run { error="Sign in to save tasks."; return }
        val localTask=task.copy(id=if(creating) UUID.randomUUID().toString() else task.id,title=task.title.trim())
        LocalWorkspaceStore.enqueue(getApplication(),owner,if(creating) "CREATE" else "UPDATE",localTask)
        tasks=listOf(localTask)+tasks.filterNot { it.id==localTask.id }
        pendingOfflineChanges=LocalWorkspaceStore.pendingCount(getApplication(),owner)
        offlineSnapshot=true
        error="Saved on this device. $pendingOfflineChanges change(s) will sync when the connection returns."
        saveLocalSnapshot(); done()
    }
    fun deleteTask(task: Task, done: () -> Unit) {
        if(offlineSnapshot) { queueOfflineDelete(task,done); return }
        operation("Delete task") {
            try { api.deleteTask(task.id) }
            catch(e:IOException) { queueOfflineDelete(task,done); return@operation }
            tasks = tasks.filterNot { it.id == task.id }; attachments = emptyList(); done(); saveLocalSnapshot()
        }
    }
    private fun queueOfflineDelete(task:Task,done:()->Unit) {
        val owner=user?.id ?: run { error="Sign in to delete tasks."; return }
        LocalWorkspaceStore.enqueue(getApplication(),owner,"DELETE",task)
        tasks=tasks.filterNot { it.id==task.id }; attachments=emptyList()
        pendingOfflineChanges=LocalWorkspaceStore.pendingCount(getApplication(),owner)
        offlineSnapshot=true
        error=if(pendingOfflineChanges==0) "Offline task changes were cancelled." else "Deleted on this device. $pendingOfflineChanges change(s) will sync when the connection returns."
        saveLocalSnapshot(); done()
    }
    fun loadAttachments(taskId: String) { attachments=emptyList(); operation("Load task images") { attachments=api.attachments(taskId) } }
    fun uploadImage(taskId: String, uri: Uri) {
        val resolver=getApplication<Application>().contentResolver
        val type=resolver.getType(uri)?.lowercase()
        if(type !in setOf("image/jpeg","image/png","image/webp")) { error="Choose a JPEG, PNG or WebP image."; return }
        val filename=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "task-image"
        operation("Upload task image") {
            val bytes=withContext(Dispatchers.IO) { try { resolver.openInputStream(uri)?.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                var total=0
                while(true) {
                    val count=input.read(buffer)
                    if(count<0) break
                    total+=count
                    if(total>5*1024*1024) break
                    output.write(buffer,0,count)
                }
                if(total>5*1024*1024) null else output.toByteArray()
            } } catch (_:Exception) { null } }
            if(bytes==null || bytes.isEmpty()) { error="Choose an image smaller than 5 MB."; return@operation }
            api.uploadAttachment(taskId,filename,bytes.toRequestBody(type!!.toMediaType()))
            attachments=api.attachments(taskId)
        }
    }
    fun readImage(taskId: String, attachmentId: String, show: (ByteArray) -> Unit) = operation("Read task image") {
        show(api.downloadAttachment(taskId,attachmentId).bytes())
    }
    fun deleteImage(taskId: String, attachmentId: String) = operation("Delete task image") {
        api.deleteAttachment(taskId,attachmentId); attachments=api.attachments(taskId)
    }
    fun saveSettings(name: String, theme: String, reminders: Boolean, priority: String) {
        if (name.isBlank() || name.trim().length > 80) { error = "Enter your name (1–80 characters)."; return }
        operation("Save settings") { user = api.settings(SettingsRequest(name.trim(),theme,reminders,priority)); saveLocalSnapshot() }
    }
    private fun clearSession() {
        token = null; store.clear(); hasSession = false; offlineSnapshot=false; pendingOfflineChanges=0; user = null; boards = emptyList(); tasks = emptyList(); attachments=emptyList(); selectedBoard = null; stats = Stats(); page = "Board"
    }
    fun signOut() = operation("Sign out") { api.logout(); clearSession() }
    fun forgetSession() { if (!busy) clearSession() }
}
