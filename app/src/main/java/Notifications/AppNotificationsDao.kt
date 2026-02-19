package Notifications
/*

* DAO (Data Access Object) pour l’accès aux notifications stockées.
*
* BUT
* ---
* Room impose de passer par un DAO pour lire/écrire dans la base.
* Ce fichier définit TOUTES les opérations autorisées sur l’historique
* des notifications de l’application.
*
* IMPORTANT
* ---
* L’UI ne doit JAMAIS accéder directement à la base.
* Elle observe un Flow fourni par le repository.
*
* Responsabilités
* ---
* * Fournir la liste triée par date
* * Ajouter ou remplacer une notification
* * Supprimer l’historique si nécessaire
*
* Pourquoi Flow ?
* ---
* Compose peut observer automatiquement les changements et
* rafraîchir l’écran sans rechargement manuel.
*
* Principe architectural
* ---
* UI -> Repository -> DAO -> Database
*
* Le DAO ne contient aucune logique métier.
* Il est volontairement minimal et déterministe.
* C'est un contrat qui définit quelles opérations sont autorisées
* sur la table. Room génère automatiquement la classe qui
* implémente cette interface. C'est pout ça que les fonctions
* sont juste définies, pas implémentées.
  */

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNotificationsDao {
    /* @Query permet d'écrire directement "à la main" une requête SQL pour lire/modifier la base de données.
    * SELECT * ~ "prends toutes les colonnes"
    * FROM app_notifications ~ "depuis la table app_notifications"
    * ORDER BY timestamp DESC ~ "trier par timestamp du plus récent au plus ancien
    * (DESC = décroissant).
    */
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    /* fun reçois un FLUX de liste de lignes de la table => mise à jour automatique.
    * Pas un action immédiate, donc pas suspend (asychrone). */
    fun observeAll(): Flow<List<AppNotificationEntity>>

    /* @Insert demande à Room de générer une insertion SQL.
    En cas de conflit, ancienne ligne remplacée par la nouvelle. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    /* Méthode qui insère une notif.
    suspend car android interdit de bloquer le thread UI => coroutine.
     */
    suspend fun upsert(entity: AppNotificationEntity)

    // Exécute requête SQL qui supprime toutes les lignes de la table
    @Query("DELETE FROM app_notifications")
    suspend fun clearAll()

}