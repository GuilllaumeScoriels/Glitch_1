package com.example.lecturemotparmotapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WordStrip(
    prev: String,
    current: String,
    next: String,
    isFullScreen: Boolean,
    onExit: () -> Unit
) {
    val rowModifier = if (isFullScreen) {
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExit() }
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    }

    Row(
        modifier = rowModifier.wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Mot précédent
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(
                text = prev,
                color = Color.LightGray,
                fontSize = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Mot central
        Box(modifier = Modifier.weight(4f), contentAlignment = Alignment.Center) {
            Text(
                text = current,
                color = Color.White,
                fontSize = 36.sp,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 36.sp,
                    lineHeight = 44.sp,                 // espace entre lignes
                    lineBreak = LineBreak.Paragraph,    // règles de coupure
                    hyphens = Hyphens.Auto              // césure auto si possible
                ),
                softWrap = true,
                overflow = TextOverflow.Clip,          // pas d'ellipse, on garde la hauteur
                maxLines = 3
            )
        }
        Spacer(modifier = Modifier.width(6.dp))

        // Mot suivant
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = next,
                color = Color.LightGray,
                fontSize = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}