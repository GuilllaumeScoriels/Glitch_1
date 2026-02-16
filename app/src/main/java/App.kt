package com.example.lecturemotparmotapp

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestoreSettings

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val db = FirebaseFirestore.getInstance()
        db.firestoreSettings = firestoreSettings {
            isPersistenceEnabled = true
        }
    }
}
