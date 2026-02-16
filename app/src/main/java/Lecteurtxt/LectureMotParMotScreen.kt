package com.example.lecturemotparmotapp

import Lecteurtxt.LectureViewModel
import Settings.AppSettings
import android.app.Activity
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.TextFieldValue
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.a18.FullscreenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.a18.FullScreenReadingScreen
import com.example.a18.R
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableIntStateOf
import android.os.Build
import android.Manifest
import android.widget.Toast
import androidx.compose.material.icons.filled.FileDownload


@OptIn(ExperimentalMaterial3Api::class)
@Composable //utilisé dans le jetpack compose pour définir l'UI de manière déclarative
fun LectureMotParMotScreen(
    vm: LectureViewModel,
    fullscreenManager: FullscreenManager,
    fileRepo: storage.FileRepository,

//    Appelant : passe une instance (ex. FileRepositoryImpl(context))
//    quand tu appelles LectureMotParMotScreen(...).
//    Rien d’autre ne change côté appelants.

    onHome: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val repo = remember { Settings.DataStoreSettingsRepository(context) }
    val settings by repo.settings.collectAsState(initial = AppSettings())
    // Exemple d’application :
    // - If(settings.defaultFullScreen) -> activer Immersive Mode pour cette écran
    // - Utiliser settings.wordsPerMinute pour calculer la durée d’affichage par mot
    // - Si settings.ttsEnabled -> initialiser TTS, et appliquer settings.ttsRate
    // - Si settings.keepScreenOn -> utiliser Window/Compose pour prévenir la mise en veille

    val currentWord by vm.currentWord.collectAsState()
    val isFullScreenMode by vm.isFullScreenMode.collectAsState()
    val isReading by vm.isReading.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var tfv by remember { mutableStateOf(TextFieldValue(text = vm.inputText)) }

    val prev by vm.prevWord.collectAsState()
    val next by vm.nextWord.collectAsState()
//    LaunchedEffect(isFullScreenMode) {
//        // Cette ligne déclenchera une recomposition quand le mode plein écran change
//        // petit délai pour aider Compose à stabiliser les états UI
//        kotlinx.coroutines.delay(10)
//    }

    LaunchedEffect(settings.keepScreenOn) {
        activity?.window?.let { win ->
            if (settings.keepScreenOn) {
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    if (isFullScreenMode) {
        FullScreenReadingScreen().Display(
            prevWord = prev,
            currentWord = currentWord,
            nextWord = next,
            vm = vm,
            onExit = {
                coroutineScope.launch {
                    delay(30)
                    vm.setFullScreenMode(false)
                }
            }
        )
        return
    } else {
        Button(onClick = { vm.setFullScreenMode(true) }) {
            Text("Mode Plein Écran")
        }
        // affichage normal
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val motActuel by vm.currentWord.collectAsState()

        // Launcher pour importer un fichier .txt
        val fileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                uri?.let {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    vm.loadWordsFromUri(it)
                }
            }
        )

        // Permission d’écriture pour Android 9 et moins (API < 29)
        val writePermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                if (granted) {
                        // On enregistre immédiatement le texte
                        coroutineScope.launch {
                                try {
                                        fileRepo.saveTextToDownloads(vm.inputText.trim())
                                        Toast.makeText(context, "Fichier enregistré dans Téléchargements", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur : ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                    } else {
                        Toast.makeText(context, "Permission refusée", Toast.LENGTH_SHORT).show()
                    }
            }

        fun requestOrSaveTxt() {
            val text = vm.inputText.trim()
            if (text.isBlank()) {
                Toast.makeText(context, "Le texte est vide", Toast.LENGTH_SHORT).show()
                return
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Demande la permission si nécessaire (Android 9 et moins)
                writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                // Android 10+ : on enregistre directement via MediaStore
                coroutineScope.launch {
                    try {
                        fileRepo.saveTextToDownloads(text)
                        Toast.makeText(context, "Fichier enregistré dans Téléchargements", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erreur : ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Activation du mode plein écran à l'entrée, désactivation à la sortie
        DisposableEffect(Unit) { /* un effet proposé par le jetpack Compose,
    pour exécuter code non déclaratif dans UI déclarative,
    càd du code dont on dit comment il doit s'exécuter.*/
            fullscreenManager.enableFullscreen()
            onDispose {
                fullscreenManager.disableFullscreen()
            }
        }

                Column( //permet d'empiler des éléments verticalement dans le jetpack compose
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isReading by vm.isReading.collectAsState()
                        //                Ce que ça fait :
                        //                vm.isReading est probablement un StateFlow<Boolean> ou un LiveData qui indique si la lecture est en cours.
                        //                collectAsState() transforme ce flux en state Compose pour déclencher une recomposition quand la valeur change.
                        //                by permet d’accéder directement à la valeur (isReading) sans devoir écrire .value.

                        //Bouton indiquant le temps estimé de lecture
                        var afficherTemps by remember { mutableStateOf(false) }
                        var tempsLecture by remember { mutableStateOf("") }
                        var clickId by remember { mutableIntStateOf(0) } // change à chaque clic

                        if (!afficherTemps) {
                            Button(
                                onClick = {
                                    // LIRE l’index AU CLIC (ne pas le mémoriser ailleurs)
                                    val temps = showTime(vm, vm._currentIndex)
                                    tempsLecture = temps
                                    afficherTemps = true
                                    clickId++ // garantit que le LaunchedEffect se relance à chaque clic
                                }
                            ) {
                                Text("Afficher temps")
                            }
                        } else {
                            Text(
                                text = tempsLecture,
                                color = Color.White
                            )

                            // Se relance à CHAQUE clic grâce à clickId
                            LaunchedEffect(clickId) {
                                // pendant l’affichage, on peut aussi rafraîchir le texte si l’index avance
                                // (optionnel) -> décommente si tu veux un update en live pendant les 3s
                                // val t0 = System.currentTimeMillis()
                                // while (System.currentTimeMillis() - t0 < 3000) {
                                //     tempsLecture = showTime(vm, vm.indexInit)
                                //     delay(200)
                                // }

                                delay(3000)
                                afficherTemps = false
                            }
                        }

                        // ⏪ Mot précédent
                        FilledIconButton(
                            onClick = { vm.stepBackwardOne() },
                            enabled = prev.isNotBlank(),
                            shape = RectangleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ChevronLeft,
                                contentDescription = "Mot précédent"
                            )
                        }

                        FilledIconButton(
                            //                    Ce que ça fait :
                            //                    C’est un bouton “icône remplie” fourni par Material 3 (à la différence de IconButton qui est transparent).
                            //                    Il affiche uniquement une icône (pas de texte) et applique un style prédéfini (fond coloré, forme, effet ripple).
                            onClick = {
                                vm.clearText()
                                vm.stopReading()
                            },
                            //                    Ce que ça fait :
                            //                    Action exécutée quand on appuie sur le bouton.
                            //                    Ici, ça appelle la méthode clearText() du viewModel, qui efface probablement le texte chargé.
                            enabled = true,
                            //                    Ce que ça fait :
                            //                    Définit si le bouton est actif ou grisé.
                            //                    Ici, le bouton est activé en permanence.
                            shape = RectangleShape, // carré
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
                            //                    Ce que ça fait :
                            //                    Applique un thème de couleurs pour ce bouton.
                            //                    containerColor = Color.White → le fond du bouton est blanc.
                            //                    IconButtonDefaults.filledIconButtonColors est la méthode Material 3 pour obtenir facilement un jeu de couleurs adapté à ce composant.
                            modifier = Modifier.size(48.dp)
                            //                    Ce que ça fait :
                            //                    Fixe largeur et hauteur du bouton à 48.dp.
                            //                    Le bouton est donc parfaitement carré.
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_delete_red),
                                contentDescription = "Supprimer le texte",
                                tint = Color.Unspecified // garder le rouge du vecteur
                            )
                            //                    Ce que ça fait :
                            //                    L’accolade { ... } contient le contenu du bouton — ici une icône.
                            //                    painter = painterResource(id = R.drawable.ic_delete_red) charge une ressource vectorielle ou bitmap depuis res/drawable.
                            //                    contentDescription est la description textuelle pour l’accessibilité (ex. lecteur d’écran).
                            //                    tint = Color.Unspecified indique à Compose de ne pas recolorer l’icône (on garde sa couleur originale — ici rouge).
                        }

                        // ✅ Bouton Play Pause.
                        FilledIconButton(
                            onClick = {
                                vm.prepareWordsFromInputIfNeeded()
                                vm.togglePlayPause()
                            },
                            enabled = true,
                            shape = RectangleShape, // carré
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_play_pause),
                                contentDescription = "Play Pause",
                                tint = Color.Unspecified // garder le rouge du vecteur
                            )
                        }

                        // ⬇️ Télécharger (.txt)
                        FilledIconButton(
                            onClick = { requestOrSaveTxt() },
                            enabled = vm.inputText.isNotBlank(),
                            shape = RectangleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = "Télécharger (.txt)"
                            )
                        }

                        FilledIconButton(
                            onClick = onHome,
                            enabled = true,
                            shape = RectangleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = "Accueil"
                            )
                        }
                    }

                    // Affichage du curseur pour le choix de la vitesse de lecture
                    vitesseLectureSlider(vm)

                    // ✅ Bouton pour importer un fichier
                    Button(
                        onClick = {
                            fileLauncher.launch(
                                arrayOf(
                                    "text/plain",
                                    "application/pdf"
                                )
                            ); vm.newtxt = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Importer un fichier")
                    }


                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            vm.tryStartReading()
                            vm.setFullScreenMode(true)
                            vm.startReading(true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mode Plein Écran")
                    }

                    // Affichage du mot actuel
                    WordStrip(
                        prev = prev,
                        current = currentWord,
                        next = next,
                        isFullScreen = isFullScreenMode,
                        onExit = {})

                    // ——— CHAMP DE TEXTE : occupe TOUT l’espace restant ———
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val inputText = vm.inputText
                    var tfv by remember { mutableStateOf(TextFieldValue(text = inputText)) }
                    LaunchedEffect(inputText) { tfv = tfv.copy(text = inputText) }

                    Box(
                        modifier = Modifier
                            .weight(1f)            // <= prend tout l'espace dispo entre le haut et le bouton
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        CursorAwareTextInput(
                            value = tfv,
                            onValueChange = { v ->
                                tfv = v
                                vm.updateInputText(v.text)
                            },
                            onOkClicked = { keyboardController?.hide() },
                            onCursorPositionChanged = { caret ->
                                vm.setStartFromCharOffset(caret)
                            }
                        )
                    }

                    // ——— BOUTON EN BAS ———
                    Button(
                        onClick = {
                            vm.prepareWordsFromInputIfNeeded()
                            vm.initializeIndex()
                            vm.stopReading()
                            vm.startReading(false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lancer la lecture")
                    }
                }


            }

            BackHandler {
                fullscreenManager.disableFullscreen()
                // TODO : gérer la navigation ici (par exemple passer un navController)
            }
        }

