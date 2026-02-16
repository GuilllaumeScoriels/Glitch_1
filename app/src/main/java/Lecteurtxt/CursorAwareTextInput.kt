package com.example.lecturemotparmotapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CursorAwareTextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOkClicked: () -> Unit,
    onCursorPositionChanged: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { newV ->
                onValueChange(newV)
                onCursorPositionChanged(newV.selection.start.coerceIn(0, newV.text.length))
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart), // champ prend toute la place
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            placeholder = {
                Text("Écrivez votre texte et placez le curseur où démarrer la lecture")
            },
            singleLine = false,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White
            )
        )

        // ✅ bouton en haut à droite, superposé au champ
        IconButton(
            onClick = onOkClicked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp) // marge avec la bordure
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "OK",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
