package com.time.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.* // This imports a bunch of helpful stuff at once
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.time.tracker.ui.theme.TrackerTheme
import kotlinx.coroutines.delay // Correct import

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // CHANGE: Call your new function here!
                    Column(modifier = Modifier.padding(innerPadding)) {
                        TimerDisplay()
                    }
                }
            }
        }
    }
}

@Composable
fun TimerDisplay() {
    // 'by' requires the 'getValue' and 'setValue' imports included in 'runtime.*'
    var seconds by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L) // Simple long value, no named argument needed
            seconds++
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Time Spent: $seconds seconds", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = { isRunning = !isRunning }) {
            Text(if (isRunning) "Pause" else "Start")
        }
    }
}