package Notifications
/*
 * AppNotificationsDb
 *
 * Base de données Room contenant l'historique local des notifications émises par l'app.
 *
 * Pourquoi une DB ?
 * Android ne permet pas de reconstruire un historique fiable des notifications
 * après qu'elles aient été supprimées du tiroir système. Nous devons donc les
 * persister nous-mêmes pour l'écran Notifications.
 *
 * Cette base est un singleton afin :
 *  - d'éviter plusieurs connexions concurrentes
 *  - de garantir une source unique de vérité
 *  - d'éviter les fuites mémoire
 *
 * Elle ne contient qu'une seule table : AppNotificationEntity.
 */

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Définit la base de données de Room (fichier .db contenant les tables).
@Database(
    entities = [AppNotificationEntity::class], // entity sur laquelle est basée la table contenue
    version = 1, // Initiale. Augmenter lorsque structure change.
    exportSchema = true // Dossier de schéma configuré.
)
/* Classe abstraite pcq c'est Romm qui fabrique la vraie classe (cachée),
lors de la compilation.
impléme
 */
abstract class AppNotificationsDb : RoomDatabase() {
    abstract fun dao() :AppNotificationsDao

    /* Companion object pour mettre des variables/fonctions appartenant à
    la classe elle-même, pas au objets instanciés. Permet l'appel sans créer d'instance.
    Une seule valeur globale <-> une par instance.
    Utile ici pour accéder aux informations sans créer d'instance, ce qu'android ne veut pas.
    Permet de stocker l'unique instance globale.
     */
    companion object{
        /* @Volatile garantit qu'une variable modifiée par un thread est
        immédiatement visible par les autres threads. Permet que chaque lecture
        lise la valeur actualisée en RAM.
        */
        @Volatile private var instance: AppNotificationsDb? = null

        // context nécessaire pour accéder au sustème de fichiers (stocket la Db).
        fun get(context: Context): AppNotificationsDb {
            /* Retournes instance si non null.
            Sinon, on entre dans bloc synchronized (pour éviter que plusieurs threads modifient une donnée en même temps).             */
            return instance ?: synchronized(this) {
                /* Re-check instance car un autre thread a pu le créer pendant l'attente du lock.
                On construit s'il est toujours null. Si entretemps instance est non null, on reprend sa
                valeur et aucune nouvelle n'est créée.*/
                instance ?: Room.databaseBuilder( // "Double-checked locking"
                    context.applicationContext,
                    AppNotificationsDb::class.java,
                    "app_notifications.db" // nom du fichier Db dans stockage interne de l'app.
                ).build().also { instance = it } // Objet enregistré dans instance
            }
        }
    }
}