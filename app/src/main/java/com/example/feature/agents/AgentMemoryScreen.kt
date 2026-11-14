package com.example.feature.agents

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ai.TaskCategory
import com.example.core.database.AgentExecutionEntity
import com.example.core.database.SavedMemoryEntity
import com.example.core.ui.*
import com.example.feature.main.MainViewModel
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.VioletPulse
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AgentAndMemoryScreen(
    viewModel: MainViewModel,
    initialTab: Int = 0, // 0 = Agents, 1 = Memory, 2 = Tasks & Reminders
    onBack: (() -> Unit)? = null
) {
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

    Column(modifier = Modifier.fillMaxSize()) {
        WafaScreenHeader(
            title = "Agents, Memory & Tasks",
            subtitle = "Orchestrated Specialists • User-Controlled Memory",
            onBack = onBack
        )

        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Agents (10)") })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Memory") })
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Tasks") })
        }

        when (activeTab) {
            0 -> AgentsTab(viewModel)
            1 -> MemoryTab(viewModel)
            else -> TasksTab(viewModel)
        }
    }
}

@Composable
private fun AgentsTab(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val executions by viewModel.agentExecutions.collectAsStateWithLifecycle()

    val specialists = listOf(
        "Coding Agent" to TaskCategory.CODING,
        "Research Agent" to TaskCategory.RESEARCH,
        "Writing Agent" to TaskCategory.WRITING,
        "Document Agent" to TaskCategory.DOCUMENT_ANALYSIS,
        "Spreadsheet Agent" to TaskCategory.SPREADSHEET,
        "Image Agent" to TaskCategory.IMAGE_GEN,
        "Audio Agent" to TaskCategory.AUDIO_SPEECH,
        "Data Agent" to TaskCategory.SPREADSHEET,
        "Planning Agent" to TaskCategory.AGENT_PLANNING,
        "File Agent" to TaskCategory.DOCUMENT_ANALYSIS
    )

    var selectedSpecialist by remember { mutableStateOf(specialists.first().first) }
    var userGoal by remember { mutableStateOf("") }
    var pendingExecution by remember { mutableStateOf<AgentExecutionEntity?>(null) }
    var liveOutput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Select Specialist Agent", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                specialists.forEach { (name, _) ->
                    FilterChip(
                        selected = selectedSpecialist == name,
                        onClick = { selectedSpecialist = name },
                        label = { Text(name) }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = userGoal,
                onValueChange = { userGoal = it },
                label = { Text("Describe multi-step goal for $selectedSpecialist") },
                modifier = Modifier.fillMaxWidth().testTag("agent_goal_input"),
                maxLines = 4
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = userGoal.isNotBlank() && !isRunning,
                onClick = {
                    val steps = listOf(
                        "1. Understand request & classify domain ($selectedSpecialist)",
                        "2. Select optimal AI model via capability router",
                        "3. Synthesize structured execution plan & tool inputs",
                        "4. Validate output quality & save approved execution log"
                    )
                    val draft = AgentExecutionEntity(
                        specialistName = selectedSpecialist,
                        userGoal = userGoal.trim(),
                        planStepsJson = JSONArray(steps).toString(),
                        status = "AWAITING_APPROVAL",
                        approvedByUser = false
                    )
                    pendingExecution = draft
                },
                modifier = Modifier.fillMaxWidth().testTag("agent_prepare_plan_button")
            ) { Text("Prepare Execution Plan (Requires Approval)") }
        }

        if (pendingExecution != null) {
            val plan = pendingExecution!!
            item {
                WafaGlassCard(accentColor = ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                    WafaHudBadge("APPROVAL REQUIRED", ElectricCyan)
                    Spacer(Modifier.height(6.dp))
                    Text("${plan.specialistName}: ${plan.userGoal}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    val arr = JSONArray(plan.planStepsJson)
                    for (i in 0 until arr.length()) {
                        Text(arr.getString(i), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val category = specialists.firstOrNull { it.first == plan.specialistName }?.second ?: TaskCategory.AGENT_PLANNING
                                isRunning = true
                                liveOutput = ""
                                pendingExecution = null
                                viewModel.runSingleShotAiTask(
                                    taskCategory = category,
                                    systemInstruction = "You are ${plan.specialistName} inside WAFA AI. Execute the user-approved plan step-by-step, validate your output, and never claim external side effects occurred.",
                                    userPrompt = plan.userGoal,
                                    onDelta = { liveOutput = it },
                                    onDone = { final, err ->
                                        isRunning = false
                                        scope.launch {
                                            viewModel.repository.db.agentExecutionDao().upsert(
                                                plan.copy(
                                                    status = if (err == null) "COMPLETED" else "FAILED",
                                                    resultSummary = (err ?: final).take(2000),
                                                    approvedByUser = true
                                                )
                                            )
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("agent_approve_button")
                        ) { Text("Approve & Execute") }

                        OutlinedButton(
                            onClick = { pendingExecution = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                    }
                }
            }
        }

        if (liveOutput.isNotBlank() || isRunning) {
            item {
                WafaGlassCard(accentColor = EmeraldSignal, modifier = Modifier.fillMaxWidth()) {
                    if (isRunning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    WafaRichTextContent(text = liveOutput.ifBlank { "Executing approved agent plan..." })
                }
            }
        }

        if (executions.isNotEmpty()) {
            item { Text("Execution Audit Log (${executions.size})", style = MaterialTheme.typography.titleMedium) }
            items(executions.take(10), key = { it.id }) { ex ->
                WafaGlassCard(accentColor = VioletPulse, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(ex.specialistName, style = MaterialTheme.typography.titleSmall)
                        WafaHudBadge(ex.status, if (ex.status == "COMPLETED") EmeraldSignal else ElectricCyan)
                    }
                    Text(ex.userGoal, style = MaterialTheme.typography.bodySmall)
                    if (ex.resultSummary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(ex.resultSummary.take(220), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Preference") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }

    val categories = listOf("Preference", "Project", "Note", "Context")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WafaGlassCard(accentColor = ElectricBlue, modifier = Modifier.fillMaxWidth()) {
                Text("Add User-Controlled Memory (Never auto-saves secrets)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Memory Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text("Pin to AI Chat System Context", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveMemory(category, title, content, pinned = pinned)
                            title = ""
                            content = ""
                            pinned = false
                        },
                        modifier = Modifier.weight(1f).testTag("save_memory_button")
                    ) { Text("Save Memory") }
                    OutlinedButton(onClick = {
                        val arr = JSONArray()
                        memories.forEach { m ->
                            arr.put(JSONObject().put("category", m.category).put("title", m.title).put("content", m.content))
                        }
                        val info = viewModel.repository.docEngine.writePlainFile(arr.toString(2), "wafa_memory_backup", "json")
                        shareGeneratedFile(context, info.file, "application/json")
                    }) { Text("Backup JSON") }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search personal memories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val filtered = memories.filter {
            searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.content.contains(searchQuery, true)
        }
        items(filtered, key = { it.id }) { mem: SavedMemoryEntity ->
            WafaGlassCard(accentColor = if (mem.isPinned) ElectricCyan else VioletPulse, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WafaHudBadge(mem.category, ElectricCyan)
                            if (mem.isPinned) WafaHudBadge("PINNED", EmeraldSignal)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(mem.title, style = MaterialTheme.typography.titleSmall)
                        Text(mem.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        viewModel.saveMemory(mem.category, mem.title, mem.content, mem.tagsCsv, !mem.isPinned, mem.id)
                    }) {
                        Icon(Icons.Default.PushPin, contentDescription = "Toggle Pin")
                    }
                    IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Memory")
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksTab(viewModel: MainViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskPriority by remember { mutableStateOf("MEDIUM") }
    var newReminderTitle by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("Add Task") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    viewModel.addTask(newTaskTitle, priority = newTaskPriority)
                    newTaskTitle = ""
                }) { Text("Add") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                    FilterChip(selected = newTaskPriority == p, onClick = { newTaskPriority = p }, label = { Text(p) })
                }
            }
        }

        items(tasks, key = { it.id }) { task ->
            WafaGlassCard(accentColor = if (task.isCompleted) EmeraldSignal else ElectricCyan, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = { viewModel.toggleTask(task) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.titleSmall)
                        Text("Priority: ${task.priority}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Task")
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newReminderTitle,
                    onValueChange = { newReminderTitle = it },
                    label = { Text("Add Reminder (+1h)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    viewModel.addReminder(newReminderTitle, System.currentTimeMillis() + 3_600_000L)
                    newReminderTitle = ""
                }) { Text("Set") }
            }
        }

        items(reminders, key = { it.id }) { r ->
            WafaGlassCard(accentColor = VioletPulse, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = VioletPulse)
                    Spacer(Modifier.width(8.dp))
                    Text(r.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteReminder(r.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Reminder")
                    }
                }
            }
        }
    }
}
