package com.example.lecturemotparmotapp

import Lecteurtxt.LectureViewModel
import kotlinx.coroutines.flow.MutableStateFlow

fun showTime(vm: LectureViewModel, indexx: Int): String {
    val remainingTime = calculTime(vm.words, indexx, vm)
    return formatMillis(remainingTime)
}

fun calculTime(
    words: MutableStateFlow<List<String>>,
    indexx: Int,
    vm: LectureViewModel
): Long{
    var totTime: Long = 0
    var utilWords = mutableListOf<String>()
    val wordList = words.value
    for (i in indexx until wordList.size - 1){
        utilWords.add(wordList[i])
    }
    for (word in utilWords) {
        totTime += vm.computeDelayFor(word)
    }
    return totTime
}

/**
 * Convertit des millisecondes en format m:ss (ex: 1:05 pour 65 secondes)
 */
fun formatMillis(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

