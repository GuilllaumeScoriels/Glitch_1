package Notifications
/*
* Représentation persistante d’une notification émise par l’application.
*
* BUT
* ---
* Android affiche les notifications dans le panneau système, mais ne fournit
* pas un historique fiable et contrôlable pour l’interface interne de l’app.
* Dès qu’une notification est supprimée par l’utilisateur, elle disparaît.
*
* Pour pouvoir afficher un écran "Notifications" dans l’application avec :
* * un historique complet
* * un tri chronologique
* * des regroupements (ex: anciennes notifications de créneau)
* * un comportement UI personnalisé
*
* nous devons posséder notre propre source de vérité.
*
* Ce modèle Room représente UNE notification envoyée par l’application.
*
* CONCEPT IMPORTANT
* ---
* La notification système Android devient uniquement un "signal visuel".
* La vraie donnée est stockée ici.
*
* Champs
* ---
* key        : identifiant unique stable pour éviter les doublons
* type       : type logique de notification (SLOT_START, SOCIAL, etc.)
* title/text : contenu affiché
* timestamp  : date logique de l’événement (et non de la lecture)
*
* Ce fichier ne contient volontairement AUCUNE logique métier.
* Il sert uniquement de structure de stockage.
  */


import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*@Entity est une annotation Android Room qui signifie que la classe représente une table dans la base de données.
Table SQL stocke informations: chaque ligne = une donnée; chaque colonne : un type d'information.
SQL = Structured Query Language
Permet stockage de données de l'app.
 */
@Entity(
    tableName = "app_notifications",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)

data class AppNotificationEntity(
    /* @PrimaryKey est la clé primaire de la table, càd la valeur qui identifie
    de manière unique chaque ligne: permet de retrouver ligne précise.
    La clé identifie le créneau lui-même et pas l'instant de la notification,
    pour éviter les doublons en cas de bug si la même notif est renvoyée qlq ms plus tard.
     */
    @PrimaryKey val key: String,
    val type: String,
    val title: String,
    val texte: String,
    val timestamp: Long
)