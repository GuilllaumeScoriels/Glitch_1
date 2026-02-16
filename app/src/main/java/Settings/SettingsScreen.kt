package your.pkg.ui.settings

import Settings.SettingsViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel
) {
    val state by vm.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Vitesse par défaut (ms/mot) — même allure et même échelle que l'écran de lecture
            run {
                val minMs = 2f
                val maxMs = 2500f
                val k = 0.3f
                fun posToMs(p: Float): Int {
                    val clamped = p.coerceIn(0f, 1f)
                    val ms = maxMs - (maxMs - minMs) * clamped.pow(k)
                    return ms.roundToInt()
                }
                fun msToPos(ms: Int): Float {
                    val clamped = ms.coerceIn(minMs.toInt(), maxMs.toInt()).toFloat()
                    val ratio = (maxMs - clamped) / (maxMs - minMs)
                    return ratio.coerceIn(0f, 1f).pow(1f / k)
                }
                var pos by remember(state.defaultWordDelayMs) { mutableStateOf(msToPos(state.defaultWordDelayMs)) }
                Text("Vitesse de lecture par défaut : ")
                Slider(
                    value = pos,
                    onValueChange = { p ->
                        pos = p
                        vm.onDefaultWordDelayChange(posToMs(p))  // ← écrit tout de suite dans DataStore
                    },
                    valueRange = 0f..1f,
                    steps = 0
                )
                Text(
                    "Cette vitesse sera utilisée pour estimer le temps de lecture des fichiers.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Difficulté par défaut pour les nouveaux fichiers (1..5)
            Column {
                Text("Difficulté par défaut (nouveaux fichiers) : ${state.defaultDifficulty}")
                Slider(
                    value = state.defaultDifficulty.toFloat(),
                    onValueChange = { vm.onDefaultDifficultyChange(it.toInt().coerceIn(1, 5)) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }

            // Vitesses par difficulté (ms/mot)
            // On réutilise la même échelle non-linéaire que plus haut
            run {
                val minMs = 2f
                val maxMs = 2500f
                val k = 0.3f
                fun posToMs(p: Float): Int {
                    val clamped = p.coerceIn(0f, 1f)
                    val ms = maxMs - (maxMs - minMs) * clamped.pow(k)
                    return ms.roundToInt()
                }
                fun msToPos(ms: Int): Float {
                    val clamped = ms.coerceIn(minMs.toInt(), maxMs.toInt()).toFloat()
                    val ratio = (maxMs - clamped) / (maxMs - minMs)
                    return ratio.coerceIn(0f, 1f).pow(1f / k)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // D1
                    var p1 by remember(state.readMsD1) { mutableStateOf(msToPos(state.readMsD1)) }
                    Text("Vitesse pour difficulté 1 : ${state.readMsD1} ms/mot")
                    Slider(value = p1, onValueChange = { p1 = it; vm.onReadMsD1Change(posToMs(it)) })

                    // D2
                    var p2 by remember(state.readMsD2) { mutableStateOf(msToPos(state.readMsD2)) }
                    Text("Vitesse pour difficulté 2 : ${state.readMsD2} ms/mot")
                    Slider(value = p2, onValueChange = { p2 = it; vm.onReadMsD2Change(posToMs(it)) })

                    // D3
                    var p3 by remember(state.readMsD3) { mutableStateOf(msToPos(state.readMsD3)) }
                    Text("Vitesse pour difficulté 3 : ${state.readMsD3} ms/mot")
                    Slider(value = p3, onValueChange = { p3 = it; vm.onReadMsD3Change(posToMs(it)) })

                    // D4
                    var p4 by remember(state.readMsD4) { mutableStateOf(msToPos(state.readMsD4)) }
                    Text("Vitesse pour difficulté 4 : ${state.readMsD4} ms/mot")
                    Slider(value = p4, onValueChange = { p4 = it; vm.onReadMsD4Change(posToMs(it)) })

                    // D5
                    var p5 by remember(state.readMsD5) { mutableStateOf(msToPos(state.readMsD5)) }
                    Text("Vitesse pour difficulté 5 : ${state.readMsD5} ms/mot")
                    Slider(value = p5, onValueChange = { p5 = it; vm.onReadMsD5Change(posToMs(it)) })
                }
            }

            // Garder l’écran allumé
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Garder l’écran allumé pendant la lecture")
                Switch(
                    checked = state.keepScreenOn,
                    onCheckedChange = vm::onKeepScreenOnChange
                )
            }
        }
    }
}
