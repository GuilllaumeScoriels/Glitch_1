package planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxChars: Int = 2
) {
    val shape = RoundedCornerShape(8.dp)
    val outline = MaterialTheme.colorScheme.outline
    val container = MaterialTheme.colorScheme.surface

    Box(
        modifier
            .clip(shape)
            .border(1.dp, outline, shape)
            .background(container)
            .heightIn(min = 32.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = {
                val digits = it.filter { c -> c.isDigit() }.take(maxChars)
                onValueChange(digits)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center),
            modifier = Modifier.width(72.dp)
        )
        if (value.isEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
