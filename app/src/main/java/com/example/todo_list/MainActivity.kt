package com.example.todo_list

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.*
import android.net.Uri
import org.json.JSONArray
import org.json.JSONException

data class TodoItem(val text: String, var completed: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TodoApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp() {
    val context = LocalContext.current
    val todoList = remember { mutableStateListOf<TodoItem>() }
    val newTodo = remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var selectedFileUri: Uri? by remember { mutableStateOf<Uri?>(null) }

    fun saveTodoListToJson(todoList: List<TodoItem>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.write("[\n") // Start the JSON array with a newline
            todoList.forEachIndexed { index, todoItem ->
                writer.write("\t{\n") // Start a new object with a tab for indentation
                writer.write("\t\t\"text\": \"${todoItem.text}\",\n")
                writer.write("\t\t\"completed\": ${todoItem.completed}\n")
                writer.write("\t}") // Close the object
                if (index < todoList.size - 1) {
                    writer.write(",\n") // Add a comma after each object except the last one
                }
            }
            writer.write("\n]") // End the JSON array with a newline
        }
    }

    fun loadTodoListFromJson(inputStream: InputStream, todoList: MutableList<TodoItem>) {
        todoList.clear()
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonStringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                jsonStringBuilder.append(line)
            }
            val jsonArray = JSONArray(jsonStringBuilder.toString())

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val text = jsonObject.getString("text")
                val completed = jsonObject.getBoolean("completed")
                todoList.add(TodoItem(text, completed))
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    val loadFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFileUri = uri // Update the selected file URI
                try {
                    context.contentResolver.openInputStream(uri)?.let { inputStream ->
                        loadTodoListFromJson(inputStream, todoList)
                    }
                } catch (e: IOException) {
                    showError = true
                    errorText = "Error loading from file."
                }
            }
        }
    }

    // Launcher for file picker to save data
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json") // MIME type for JSON
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.let { outputStream ->
                    saveTodoListToJson(todoList, outputStream)
                }
            } catch (e: IOException) {
                showError = true
                errorText = "Error saving to file."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Todo List") },
                actions = {
                    // Save button
                    Button(
                        onClick = {
                            saveFileLauncher.launch("todos.json")
                        }
                    ) {
                        Text("Save")
                    }
                    // Load button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json" // Filter only JSON files
                            }
                            loadFileLauncher.launch(intent)
                        }
                    ) {
                        Text("Load")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val trimmedTodo = newTodo.value.trim()
                if (trimmedTodo.isNotBlank() && trimmedTodo.length <= 32) {
                    if (todoList.none { it.text.equals(trimmedTodo, ignoreCase = true) }) {
                        todoList.add(TodoItem(text = trimmedTodo, completed = false))
                        newTodo.value = ""
                    } else {
                        showError = true
                        errorText = "Task already exists!"
                    }
                } else if (trimmedTodo.length > 32) {
                    showError = true
                    errorText = "Task must be 32 characters or less"
                }
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Todo")
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = remember { SnackbarHostState() }) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            TextField(
                value = newTodo.value,
                onValueChange = { newValue ->
                    newTodo.value = newValue.take(32)
                },
                label = { Text("New Todo") },
                modifier = Modifier.fillMaxWidth(),
                isError = showError
            )
            if (showError) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(todoList) { todoItem ->
                    TodoItemRow(
                        todoItem = todoItem,
                        onDelete = {
                            todoList.remove(todoItem)
                        },
                        onCompletionChange = { isChecked ->
                            val index = todoList.indexOf(todoItem)
                            if (index != -1) {
                                todoList[index] = todoList[index].copy(completed = isChecked)
                            }
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun TodoItemRow(todoItem: TodoItem, onDelete: () -> Unit, onCompletionChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Checkbox(
            checked = todoItem.completed,
            onCheckedChange = onCompletionChange,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = todoItem.text,
            style = if (todoItem.completed) MaterialTheme.typography.bodyLarge.copy(
                textDecoration = TextDecoration.LineThrough
            ) else MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TodoApp()
}