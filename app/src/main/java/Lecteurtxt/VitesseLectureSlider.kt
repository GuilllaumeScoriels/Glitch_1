package com.example.lecturemotparmotapp

import Lecteurtxt.LectureViewModel
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect

@Composable
fun vitesseLectureSlider(vm: LectureViewModel) {
    val minMs = 2f
    val maxMs = 2500f
    val k = 0.3f // <1 : plus petit => plus d'espace pour les grandes vitesses

    fun posToMs(p: Float): Int {
        val clamped = p.coerceIn(0f, 1f)
        val ms = maxMs - (maxMs - minMs) * clamped.pow(k)
        //Elévation à la puissance k
        return ms.roundToInt()
    }

    fun msToPos(ms: Int): Float {
        val clamped = ms.coerceIn(minMs.toInt(), maxMs.toInt()).toFloat()
        val ratio = (maxMs - clamped) / (maxMs - minMs)
        return ratio.coerceIn(0f, 1f).pow(1f / k)
    }

    // Position "logique" du pouce, dérivée de la valeur actuelle du VM
    var pos by remember { mutableStateOf(msToPos(vm._wordDelayMillis)) }

    // Si la vitesse change ailleurs (Paramètres, relance, etc.), on aligne la position du slider
    LaunchedEffect(vm._wordDelayMillis) {
        pos = msToPos(vm._wordDelayMillis)
    }

    Text("Vitesse de lecture : ${vm._wordDelayMillis} ms")
    Slider(
        value = pos,
        onValueChange = { p ->
            pos = p
            vm.setWordDelay(posToMs(p)) // applique en direct (ou déplace dans onValueChangeFinished)
        },
        valueRange = 0f..1f,
        steps = 0
        // onValueChangeFinished = { vm.setWordDelay(posToMs(pos)) } // option: n’appliquer qu’à la fin du drag
    )
}
