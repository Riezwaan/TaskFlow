package za.ac.taskflow

import android.content.Context
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/** One NoSQL document stores the last server-confirmed workspace snapshot for each account. */
@Entity
data class CachedWorkspace(
    @Id var id: Long = 0,
    var ownerId: String = "",
    var userJson: String = "",
    var boardsJson: String = "[]",
    var tasksJson: String = "[]",
    var statsJson: String = "{}",
    var selectedBoardId: String = ""
)

@Entity
data class PendingTaskMutation(
    @Id var id: Long = 0,
    var ownerId: String = "",
    var taskId: String = "",
    var action: String = "",
    var taskJson: String = "",
    var createdAt: Long = 0
)

/** Device-local ObjectBox cache; the hosted REST API remains the source of truth. */
object LocalWorkspaceStore {
    @Volatile private var store: BoxStore? = null

    private fun store(context: Context): BoxStore = store ?: synchronized(this) {
        store ?: MyObjectBox.builder().androidContext(context.applicationContext).build().also { store = it }
    }

    private fun box(context: Context): Box<CachedWorkspace> = store(context).boxFor(CachedWorkspace::class.java)
    private fun queue(context: Context): Box<PendingTaskMutation> = store(context).boxFor(PendingTaskMutation::class.java)

    fun read(context: Context, ownerId: String): CachedWorkspace? = try {
        box(context).query(CachedWorkspace_.ownerId.equal(ownerId)).build().use { it.findFirst() }
    } catch (_: Exception) { null }

    fun write(context: Context, ownerId: String, userJson: String, boardsJson: String, tasksJson: String, statsJson: String, selectedBoardId: String) {
        val records = box(context)
        val existing = records.query(CachedWorkspace_.ownerId.equal(ownerId)).build().use { it.findFirst() }
        records.put((existing ?: CachedWorkspace()).copy(
            ownerId = ownerId,
            userJson = userJson,
            boardsJson = boardsJson,
            tasksJson = tasksJson,
            statsJson = statsJson,
            selectedBoardId = selectedBoardId
        ))
    }

    fun pending(context: Context, ownerId: String): List<PendingTaskMutation> = try {
        queue(context).query(PendingTaskMutation_.ownerId.equal(ownerId)).build().use { it.find().sortedBy(PendingTaskMutation::createdAt) }
    } catch (_: Exception) { emptyList() }

    fun enqueue(context: Context, ownerId: String, action: String, task: Task) {
        val queue=queue(context)
        val existing=queue.query(PendingTaskMutation_.ownerId.equal(ownerId)).build().use { it.find() }
        val sameTask=existing.filter { it.taskId==task.id }
        val create=sameTask.firstOrNull { it.action=="CREATE" }
        if(action=="UPDATE" && create!=null) {
            queue.put(create.copy(taskJson=com.google.gson.Gson().toJson(task)))
            return
        }
        if(action=="DELETE" && create!=null) {
            sameTask.forEach { queue.remove(it.id) }
            return
        }
        if(action=="DELETE") sameTask.forEach { queue.remove(it.id) }
        else sameTask.filter { it.action=="UPDATE" }.forEach { queue.remove(it.id) }
        queue.put(PendingTaskMutation(ownerId=ownerId,taskId=task.id,action=action,
            taskJson=com.google.gson.Gson().toJson(task),createdAt=System.currentTimeMillis()))
    }

    fun removePending(context: Context, mutationId: Long) {
        queue(context).remove(mutationId)
    }

    fun pendingCount(context: Context, ownerId: String): Int = pending(context,ownerId).size
}
