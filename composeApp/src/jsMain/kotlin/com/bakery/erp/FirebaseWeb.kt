package com.bakery.erp

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize

// Reemplaza estos valores con los de tu proyecto Firebase Console
// (los obtienes en: Configuración del proyecto → Tus apps → app web → Configuración del SDK)
private val firebaseOptions = FirebaseOptions(
    apiKey = "AIzaSyCC1nIxA5kWBGcrogka3APFhqbs6j2oNhM",
    authDomain = "bakery-erp-saas.firebaseapp.com",
    projectId = "bakery-erp-saas",
    storageBucket = "bakery-erp-saas.firebasestorage.app",
    gcmSenderId = "412638080390",
    applicationId = "1:412638080390:web:796d1dcec9d4bfac305007"
)

fun initializeFirebaseWeb() {
    Firebase.initialize(options = firebaseOptions)
}
