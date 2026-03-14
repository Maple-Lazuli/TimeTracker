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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.time.tracker.ui.theme.TrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.collections.filter

object CategoryColors {
    private val colorMap = Collections.synchronizedMap(mutableMapOf<String, Color>())

    fun getColor(category: String): Color {
        return colorMap.getOrPut(category) {
            val hue = (colorMap.size * 137.5f) % 360f
            Color.hsl(
                hue = hue,
                saturation = 0.65f,
                lightness = 0.55f
            )
        }
    }
    fun reset() {
        colorMap.clear()
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
                NavigationDrawerItem(
                    label = { Text("Data Management") },
                    selected = false,
                    onClick = {
                        navController.navigate("management") { launchSingleTop = true }
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
                composable("management") { DataManagementScreen(dbHelper) }
            }
        }
    }
}

@Composable
fun MainScreen(dbHelper: DatabaseHelper) {

    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }

    val categoryMap = remember { dbHelper.getCategories() }
    var selectedMain by remember { mutableStateOf(categoryMap.keys.firstOrNull() ?: "") }
    val selectedSubs = remember { mutableStateMapOf<String, Boolean>() }


    LaunchedEffect(isRunning) {
        if (isRunning) {

            startTime = System.currentTimeMillis() - elapsedMs
            while (isRunning) {
                elapsedMs = System.currentTimeMillis() - startTime
                delay(100L)
            }
        }
    }

    LaunchedEffect(selectedMain) { selectedSubs.clear() }

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        val totalSeconds = elapsedMs / 1000f
        val minutes = (totalSeconds / 60).toInt()
        val remainingSeconds = totalSeconds % 60

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SESSION ACTIVE",
                style = MaterialTheme.typography.labelLarge,
                color = if (isRunning) MaterialTheme.colorScheme.primary else Color.Gray
            )
            Text(

                text = String.format(Locale.US, "%02d:%04.1f", minutes, remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { isRunning = !isRunning },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFFB00020) else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isRunning) "Stop / Pause" else "Start Session")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp).alpha(0.5f))

        if (categoryMap.isEmpty()) {
            Text("No categories found. Add some in 'Manage Categories'!")
        } else {
            CategorySelector(selectedMain, { selectedMain = it }, selectedSubs, categoryMap)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),

            enabled = elapsedMs >= 1000L && !isRunning,
            onClick = {
                val finalSubs = selectedSubs.filter { it.value }.keys.toList()
                val subsString = if (finalSubs.isEmpty()) "unspecified" else finalSubs.joinToString(", ")

                val secondsToSave = (elapsedMs / 1000L)
                dbHelper.insertSession(selectedMain, subsString, System.currentTimeMillis(), secondsToSave)

                // Reset
                elapsedMs = 0L
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

        Button(
            onClick = {
                if (mainInput.isNotBlank() && subInput.isNotBlank()) {
                    dbHelper.insertCategory(mainInput, subInput)
                    mainInput = ""; subInput = ""; refreshCounter++
                }
            },
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()
        ) { Text("Add Category") }

        HorizontalDivider()
        Text("Existing Categories", modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            categories.forEach { (main, subs) ->
                item {

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = main,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = { dbHelper.deleteMainCategory(main); refreshCounter++ }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Main Category",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                items(subs) { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sub, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { dbHelper.deleteCategory(main, sub); refreshCounter++ }) {
                            Icon(Icons.Default.Delete, "Delete Sub", tint = MaterialTheme.colorScheme.error)
                        }
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

    val importDbLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            try {
                val dbFile = dbHelper.getDatabasePath(context)

                dbHelper.close()

                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                scope.launch {
                    snackbarHostState.showSnackbar("Database Imported! Please restart the app.")
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Import Failed: ${e.message}") }
            }
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
            OutlinedButton(
                onClick = { importDbLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFFB300)
                )
            ) {
                Text("Import SQLite (Overwrites Data)")
            }
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            Button(onClick = { exportCsvLauncher.launch("time_logs.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export to CSV") }
        }
    }
}
@Composable
fun MetricsScreen(dbHelper: DatabaseHelper) {

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
            val zoneId = ZoneId.systemDefault()
            val now = Instant.now().atZone(zoneId)

            val startOfDay = now.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            val localMidnightMillis = LocalDate.now(zoneId)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()

            val grouped = list.groupBy { it.main }

            val heat = grouped.mapValues { (_, s) ->
                s.map {
                    Instant.ofEpochMilli(it.timestamp)
                        .atZone(zoneId)
                        .toLocalDate()
                        .toEpochDay()
                }.toSet()
            }
            val colors = grouped.keys.associateWith { CategoryColors.getColor(it) }
            val daily = dbHelper.getTimeByCategory(localMidnightMillis)

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
            item {
                val todayDate = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d y"))
                Text("$todayDate", style = MaterialTheme.typography.headlineSmall)
            }
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
                    Spacer(Modifier.height(24.dp))

                    // 1. Title of the Main Category
                    Text(main, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)

                    // 2. The Overall Category Bar
                    OverallCategorySummary(sessions)

                    HorizontalDivider(Modifier.padding(vertical = 8.dp).alpha(0.3f))

                    // 3. The existing subcategory breakdown
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

    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(Modifier.height(200.dp).fillMaxWidth()) {
            // Y-Axis
            Column(Modifier.fillMaxHeight().width(40.dp), Arrangement.SpaceBetween, Alignment.End) {
                Text(String.format(Locale.US, "%.0fh", maxH), style = MaterialTheme.typography.labelSmall)
                Text(String.format(Locale.US, "%.0fh", maxH / 2), style = MaterialTheme.typography.labelSmall)
                Text("0h", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.width(8.dp))

            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val slotWidth = w / 7f
                    val getX: (Int) -> Float = { i -> (i * slotWidth) + (slotWidth / 2f) }

                    repeat(3) { i ->
                        val y = h - (i * (h / 2f))
                        drawLine(Color.LightGray.copy(0.2f), Offset(0f, y), Offset(w, y))
                    }

                    last7Days.forEachIndexed { i, _ ->
                        val x = getX(i)
                        drawLine(Color.LightGray.copy(0.5f), Offset(x, h), Offset(x, h + 15f), 2f)
                    }

                    traces.forEach { (cat, dayMap) ->
                        val path = Path()
                        val color = colors[cat] ?: Color.Gray
                        last7Days.forEachIndexed { i, day ->
                            val hours = dayMap[day] ?: 0f
                            val x = getX(i)
                            val y = h - (if (maxH > 0) (hours / maxH * h) else 0f)

                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(color, 6f, Offset(x, y))
                        }
                        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp)) {
            last7Days.forEach { day ->
                val dayDate = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(day * dMs))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = dayDate, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            traces.keys.forEach { category ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 16.dp, bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(colors[category] ?: Color.Gray, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
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
    val columns = dayGrid.chunked(4)
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        categoryActivity.forEach { (category, activeDays) ->

            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(bottom = 4.dp)
            ) {
                columns.forEach { columnDays ->
                    Column {
                        columnDays.forEach { dayTimestamp ->
                            val isActive = activeDays.contains(dayTimestamp)
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .padding(2.dp)
                                    .background(
                                        if (isActive) colors[category] ?: Color.Gray
                                        else Color.Gray.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Less ", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                val color = colors[category] ?: Color.Gray
                listOf(0.15f, 0.4f, 0.7f, 1.0f).forEach { opacity ->
                    Box(
                        Modifier
                            .size(12.dp)
                            .padding(1.dp)
                            .background(color.copy(alpha = opacity), RoundedCornerShape(1.dp))
                    )
                }

                Text(" More", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}


@Composable
fun OverallCategorySummary(sessions: List<DatabaseHelper.SessionData>) {
    val totalHours = sessions.sumOf { it.duration.toDouble() }.toFloat() / 3600f

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("OVERALL PROGRESS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = String.format(Locale.US, "%.1f hrs", totalHours),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = if (totalHours > 10000f) Color(0xFF1E90FF) else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(getThermalBrush(totalHours), RoundedCornerShape(10.dp))
            )
        }
    }
}

@Composable
fun CumulativeSubCategoryChart(sessions: List<DatabaseHelper.SessionData>) {
    val subTotals = sessions.groupBy { it.subs }
        .mapValues { it.value.sumOf { s -> s.duration.toDouble() }.toFloat() / 3600f }
        .toList()
        .sortedByDescending { it.second }

    val maxSubHours = subTotals.sumOf { it.second.toDouble() }.toFloat()

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        subTotals.forEach { (sub, hours) ->
            val progress = if (maxSubHours > 0) (hours / maxSubHours).coerceIn(0f, 1f) else 0f

            Column(Modifier.padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format(Locale.US, "%.1fh", hours),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hours > 10000f) Color(0xFF1E90FF) else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(getThermalBrush(hours), RoundedCornerShape(5.dp))
                    )
                }
            }
        }
    }
}


@Composable
fun getThermalBrush(hours: Float): Brush {
    val targetHours = 10000f
    return if (hours <= targetHours) {
        val heatIntensity = (hours / targetHours).coerceIn(0f, 1f)
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF8B0000),
                lerpColor(Color(0xFFFF4500), Color(0xFFFFD700), heatIntensity)
            )
        )
    } else {
        val blueIntensity = ((hours - targetHours) / targetHours).coerceIn(0f, 1f)
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFD700),
                lerpColor(Color(0xFF00FFFF), Color(0xFF1E90FF), blueIntensity)
            )
        )
    }
}


fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(dbHelper: DatabaseHelper) {
    var refreshCounter by remember { mutableIntStateOf(0) }
    val sessions = remember(refreshCounter) { dbHelper.getRawSessions().sortedByDescending { it.timestamp } }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }


    var editingSession by remember { mutableStateOf<DatabaseHelper.SessionData?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    dbHelper.clearAllSessions()
                    refreshCounter++
                    scope.launch { snackbarHostState.showSnackbar("All sessions cleared.") }
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                icon = { Icon(Icons.Default.Delete, null) },
                text = { Text("Reset All Data") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("Session History", style = MaterialTheme.typography.headlineMedium)
            Text("${sessions.size} total entries", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            Spacer(Modifier.height(16.dp))

            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data recorded yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {

                    item {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Date/Category", Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium)
                            Text("Time", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            Text("Action", Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                        }
                    }

                    items(sessions) { session ->
                        val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(session.timestamp))

                        Card(
                            onClick = { editingSession = session },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                Modifier.padding(8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1.5f)) {
                                    Text(dateStr, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Text(session.main, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(session.subs, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }

                                Text(
                                    text = String.format(Locale.US, "%.2fh", session.duration / 3600f),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                IconButton(
                                    onClick = {
                                        dbHelper.deleteSession(session.id)
                                        refreshCounter++
                                    },
                                    modifier = Modifier.weight(0.5f)
                                ) {
                                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        }

        editingSession?.let { session ->
            var newSeconds by remember { mutableStateOf((session.duration).toString()) }

            AlertDialog(
                onDismissRequest = { editingSession = null },
                title = { Text("Edit Session Time") },
                text = {
                    Column {
                        Text("${session.main} - ${session.subs}", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newSeconds,
                            onValueChange = { newSeconds = it.filter { char -> char.isDigit() } },
                            label = { Text("Duration (seconds)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val seconds = newSeconds.toLongOrNull() ?: 0L
                        dbHelper.updateSessionDuration(session.id, seconds)
                        refreshCounter++
                        editingSession = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { editingSession = null }) { Text("Cancel") }
                }
            )
        }
    }
}