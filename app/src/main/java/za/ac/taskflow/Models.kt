package za.ac.taskflow

import java.time.LocalDate

data class User(val id: String, val name: String, val email: String, val theme: String = "system", val reminders: Boolean = true, val defaultPriority: String = "MEDIUM")
data class AuthRequest(val email: String, val password: String, val name: String? = null)
data class AuthResponse(val token: String, val user: User)
data class Board(val id: String, val name: String)
data class BoardRequest(val name: String)
data class ChecklistItem(val text: String, val done: Boolean = false)
data class Task(val id: String = "", val boardId: String, val title: String, val description: String = "",
    val status: String = "TODO", val priority: String = "MEDIUM", val dueDate: String? = null,
    val checklist: List<ChecklistItem> = emptyList(), val createdAt: String = "", val updatedAt: String = "")
data class Stats(val completed: Int = 0, val points: Int = 0, val streak: Int = 0, val badges: List<String> = emptyList())
data class SettingsRequest(val name: String, val theme: String, val reminders: Boolean, val defaultPriority: String)

object Validation {
    fun credentials(email: String, password: String, name: String? = null): String? = when {
        name != null && (name.isBlank() || name.trim().length > 80) -> "Enter your name (1–80 characters)."
        !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) || email.trim().length > 254 -> "Enter a valid email address."
        password.length !in 8..128 -> "Use a password with 8–128 characters."
        else -> null
    }
    fun task(task: Task): String? = when {
        task.title.isBlank() || task.title.trim().length > 120 -> "Enter a title (1–120 characters)."
        task.description.length > 4000 -> "Keep the description under 4,000 characters."
        task.status !in listOf("TODO", "DOING", "DONE") -> "Choose a valid status."
        task.priority !in listOf("LOW", "MEDIUM", "HIGH") -> "Choose a valid priority."
        task.dueDate != null && !validDate(task.dueDate) -> "Use a valid date in YYYY-MM-DD format."
        task.checklist.size > 30 || task.checklist.any { it.text.isBlank() || it.text.length > 160 } -> "Use up to 30 checklist items, each 1–160 characters."
        else -> null
    }
    fun validDate(value: String): Boolean = try { LocalDate.parse(value).toString() == value } catch (_: Exception) { false }
}

fun filterTasks(tasks: List<Task>, search: String, priority: String, due: String, today: LocalDate = LocalDate.now()): List<Task> =
    tasks.filter { task -> task.title.contains(search.trim(), ignoreCase = true) &&
        (priority == "ALL" || task.priority == priority) && when (due) {
            "TODAY" -> task.dueDate == today.toString()
            "OVERDUE" -> task.dueDate?.let { it < today.toString() && task.status != "DONE" } ?: false
            else -> true
        }
    }
