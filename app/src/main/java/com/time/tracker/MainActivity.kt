package com.time.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // We use a Column to stack the Timer on top of the Selectors
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TimerDisplay()
                        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
                        CategorySelector()
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
fun CategorySelector() {
    var expanded by remember { mutableStateOf(false) }
    var selectedMain by remember { mutableStateOf("Cyber") }
    val selectedSubs = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(selectedMain) {
        selectedSubs.clear()
    }

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
                            selectedMain = category
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