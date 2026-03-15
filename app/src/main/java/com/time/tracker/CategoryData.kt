package com.time.tracker

val categoryMap = mapOf(
    "Language" to listOf("Vocabulary", "Grammar", "Listening"),
    "Cyber" to listOf("Network Analysis", "Offensive Ops", "Defensive Ops"),
    "Analysis" to listOf("Data Cleaning", "Visualization", "Modeling"),
    "Mathematics" to listOf("Statistics", "Calculus", "Probability")
)

data class ActivitySession(
    val id: Int = 0,
    val mainCategory: String,
    val subCategories: List<String>,
    val startTime: Long,
    val durationSeconds: Long
)