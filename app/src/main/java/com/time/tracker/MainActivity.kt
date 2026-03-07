package com.time.tracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.time.tracker.ui.theme.TrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val dbHelper by lazy { DatabaseHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackerTheme {
                MainNavigation(dbHelper)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(dbHelper: DatabaseHelper) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("Time Tracker", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                NavigationDrawerItem(
                    label = { Text("Record Time") },
                    selected = false,
                    onClick = {
                        navController.navigate("record") { launchSingleTop = true }
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Manage Categories") },
                    selected = false,
                    onClick = {
                        navController.navigate("categories") { launchSingleTop = true }
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Import / Export") },
                    selected = false,
                    onClick = {
                        navController.navigate("io") { launchSingleTop = true }
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Metrics") },
                    selected = false,
                    onClick = {
                        navController.navigate("metrics") { launchSingleTop = true }
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Tracker") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(navController = navController, startDestination = "record", modifier = Modifier.padding(innerPadding)) {
                composable("record") { MainScreen(dbHelper) }
                composable("categories") { CategoryManagementScreen(dbHelper) }
                composable("io") { IOScreen(dbHelper, LocalContext.current) }
                composable("metrics") { MetricsScreen(dbHelper) }
            }
        }
    }
}

@Composable
fun MainScreen(dbHelper: DatabaseHelper) {
    var seconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    val categoryMap = remember { dbHelper.getCategories() }
    var selectedMain by remember { mutableStateOf(categoryMap.keys.firstOrNull() ?: "") }
    val selectedSubs = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            seconds++
        }
    }

    LaunchedEffect(selectedMain) { selectedSubs.clear() }

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Time Spent: $seconds seconds", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { isRunning = !isRunning }) { Text(if (isRunning) "Pause" else "Start") }
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        if (categoryMap.isEmpty()) {
            Text("No categories found. Go to 'Manage Categories' to add some!")
        } else {
            CategorySelector(selectedMain, { selectedMain = it }, selectedSubs, categoryMap)
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            enabled = seconds > 0 && !isRunning,
            onClick = {
                val finalSubs = selectedSubs.filter { it.value }.keys.toList()
                val subsString = if (finalSubs.isEmpty()) "unspecified" else finalSubs.joinToString(", ")
                dbHelper.insertSession(selectedMain, subsString, System.currentTimeMillis(), seconds.toLong())
                seconds = 0
                selectedSubs.clear()
            }
        ) { Text("Save Session") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(selectedMain: String, onMainChange: (String) -> Unit, selectedSubs: SnapshotStateMap<String, Boolean>, categoryMap: Map<String, List<String>>) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Main Category", style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            TextField(value = selectedMain, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categoryMap.keys.forEach { category ->
                    DropdownMenuItem(text = { Text(category) }, onClick = { onMainChange(category); expanded = false })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Subcategories", style = MaterialTheme.typography.labelLarge)
        categoryMap[selectedMain]?.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selectedSubs[sub] ?: false, onCheckedChange = { selectedSubs[sub] = it })
                Text(sub)
            }
        }
    }
}

