package com.time.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                Text(
                    "Time Tracker",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
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
            NavHost(
                navController = navController,
                startDestination = "record",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("record") { MainScreen(dbHelper) }
                composable("categories") { CategoryManagementScreen(dbHelper) }
            }
        }
    }
}

@Composable
fun MainScreen(dbHelper: DatabaseHelper) {
    var seconds by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }

    // Refresh categories whenever we enter this screen
    val categoryMap = remember { dbHelper.getCategories() }
    var selectedMain by remember { mutableStateOf(categoryMap.keys.firstOrNull() ?: "") }
    val selectedSubs = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            seconds++
        }
    }

    // Reset subs if the main category changes
    LaunchedEffect(selectedMain) {
        selectedSubs.clear()
    }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Time Spent: $seconds seconds", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Button(onClick = { isRunning = !isRunning }) {
            Text(if (isRunning) "Pause" else "Start")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        if (categoryMap.isEmpty()) {
            Text("No categories found. Go to 'Manage Categories' to add some!")
        } else {
            CategorySelector(
                selectedMain = selectedMain,
                onMainChange = { selectedMain = it },
                selectedSubs = selectedSubs,
                categoryMap = categoryMap
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            enabled = seconds > 0 && !isRunning,
            onClick = {
                val finalSubs = selectedSubs.filter { it.value }.keys.toList()
                val subsString = if (finalSubs.isEmpty()) "unspecified" else finalSubs.joinToString(", ")

                dbHelper.insertSession(
                    main = selectedMain,
                    subs = subsString,
                    start = System.currentTimeMillis(),
                    duration = seconds.toLong()
                )

                seconds = 0
                selectedSubs.clear()
            }
        ) {
            Text("Save Session")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    selectedMain: String,
    onMainChange: (String) -> Unit,
    selectedSubs: SnapshotStateMap<String, Boolean>,
    categoryMap: Map<String, List<String>>
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text("Main Category", style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = selectedMain,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )

            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categoryMap.keys.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            onMainChange(category)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Subcategories", style = MaterialTheme.typography.labelLarge)
        val availableSubs = categoryMap[selectedMain] ?: emptyList()
        availableSubs.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedSubs[sub] ?: false,
                    onCheckedChange = { isChecked -> selectedSubs[sub] = isChecked }
                )
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
        Spacer(Modifier.height(8.dp))

        TextField(
            value = mainInput,
            onValueChange = { mainInput = it },
            label = { Text("Main (e.g. Cyber)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = subInput,
            onValueChange = { subInput = it },
            label = { Text("Sub (e.g. DNEA)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (mainInput.isNotBlank() && subInput.isNotBlank()) {
                    dbHelper.insertCategory(mainInput, subInput)
                    mainInput = ""
                    subInput = ""
                    refreshCounter++
                }
            },
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()
        ) {
            Text("Add Category")
        }

        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Existing Categories", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            categories.forEach { (main, subs) ->
                item {
                    Text(
                        text = main,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(subs) { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sub)
                        IconButton(onClick = {
                            dbHelper.deleteCategory(main, sub)
                            refreshCounter++
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}