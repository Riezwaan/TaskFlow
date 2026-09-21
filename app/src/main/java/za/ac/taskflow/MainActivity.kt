package za.ac.taskflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TaskFlowApp() }
    }
}
private val Leaf = Color(0xFF345941)
private val Cream = Color(0xFFF7F6F0)
@Composable fun TaskFlowApp(vm: TaskFlowViewModel = viewModel()) {
    val dark = when(vm.user?.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = if(dark) darkColorScheme(primary=Color(0xFFB4D0A6),background=Color(0xFF171D18),surface=Color(0xFF202721))
        else lightColorScheme(primary=Leaf,secondary=Color(0xFF7B6C45),background=Cream,surface=Color.White,surfaceVariant=Color(0xFFE8EBDC))
    MaterialTheme(colorScheme=colors) {
        Surface(Modifier.fillMaxSize()) {
            if(vm.hasSession) Home(vm) else AuthScreen(vm)
        }
    }
}
@Composable private fun Heading(kicker: String, title: String, subtitle: String? = null) {
    Text(kicker.uppercase(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,letterSpacing=2.sp)
    Spacer(Modifier.height(6.dp))
    Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
    if(subtitle!=null) { Spacer(Modifier.height(8.dp)); Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun ErrorNotice(vm: TaskFlowViewModel) {
    vm.error?.let { message ->
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer),modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.onErrorContainer)
                IconButton(onClick=vm::clearError) { Icon(Icons.Outlined.Close,"Dismiss error") }
            }
        }
    }
}
@Composable private fun AuthScreen(vm: TaskFlowViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var connection by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(32.dp))
        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.primary) {
            Icon(Icons.Outlined.ViewKanban,null,Modifier.padding(18.dp).size(42.dp),tint=MaterialTheme.colorScheme.onPrimary)
        }
        Heading("TaskFlow / your day, in motion",if(register) "Make room for\nwhat matters." else "A little focus.\nA lot of progress.",if(register) "Create your account and start your first board." else "Sign in to pick up where you left off.")
        ErrorNotice(vm)
        if(register) OutlinedTextField(name,{name=it},label={Text("Full name")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(email,{email=it},label={Text("Email address")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Password")},supportingText={Text("8–128 characters")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth())
        if(register) OutlinedTextField(confirm,{confirm=it},label={Text("Confirm password")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={
            if(register && password!=confirm) vm.report("The passwords do not match.")
            else vm.authenticate(email,password,if(register) name else null) { password="";confirm="" }
        },enabled=!vm.busy,modifier=Modifier.fillMaxWidth().height(52.dp)) { Text(if(vm.busy) "Connecting…" else if(register) "Create account" else "Sign in") }
        TextButton(onClick={register=!register;vm.clearError()},enabled=!vm.busy,modifier=Modifier.align(Alignment.CenterHorizontally)) { Text(if(register) "Already have an account? Sign in" else "New here? Create an account") }
        HorizontalDivider()
        Text("Plan it. Move it. Finish it.",style=MaterialTheme.typography.titleMedium)
        Text("Three simple columns to turn your plans into progress.",style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick={connection=true},enabled=!vm.busy) { Icon(Icons.Outlined.Cloud,null);Spacer(Modifier.width(8.dp));Text("Server connection") }
    }
    if(connection) ConnectionDialog(vm) { connection=false }
}
@Composable private fun ConnectionDialog(vm: TaskFlowViewModel, close: () -> Unit) {
    var url by remember { mutableStateOf(vm.endpoint) }
    AlertDialog(onDismissRequest=close,title={Text("Server connection")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Enter your deployed TaskFlow API address. For emulator development, use http://10.0.2.2:3000/.")
        OutlinedTextField(url,{url=it},label={Text("API address")},singleLine=true)
        ErrorNotice(vm)
    }},confirmButton={TextButton(onClick={if(vm.setEndpoint(url)) close()}) {Text("Save connection")}},dismissButton={TextButton(onClick=close) {Text("Cancel")}})
}
@Composable private fun Home(vm: TaskFlowViewModel) {
    var editTask by remember { mutableStateOf<Task?>(null) }
    var detail by remember { mutableStateOf<Task?>(null) }
    var boardDialog by remember { mutableStateOf(false) }
    var editingBoard by remember { mutableStateOf<Board?>(null) }
    Scaffold(bottomBar={ NavigationBar {
        val pages = listOf("Board" to Icons.Outlined.ViewKanban,"Progress" to Icons.Outlined.Insights,"Reminders" to Icons.Outlined.NotificationsNone,"Settings" to Icons.Outlined.Settings)
        pages.forEach { (label,icon) -> NavigationBarItem(selected=vm.page==label,onClick={vm.page=label},icon={Icon(icon,label)},label={Text(label)}) }
    } },floatingActionButton={if(vm.page=="Board" && vm.selectedBoard!=null) FloatingActionButton(onClick={editTask=Task(boardId=vm.selectedBoard!!.id,title="",priority=vm.user?.defaultPriority ?: "MEDIUM")}) {Icon(Icons.Outlined.Add,"Add task")} }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("TASKFLOW",Modifier.weight(1f),fontWeight=FontWeight.Bold,letterSpacing=3.sp,color=MaterialTheme.colorScheme.primary)
                IconButton(onClick=vm::refresh,enabled=!vm.busy) {Icon(Icons.Outlined.Refresh,"Refresh from server")}
            }
            if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ErrorNotice(vm)
            if(vm.user==null) {
                Spacer(Modifier.height(30.dp));Text("Loading your workspace…")
                TextButton(onClick=vm::refresh,enabled=!vm.busy) {Text("Retry")}
                TextButton(onClick=vm::forgetSession,enabled=!vm.busy) {Text("Return to sign in")}
            } else when(vm.page) {
                "Board" -> BoardScreen(vm,onTask={detail=it},onAddBoard={editingBoard=null;boardDialog=true},onEditBoard={editingBoard=it;boardDialog=true})
                "Progress" -> ProgressScreen(vm.stats)
                "Reminders" -> ReminderScreen(vm) {detail=it}
                "Settings" -> SettingsScreen(vm)
            }
        }
    }
    if(boardDialog) BoardDialog(vm,editingBoard) {boardDialog=false}
    detail?.let { task -> TaskDetails(vm,vm.tasks.firstOrNull {it.id==task.id} ?: task,onClose={detail=null},onEdit={detail=null;editTask=it}) }
    editTask?.let { task -> TaskEditor(vm,task) {editTask=null} }
}
@Composable private fun BoardScreen(vm: TaskFlowViewModel, onTask:(Task)->Unit, onAddBoard:()->Unit, onEditBoard:(Board)->Unit) {
    var menu by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("ALL") }
    var due by rememberSaveable { mutableStateOf("ALL") }
    var dragged by remember { mutableStateOf<Task?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    val regions = remember { mutableStateMapOf<String,Rect>() }
    val statuses = listOf("TODO" to "To-do","DOING" to "Doing","DONE" to "Done")
    Column(Modifier.fillMaxSize()) {
        Text("YOUR WORKSPACE",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,letterSpacing=2.sp)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick={menu=true},enabled=!vm.busy) { Text(vm.selectedBoard?.name ?: "Choose a board",style=MaterialTheme.typography.headlineSmall);Icon(Icons.Outlined.ExpandMore,null) }
                DropdownMenu(menu,{menu=false}) {
                    vm.boards.forEach { board -> DropdownMenuItem(text={Text(board.name)},onClick={menu=false;vm.selectBoard(board)}) }
                    DropdownMenuItem(text={Text("+ Create board")},onClick={menu=false;onAddBoard()})
                }
            }
            vm.selectedBoard?.let { board -> IconButton(onClick={onEditBoard(board)},enabled=!vm.busy) {Icon(Icons.Outlined.MoreHoriz,"Manage board")} }
        }
        if(vm.boards.isEmpty()) {
            Spacer(Modifier.height(38.dp));Heading("A fresh start","Your first board\nstarts here.","Create a board for your studies, projects or everyday plans.")
            Spacer(Modifier.height(20.dp));Button(onClick=onAddBoard,enabled=!vm.busy) {Text("Create a board")};return
        }
        OutlinedTextField(search,{search=it},leadingIcon={Icon(Icons.Outlined.Search,null)},label={Text("Search tasks")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "All priorities","HIGH" to "High","MEDIUM" to "Medium","LOW" to "Low").forEach { (id,label) -> FilterChip(priority==id,{priority=id},label={Text(label)}) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "Any date","TODAY" to "Due today","OVERDUE" to "Overdue").forEach { (id,label) -> FilterChip(due==id,{due=id},label={Text(label)}) }
        }
        Text(if(dragged==null) "Long-press a card to move it. Swipe to see columns." else "Release over a column to move this task.",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(bottom=10.dp))
        val visible = filterTasks(vm.tasks,search,priority,due)
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(bottom=78.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            statuses.forEach { (status,label) ->
                val list = visible.filter {it.status==status}
                val targeted = dragged!=null && regions[status]?.contains(dragPoint)==true
                Surface(modifier=Modifier.width(265.dp).fillMaxHeight().onGloballyPositioned {regions[status]=it.boundsInRoot()},shape=RoundedCornerShape(18.dp),color=if(targeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)) {
                    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(label,Modifier.weight(1f),fontWeight=FontWeight.Bold);Text(list.size.toString(),color=MaterialTheme.colorScheme.primary)
                        }
                        if(list.isEmpty()) Text("No tasks here yet",Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
                        list.forEach { task ->
                            key(task.id) {
                                var bounds by remember {mutableStateOf(Rect.Zero)}
                                Card(onClick={if(!vm.busy)onTask(task)},modifier=Modifier.fillMaxWidth().onGloballyPositioned {bounds=it.boundsInRoot()}
                                    .pointerInput(task,vm.busy) {
                                        if(!vm.busy) detectDragGesturesAfterLongPress(
                                            onDragStart={dragged=task;dragPoint=bounds.topLeft+it},
                                            onDrag={change,amount -> change.consume();dragPoint+=amount},
                                            onDragCancel={dragged=null},
                                            onDragEnd={
                                                val destination = regions.entries.firstOrNull {it.value.contains(dragPoint)}?.key
                                                if(destination!=null && destination!=task.status) vm.saveTask(task.copy(status=destination)) {}
                                                dragged=null
                                            })
                                    },colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                        Text(task.priority,style=MaterialTheme.typography.labelSmall,color=if(task.priority=="HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,letterSpacing=1.sp)
                                        Text(task.title,fontWeight=FontWeight.SemiBold)
                                        if(task.description.isNotBlank()) Text(task.description,maxLines=2,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        if(task.dueDate!=null) Text("Due ${task.dueDate}",style=MaterialTheme.typography.labelSmall)
                                        if(task.checklist.isNotEmpty()) Text("${task.checklist.count {it.done}} / ${task.checklist.size} checklist items",style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun BoardDialog(vm: TaskFlowViewModel, board: Board?, close:()->Unit) {
    var name by remember {mutableStateOf(board?.name ?: "")}
    var deleting by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!vm.busy)close()},title={Text(if(deleting) "Delete this board?" else if(board==null) "Create a board" else "Manage board")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(deleting) Text("This permanently deletes all tasks in ${board?.name}. Earned rewards remain.") else OutlinedTextField(name,{name=it},label={Text("Board name")},singleLine=true)
        ErrorNotice(vm)
        if(board!=null && !deleting) TextButton(onClick={deleting=true},enabled=!vm.busy) {Text("Delete board",color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={if(deleting && board!=null) vm.deleteBoard(board,close) else vm.saveBoard(name,board,close)},enabled=!vm.busy) {Text(if(deleting) "Delete permanently" else "Save board")}},dismissButton={TextButton(onClick=close,enabled=!vm.busy) {Text("Cancel")}})
}
@Composable private fun Choice(label:String, options:List<String>, value:String, onChange:(String)->Unit) {
    Text(label,style=MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(value==option,{onChange(option)},label={Text(option.lowercase().replaceFirstChar {it.uppercase()})}) }
    }
}
@Composable private fun TaskEditor(vm:TaskFlowViewModel, task:Task, close:()->Unit) {
    var title by rememberSaveable(task.id) {mutableStateOf(task.title)}
    var description by rememberSaveable(task.id) {mutableStateOf(task.description)}
    var priority by rememberSaveable(task.id) {mutableStateOf(task.priority)}
    var status by rememberSaveable(task.id) {mutableStateOf(task.status)}
    var date by rememberSaveable(task.id) {mutableStateOf(task.dueDate ?: "")}
    var checklist by remember {mutableStateOf(task.checklist)}
    var itemText by rememberSaveable {mutableStateOf("")}
    Dialog(onDismissRequest={if(!vm.busy)close()}) {
        Surface(shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max=650.dp).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(task.id.isEmpty()) "A new step forward" else "Edit your task",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                ErrorNotice(vm)
                OutlinedTextField(title,{title=it},label={Text("Task title")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(description,{description=it},label={Text("Description")},minLines=2,modifier=Modifier.fillMaxWidth())
                Choice("Priority",listOf("LOW","MEDIUM","HIGH"),priority) {priority=it}
                Choice("Status",listOf("TODO","DOING","DONE"),status) {status=it}
                OutlinedTextField(date,{date=it},label={Text("Due date (optional)")},placeholder={Text("YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Text("Checklist",fontWeight=FontWeight.Bold)
                checklist.forEachIndexed { index,item -> Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(item.done,{done->checklist=checklist.mapIndexed {i,c -> if(i==index)c.copy(done=done) else c}})
                    Text(item.text,Modifier.weight(1f))
                    IconButton(onClick={checklist=checklist.filterIndexed {i,_ -> i!=index}}) {Icon(Icons.Outlined.Close,"Remove checklist item")}
                } }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(itemText,{itemText=it},label={Text("New checklist item")},modifier=Modifier.weight(1f))
                    IconButton(onClick={
                        if(itemText.isBlank() || itemText.trim().length>160 || checklist.size>=30) vm.report("Use 1–160 characters, up to 30 items.")
                        else {checklist=checklist+ChecklistItem(itemText.trim());itemText=""}
                    }) {Icon(Icons.Outlined.Add,"Add checklist item")}
                }
                Text("Tap + to add the checklist item before saving.",style=MaterialTheme.typography.bodySmall)
                Button(onClick={vm.saveTask(task.copy(title=title,description=description,priority=priority,status=status,dueDate=date.trim().ifBlank {null},checklist=checklist),close)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text(if(vm.busy) "Saving…" else "Save task")}
                TextButton(onClick=close,enabled=!vm.busy,modifier=Modifier.align(Alignment.End)) {Text("Cancel")}
            }
        }
    }
}
@Composable private fun TaskDetails(vm:TaskFlowViewModel,task:Task,onClose:()->Unit,onEdit:(Task)->Unit) {
    var deleting by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!vm.busy)onClose()},title={Text(if(deleting) "Delete task?" else task.title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        ErrorNotice(vm)
        if(deleting) Text("This permanently removes the task. Earned rewards remain.") else {
            Text("${task.priority} priority • ${task.status}",color=MaterialTheme.colorScheme.primary)
            Text(task.description.ifBlank {"No description"})
            task.dueDate?.let {Text("Due $it")}
            task.checklist.forEachIndexed { index,item -> Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(item.done,{done -> vm.saveTask(task.copy(checklist=task.checklist.mapIndexed {i,c -> if(i==index)c.copy(done=done) else c})) {}},enabled=!vm.busy)
                Text(item.text)
            } }
            Text("First completion earns 10 points.",style=MaterialTheme.typography.bodySmall)
            Choice("Move to",listOf("TODO","DOING","DONE"),task.status) {if(!vm.busy)vm.saveTask(task.copy(status=it)) {}}
            TextButton(onClick={deleting=true},enabled=!vm.busy) {Text("Delete task",color=MaterialTheme.colorScheme.error)}
        }
    }},confirmButton={TextButton(onClick={if(deleting)vm.deleteTask(task,onClose) else onEdit(task)},enabled=!vm.busy) {Text(if(deleting) "Delete permanently" else "Edit task")}},dismissButton={TextButton(onClick=onClose,enabled=!vm.busy) {Text("Close")}})
}
@Composable private fun ProgressScreen(stats:Stats) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Heading("Every step counts","Look how far\nyou’ve come.","Small wins add up. Keep your momentum going.")
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {Text("TOTAL POINTS",letterSpacing=2.sp);Text(stats.points.toString(),fontSize=62.sp,fontWeight=FontWeight.Bold);Text("10 points for each first-time task completion")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f)) {Column(Modifier.padding(20.dp)) {Text("${stats.completed}",style=MaterialTheme.typography.headlineLarge);Text("tasks completed")}}
            Card(Modifier.weight(1f)) {Column(Modifier.padding(20.dp)) {Text("${stats.streak}",style=MaterialTheme.typography.headlineLarge);Text("day streak")}}
        }
        Text("Your milestones",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        listOf("First Task","10 Tasks Done","7-Day Streak").forEach {badge ->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(if(badge in stats.badges) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock,null)
                Text(badge,Modifier.weight(1f));Text(if(badge in stats.badges) "Unlocked" else "Keep going",style=MaterialTheme.typography.labelSmall)
            }
        }
        Text("Streaks use consecutive UTC days with a first completion. Reopening or deleting a task does not remove earned points.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun ReminderScreen(vm:TaskFlowViewModel,onTask:(Task)->Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Heading("Stay on track","Coming up.","Due-date reminders for ${vm.selectedBoard?.name ?: "your selected board"}.")
        if(vm.user?.reminders==false) Text("In-app reminders are turned off. Enable them in Settings.") else {
            val reminders=vm.tasks.filter {it.dueDate!=null && it.status!="DONE"}.sortedBy {it.dueDate}
            if(reminders.isEmpty()) Text("You’re all caught up. Tasks with a due date will appear here.")
            reminders.forEach {task -> Card(onClick={onTask(task)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(task.title,fontWeight=FontWeight.Bold)
                    Text(if(task.dueDate!!<LocalDate.now().toString()) "Overdue · ${task.dueDate}" else "Due ${task.dueDate}")
                }
            } }
        }
        Text("These reminders appear inside the app. Push notifications are planned for the final PoE.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SettingsScreen(vm:TaskFlowViewModel) {
    val user=vm.user ?: return
    var name by remember(user.name) {mutableStateOf(user.name)}
    var theme by remember(user.theme) {mutableStateOf(user.theme)}
    var reminders by remember(user.reminders) {mutableStateOf(user.reminders)}
    var priority by remember(user.defaultPriority) {mutableStateOf(user.defaultPriority)}
    var saved by remember {mutableStateOf(false)}
    val changed=name!=user.name || theme!=user.theme || reminders!=user.reminders || priority!=user.defaultPriority
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Heading("Make it yours","Your preferences.")
        Text(user.email,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name,{name=it;saved=false},label={Text("Your name")},modifier=Modifier.fillMaxWidth())
        Choice("Appearance",listOf("system","light","dark"),theme) {theme=it;saved=false}
        Row(verticalAlignment=Alignment.CenterVertically) {Text("In-app due-date reminders",Modifier.weight(1f));Switch(reminders,{reminders=it;saved=false})}
        Choice("Default priority for new tasks",listOf("LOW","MEDIUM","HIGH"),priority) {priority=it;saved=false}
        Button(onClick={saved=true;vm.saveSettings(name,theme,reminders,priority)},enabled=!vm.busy && changed,modifier=Modifier.fillMaxWidth()) {Text("Save preferences")}
        if(saved && !vm.busy && !changed && vm.error==null) Text("Preferences saved.",color=MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("TaskFlow · Part 2",fontWeight=FontWeight.Bold)
        Text("Language: English. Additional languages, Google sign-in, offline sync and push notifications are reserved for the final PoE.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=vm::signOut,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Sign out")}
    }
}
