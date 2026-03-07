package com.time.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.time.tracker.ui.theme.TrackerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackerTheme {
                // Scaffold provides the basic visual structure (like top bars)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // We wrap MainScreen in a Box or Surface to respect the 'innerPadding'
                    // created by enableEdgeToEdge()
                    Surface(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen() // <-- This is your new "Manager" screen
                    }
                }
            }
        }
    }
}

@Composable
fun TimerDisplay() {
    var seconds by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            seconds++
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Time Spent: $seconds seconds", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { isRunning = !isRunning }) {
            Text(if (isRunning) "Pause" else "Start")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    selectedMain: String,
    onMainChange: (String) -> Unit,
    selectedSubs: SnapshotStateMap<String, Boolean>
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
fun MainScreen() {
    // 1. Centralized State
    var seconds by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedMain by remember { mutableStateOf("Cyber") }
    val selectedSubs = remember { mutableStateMapOf<String, Boolean>() }

    // Logic to reset subcategories when main changes
    LaunchedEffect(selectedMain) {
        selectedSubs.clear()
    }

    // Logic for the timer
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            seconds++
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 2. Timer UI
        Text("Time Spent: $seconds seconds", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { isRunning = !isRunning }) {
            Text(if (isRunning) "Pause" else "Start")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        // 3. Category Selectors
        // Pass the state down to the selector function
        CategorySelector(
            selectedMain = selectedMain,
            onMainChange = { selectedMain = it },
            selectedSubs = selectedSubs
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 4. THE SAVE BUTTON
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = seconds > 0 && !isRunning, // Only save if timer stopped and > 0
            onClick = {
                // Collect the true subcategories (the ones set to 'true')
                val finalSubcategories = selectedSubs.filter { it.value }.keys.toList()

                // If nothing is checked, use our "unspecified" default
                val reportSubs = if (finalSubcategories.isEmpty()) listOf("unspecified") else finalSubcategories

                val newSession = ActivitySession(
                    mainCategory = selectedMain,
                    subCategories = reportSubs,
                    startTime = System.currentTimeMillis(),
                    durationSeconds = seconds.toLong()
                )

                // For now, let's print it to the console (Logcat)
                println("SAVED SESSION: $newSession")

                // Reset everything for the next session
                seconds = 0
            }
        ) {
            Text("Save Session")
        }
    }
}