package com.time.tracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.time.tracker.ui.theme.TrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import kotlin.collections.filter

// Helper for consistent chart colors
object CategoryColors {
    private val palette = listOf(0xFF6200EE, 0xFF03DAC5, 0xFF018786, 0xFFB00020, 0xFFFFAB00)
    private val colorMap = Collections.synchronizedMap(mutableMapOf<String, Color>())

    fun getColor(category: String): Color {
        return colorMap.getOrPut(category) {
            Color(palette[colorMap.size % palette.size])
        }
    }
}

class MainActivity : ComponentActivity() {
    private val dbHelper by lazy { DatabaseHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TrackerTheme { MainNavigation(dbHelper) } }
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
                NavigationDrawerItem(label = { Text("Record Time") }, selected = false, onClick = {
                    navController.navigate("record") { launchSingleTop = true }
                    scope.launch { drawerState.close() }
                })
                NavigationDrawerItem(label = { Text("Manage Categories") }, selected = false, onClick = {
                    navController.navigate("categories") { launchSingleTop = true }
                    scope.launch { drawerState.close() }
                })
                NavigationDrawerItem(label = { Text("Metrics") }, selected = false, onClick = {
                    navController.navigate("metrics") { launchSingleTop = true }
                    scope.launch { drawerState.close() }
                })
                NavigationDrawerItem(label = { Text("Import / Export") }, selected = false, onClick = {
                    navController.navigate("io") { launchSingleTop = true }
                    scope.launch { drawerState.close() }
                })
                NavigationDrawerItem(label = { Text("Simulate Data") }, selected = false, onClick = {
                    navController.navigate("simulate") { launchSingleTop = true }
                    scope.launch { drawerState.close() }
                })
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
                composable("simulate") { SimulationScreen(dbHelper) }
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
            Text("No categories found. Add some in 'Manage Categories'!")
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
        TextField(value = mainInput, onValueChange = { mainInput = it }, label = { Text("Main") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TextField(value = subInput, onValueChange = { subInput = it }, label = { Text("Sub") }, modifier = Modifier.fillMaxWidth())
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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("Data Management", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { exportDbLauncher.launch("tracker_backup.db") }, modifier = Modifier.fillMaxWidth()) { Text("Export SQLite (.db)") }
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            Button(onClick = { exportCsvLauncher.launch("time_logs.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export to CSV") }
        }
    }
}
@Composable
fun MetricsScreen(dbHelper: DatabaseHelper) {
    // 2. Immutable State Object to prevent concurrent modification crashes
    data class MetricsState(
        val allData: List<DatabaseHelper.SessionData>,
        val grouped: Map<String, List<DatabaseHelper.SessionData>>,
        val heatmap: Map<String, Set<Long>>,
        val colors: Map<String, Color>,
        val dailyTotals: Map<String, Long>
    )

    var state by remember { mutableStateOf<MetricsState?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlin.runCatching {
            val list = dbHelper.getRawSessions().toList()
            val dayInMs = 24 * 60 * 60 * 1000L
            val now = System.currentTimeMillis()

            val grouped = list.groupBy { it.main }
            val heat = grouped.mapValues { (_, s) -> s.map { it.timestamp / dayInMs }.toSet() }
            val colors = grouped.keys.associateWith { CategoryColors.getColor(it) }
            val daily = dbHelper.getTimeByCategory(now - dayInMs)

            MetricsState(list, grouped, heat, colors, daily)
        }.onSuccess {
            state = it
            isLoading = false
        }.onFailure { isLoading = false }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        val s = state ?: return
        val weekStart = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item { Text("Daily Proportions", style = MaterialTheme.typography.headlineSmall) }
            item { SimplePieChart(s.dailyTotals, s.colors) }

            item { Spacer(Modifier.height(32.dp)) }

            item { Text("Weekly Activity (Hrs/Day)", style = MaterialTheme.typography.headlineSmall) }
            item { SimpleLineChart(s.allData.filter { it.timestamp >= weekStart }, s.colors) }

            item { Spacer(Modifier.height(32.dp)) }

            item { Text("Yearly Consistency", style = MaterialTheme.typography.headlineSmall) }
            item { YearlyHeatmap(s.heatmap, s.colors) }

            item { Spacer(Modifier.height(32.dp)) }

            item { Text("All-Time Cumulative Trends", style = MaterialTheme.typography.headlineSmall) }
            s.grouped.forEach { (main, sessions) ->
                item(key = main) {
                    Text(main, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    CumulativeSubCategoryChart(sessions)
                }
            }
        }
    }
}

@Composable
fun SimplePieChart(data: Map<String, Long>, colors: Map<String, Color>) {
    val total = data.values.sum().toFloat()
    if (total <= 0f) { Text("No data for this period", modifier = Modifier.padding(16.dp)); return }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(200.dp)) {
        Canvas(modifier = Modifier.size(150.dp)) {
            var startAngle = -90f
            data.forEach { (category, value) ->
                val sweepAngle = (value / total) * 360f
                drawArc(colors[category] ?: Color.Gray, startAngle, sweepAngle, true)
                startAngle += sweepAngle
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            data.keys.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors[category] ?: Color.Gray))
                    Text(" $category", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SimpleLineChart(sessions: List<DatabaseHelper.SessionData>, colors: Map<String, Color>) {
    val dMs = 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val last7Days = (0..6).map { (now / dMs) - it }.reversed()

    val traces = sessions.groupBy { it.main }.mapValues { (_, s) ->
        s.groupBy { it.timestamp / dMs }.mapValues { it.value.sumOf { it.duration } / 3600f }
    }

    val maxVal = traces.values.flatMap { it.values }.maxOrNull() ?: 0f
    val maxH = if (maxVal < 1f) 5f else maxVal * 1.2f

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.height(200.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxHeight().width(40.dp), Arrangement.SpaceBetween, Alignment.End) {
                Text(String.format(Locale.US, "%.1f h", maxH), style = MaterialTheme.typography.labelSmall)
                Text("0h", style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / 6f

                    traces.forEach { (cat, dayMap) ->
                        val path = Path()
                        val color = colors[cat] ?: Color.Gray
                        last7Days.forEachIndexed { i, day ->
                            val hours = dayMap[day] ?: 0f
                            val x = i * stepX
                            val y = h - (hours / maxH * h)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(color, 6f, Offset(x, y))
                        }
                        drawPath(path, color, style = Stroke(width = 5f))
                    }
                }
            }
        }
    }
}

@Composable
fun YearlyHeatmap(categoryActivity: Map<String, Set<Long>>, colors: Map<String, Color>) {
    val dayInMs = 24 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val dayGrid = (0 until 140).map { i -> (now / dayInMs) - i }.reversed()

    Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        categoryActivity.forEach { (category, activeDays) ->
            Text(category, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(vertical = 2.dp)) {
                dayGrid.forEach { dayTimestamp ->
                    Box(
                        Modifier.size(12.dp).padding(1.dp).background(
                            if (activeDays.contains(dayTimestamp)) colors[category] ?: Color.Gray
                            else Color.Gray.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(2.dp)
                        )
                    )
                }
            }
        }
    }
}
@Composable
fun CumulativeSubCategoryChart(sessions: List<DatabaseHelper.SessionData>) {
    val subTotals = sessions.groupBy { it.subs }.mapValues { it.value.sumOf { s -> s.duration } / 3600f }.toList().sortedByDescending { it.second }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        subTotals.forEach { (sub, hours) ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(sub, style = MaterialTheme.typography.bodySmall)
                    Text("${String.format("%.1f", hours)}h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = {
                        val totalMain = subTotals.sumOf { it.second.toDouble() }.toFloat()
                        if (totalMain > 0) hours / totalMain else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun SimulationScreen(dbHelper: DatabaseHelper) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val categories = remember { dbHelper.getCategories() }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Simulation Tool", style = MaterialTheme.typography.headlineMedium)
            Text("Generates 60 sessions over 30 days.", modifier = Modifier.padding(vertical = 16.dp), textAlign = TextAlign.Center)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = categories.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val dayInMs = 24 * 60 * 60 * 1000L
                        val now = System.currentTimeMillis()
                        repeat(60) {
                            val timestamp = now - ((0..30).random() * dayInMs) - (0..dayInMs).random()
                            val mainCat = categories.keys.random()
                            val subCat = categories[mainCat]?.random() ?: "General"
                            dbHelper.insertSession(mainCat, subCat, timestamp, (900..10800).random().toLong())
                        }
                        snackbarHostState.showSnackbar("Simulated 60 sessions!")
                    }
                }
            ) { Text("Generate 30 Days of Activity") }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = {
                dbHelper.writableDatabase.execSQL("DELETE FROM sessions")
                scope.launch { snackbarHostState.showSnackbar("All sessions cleared.") }
            }) {
                Text("Clear All Session Data", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}