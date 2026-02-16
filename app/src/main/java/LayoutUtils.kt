package com.example.lecturemotparmotapp

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment

@Composable
fun BoundedArea(
    modifier: Modifier = Modifier,
    maxHeight: Dp = 160.dp,
    scrollWhenOverflow: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val bounded = modifier
        .fillMaxWidth()
        .heightIn(max = maxHeight)

    val finalModifier = if (scrollWhenOverflow) {
        bounded.verticalScroll(rememberScrollState())
    } else {
        bounded
    }

    Box(
        modifier = finalModifier,
        contentAlignment = contentAlignment,
        content = content
    )
}
