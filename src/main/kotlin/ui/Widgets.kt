package ui

import ColorAkcent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TabButton(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(if (active) ColorAkcent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = if (active) Color.White else Color.Black)
    }
}