@Composable
fun CategoryManagementScreen(dbHelper: DatabaseHelper) {
    var mainInput by remember { mutableStateOf("") }
    var subInput by remember { mutableStateOf("") }
    var refreshCounter by remember { mutableIntStateOf(0) }
    val categories = remember(refreshCounter) { dbHelper.getCategories() }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Add New Category", style = MaterialTheme.typography.titleMedium)
        TextField(value = mainInput, onValueChange = { mainInput = it }, label = { Text("Main (e.g. Cyber)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TextField(value = subInput, onValueChange = { subInput = it }, label = { Text("Sub (e.g. DNEA)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (mainInput.isNotBlank() && subInput.isNotBlank()) { dbHelper.insertCategory(mainInput, subInput); mainInput = ""; subInput = ""; refreshCounter++ } }, modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()) { Text("Add Category") }
        HorizontalDivider()
        Text("Existing Categories", modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            categories.forEach { (main, subs) ->
                item { Text(text = main, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                items(subs) { sub ->
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(sub)
                        IconButton(onClick = { dbHelper.deleteCategory(main, sub); refreshCounter++ }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
fun IOScreen(dbHelper: DatabaseHelper, context: Context) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportDbLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-sqlite3")) { uri ->
        uri?.let {
            val dbFile = dbHelper.getDatabasePath(context)
            context.contentResolver.openOutputStream(it)?.use { output -> dbFile.inputStream().copyTo(output) }
            scope.launch { snackbarHostState.showSnackbar("Database Exported!") }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { output ->
                val cursor = dbHelper.getAllSessionsCursor()
                val writer = output.bufferedWriter()
                writer.write("Date,Amount of time (ms),Category,Subcategories\n")
                if (cursor.moveToFirst()) {
                    do {
                        writer.write("${cursor.getLong(0)},${cursor.getLong(1) * 1000},${cursor.getString(2)},\"${cursor.getString(3)}\"\n")
                    } while (cursor.moveToNext())
                }
                cursor.close(); writer.flush()
            }
            scope.launch { snackbarHostState.showSnackbar("CSV Exported!") }
        }
    }

    val importDbLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            dbHelper.close()
            val dbFile = dbHelper.getDatabasePath(context)
            context.contentResolver.openInputStream(it)?.use { input -> dbFile.outputStream().use { output -> input.copyTo(output) } }
            scope.launch { snackbarHostState.showSnackbar("Database Imported! Restart App.") }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("Data Management", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { exportDbLauncher.launch("tracker_backup.db") }, modifier = Modifier.fillMaxWidth()) { Text("Export SQLite (.db)") }
            OutlinedButton(onClick = { importDbLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3")) }, modifier = Modifier.fillMaxWidth()) { Text("Import SQLite (.db)") }
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            Button(onClick = { exportCsvLauncher.launch("time_logs.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export to CSV") }
        }
    }
}

@Composable
fun MetricsScreen(dbHelper: DatabaseHelper) {
    val now = System.currentTimeMillis()
    val dayStart = now - (24 * 60 * 60 * 1000)
    val weekStart = now - (7 * 24 * 60 * 60 * 1000)

    val dayData = remember { dbHelper.getTimeByCategory(dayStart) }
    val weekData = remember { dbHelper.getTimeByCategory(weekStart) }
    val allTimeData = remember { dbHelper.getRawSessions() }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Daily Proportions", style = MaterialTheme.typography.headlineSmall) }
        item { SimplePieChart(dayData) }
        item { Spacer(Modifier.height(32.dp)) }
        item { Text("Weekly Proportions", style = MaterialTheme.typography.headlineSmall) }
        item { SimplePieChart(weekData) }
        item { Spacer(Modifier.height(32.dp)) }
        item { Text("Weekly Activity (Last 7 Days)", style = MaterialTheme.typography.headlineSmall) }
        item { SimpleLineChart(allTimeData.filter { it.timestamp >= weekStart }) }
        item { Spacer(Modifier.height(32.dp)) }
        item { Text("All-Time Cumulative Trends", style = MaterialTheme.typography.headlineSmall) }
        allTimeData.groupBy { it.main }.forEach { (main, sessions) ->
            item {
                Text(main, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                CumulativeSubCategoryChart(sessions)
            }
        }
    }
}

@Composable
fun SimplePieChart(data: Map<String, Long>) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error)
    val total = data.values.sum().toFloat()
    if (total == 0f) { Text("No data for this period", modifier = Modifier.padding(16.dp)); return }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(200.dp)) {
        Canvas(modifier = Modifier.size(150.dp)) {
            var startAngle = 0f
            data.values.forEachIndexed { index, value ->
                val sweepAngle = (value / total) * 360f
                drawArc(colors[index % colors.size], startAngle, sweepAngle, true)
                startAngle += sweepAngle
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            data.keys.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors[index % colors.size]))
                    Text(" $label", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SimpleLineChart(sessions: List<DatabaseHelper.SessionData>) {
    Box(Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text("Linear Trend: ${sessions.size} sessions recorded")
    }
}

@Composable
fun CumulativeSubCategoryChart(sessions: List<DatabaseHelper.SessionData>) {
    Box(Modifier.fillMaxWidth().height(100.dp).padding(vertical = 4.dp).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
        Text("Cumulative Data: ${sessions.sumOf { it.duration }}s total", modifier = Modifier.align(Alignment.Center))
    }
}