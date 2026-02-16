package com.example.a18

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import Lecteurtxt.LectureViewModel
import com.example.lecturemotparmotapp.WordStrip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class FullScreenReadingScreen {
    @Composable
    fun Display(
        prevWord: String,
        currentWord: String,
        nextWord: String,
        vm: LectureViewModel,
        onExit: () -> Unit,
    ) {
        val coroutineScope = rememberCoroutineScope()
        WordStrip(
            prev = prevWord,
            current = currentWord,
            next = nextWord,
            isFullScreen = true,
            onExit = {
                coroutineScope.launch {
                    delay(30)
                    vm.setFullScreenMode(false)
                }
                vm.pauseReading()
            }
        )
    }
}
