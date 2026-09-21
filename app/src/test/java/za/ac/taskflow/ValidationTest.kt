package za.ac.taskflow
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class ValidationTest {
    private fun task() = Task(boardId="board",title="Read chapter",dueDate="2026-09-21",priority="HIGH")
    @Test fun credentialsRejectInvalidInput() {
        assertNotNull(Validation.credentials("bad","12345678"))
        assertNotNull(Validation.credentials("a@b.com","short"))
        assertNotNull(Validation.credentials("a@b.com","12345678"," "))
        assertNull(Validation.credentials("a@b.com","12345678","Student"))
    }
    @Test fun calendarDatesAreStrict() {
        assertFalse(Validation.validDate("2026-02-30"))
        assertFalse(Validation.validDate("2026-13-01"))
        assertTrue(Validation.validDate("2028-02-29"))
        assertNotNull(Validation.task(task().copy(dueDate="not a date")))
    }
    @Test fun taskValidationCoversLimitsAndEnums() {
        assertNull(Validation.task(task()))
        assertNotNull(Validation.task(task().copy(title=" ")))
        assertNotNull(Validation.task(task().copy(title="a".repeat(121))))
        assertNotNull(Validation.task(task().copy(priority="URGENT")))
        assertNotNull(Validation.task(task().copy(status="INVALID")))
        assertNotNull(Validation.task(task().copy(checklist=listOf(ChecklistItem("")))))
    }
    @Test fun filtersCombineCaseInsensitiveSearchPriorityAndDates() {
        val tasks=listOf(task(),task().copy(title="Other",priority="LOW"),task().copy(title="Read more",dueDate="2026-09-20"))
        val today=LocalDate.parse("2026-09-21")
        assertEquals(1,filterTasks(tasks,"READ","HIGH","TODAY",today).size)
        assertEquals(1,filterTasks(tasks,"read","ALL","OVERDUE",today).size)
        assertEquals(0,filterTasks(listOf(task().copy(status="DONE",dueDate="2026-09-20")),"","ALL","OVERDUE",today).size)
    }
}
