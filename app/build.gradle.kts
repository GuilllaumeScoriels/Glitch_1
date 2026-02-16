//Le projet est un conteneur dans lequel les modules définissent le code à compiler.

//Le fichier gradle est le plan de construction de l'app android.
//C'est un script de configuration de build.
//Gradle est l'outil de build utilisé par Android Studio.
//    Il compile le code,
//    Il assemble les ressources (images, layouts, strings, etc),
//    Il génère les APK(Android Package Kit, le fichier d'installation de l'application) /AAB (Android App Bundle) pour les distribuer sur le téléphone ou sur le pay store.
//    Il gère les dépendances (comme les librairies externes ou le jetpack Compose par exemple)
//    Il automatise certaines tâches.

//Les plugins sont comme des extensions qui ajoutent des fonctionnalités au processus de build.
//    Les plugins disponibles pour tout le projet sont déclarés dans le build.gradle racine
//    ou dans le fichier settings.gradle.
//    Ici, on appliques les plugins dont le module a besoin.
plugins {
    alias(libs.plugins.android.application) //Plugin Android pour créer une application Android
    alias(libs.plugins.kotlin.android) //Plugin Kotlin, pour pouvoir écrire ton code en Kotlin.
    alias(libs.plugins.kotlin.compose) //Plugin spécifique pour Jetpack Compose (outil de création d’interfaces modernes en Kotlin).
    alias(libs.plugins.google.services) // optionnel mais recommandé

    // Applique les plugins sans version (prises au niveau racine)
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")

}
android { //Bloc principal qui configure Android :
    namespace = "com.example.a18" //Nom de l'espace de noms kotlin.
    //Fait référence à la manière dont le langage organise les éléments pour éviter les conflits de noms.
    //Plusieurs éléments différents peuvent avoir le même nom, tant qu'ils sont dans des packages différents.
    compileSdk = 36 //Version du SDK Android utilisée pour compiler.

    defaultConfig { //Paramètres par défaut de l'appli.
        applicationId = "com.example.a18v1" //Identifiant unique de l'app, utilisé sur le playStore
        minSdk = 23 //Version minimale d'Android supportée
        targetSdk = 36 //Version visée d'Android
        versionCode = 1 //Numéro interne pour les MàJ. Permet de savoir si une APK ou un AAB est plus récent qu'un autre.
        versionName = "1.0" //Nom affiché de la version

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        //Runner pour les tests instrumentés.
    }

    buildTypes { //Définitino des variantes de build :
        release { //Paramètres de l’appli en mode release :
//            Mode Release : C’est la version finale de ton application.
//            Caractéristiques :
//                Code optimisé (minification, obfuscation avec ProGuard/R8).
//                Pas de debug, pas de traces inutiles.
//                Signée avec ta propre clé (keystore) → pour prouver que c’est bien toi le développeur.
//                C’est ce mode qui permet de générer un APK/AAB officiel pour publication.
//                Par opposition au mode Debug utilisé pendant le développement.

            isMinifyEnabled = false //Pas d'optimisation/minification du code
            proguardFiles( //Fichiers de règles pour la minification/obfuscation, si jamais elle est activée par la suite.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions { //Définit la version de java pour compiler.
        sourceCompatibility = JavaVersion.VERSION_17
            //Définit la version de java utilisée dans le code source, càd la syntaxe que j'ai le droit d'utiliser.
            //Je peux donc utiliser toutes les nouveautés jusque java17.
            //Certaines bibliothèques, frameworks ou environnements ne supportent pas les dernières features du langage.
        targetCompatibility = JavaVersion.VERSION_17
            //Définit la version de bytecode générée par le compilateur.
            //Autrement dit : sur quelle machine virtuelle Java (JVM) ton app pourra tourner.
            //targetCompatibility = 17 → ton code sera compilé en bytecode Java 17, donc nécessite une JVM 17 minimum pour tourner.
        isCoreLibraryDesugaringEnabled = true

    }
    composeOptions { //Version du compilateur Compose utilisée.
        kotlinCompilerExtensionVersion = "1.5.13" // ou une version compatible
    }
    kotlinOptions {
        jvmTarget = "17" //Génère du bytecode Java 17 pour Kotlin.
    }
    buildFeatures {
        compose = true //Active Jetpack Compose.
    }
}

dependencies {
    // AndroidX de base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (via BOM du catalog)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Fichiers
    implementation(libs.androidx.documentfile)

    // *** Firebase (Option B KTX) ***
    // Un SEUL BoM < 34 (compatible -ktx)
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation(libs.androidbrowserhelper)
    implementation(libs.volley)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Libs additionnelles
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation ("io.coil-kt:coil-compose:2.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Desugaring (garde une seule ligne)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

kapt {
    correctErrorTypes = true
}
