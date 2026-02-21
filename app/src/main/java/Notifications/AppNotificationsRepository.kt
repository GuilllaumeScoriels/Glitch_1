package Notifications
/*
Vue d’ensemble (pipeline)

Ton code déclenche une notification
→ Notifications/NotificationHelper.kt

On construit un objet “historique”
→ AppNotificationEntity (les champs: key, type, title, text, timestamp)

On sauvegarde en base
→ AppNotificationsRepository.upsert(entity)
→ AppNotificationsDao.upsert(entity)
→ Room écrit dans app_notifications.db

L’écran Notifications observe la base (implémentation moderne du pattern observer via flow).
→ AppNotificationsRepository.observeAll() retourne un Flow<List<AppNotificationEntity>>

Room émet une nouvelle liste à chaque changement
→ Compose reçoit la nouvelle liste (collect)
→ l’UI se recompose et affiche :

la plus récente SLOT_START en clair,

les autres SLOT_START groupées déroulables,

le reste en liste
 */

/**
 * AppNotificationsRepository
 *
 * Rôle général
 * -------------
 * Cette classe constitue la couche d'accès aux données (Data Layer) pour l’historique
 * des notifications émises par l’application.
 *
 * Elle sert d’intermédiaire entre :
 *   - les producteurs de notifications (ex: NotificationHelper)
 *   - et l’interface utilisateur (ex: NotificationsScreen)
 *
 * L’application ne doit JAMAIS accéder directement au DAO ou à la base Room.
 * Tout passe par ce repository afin de centraliser la logique et garantir
 * un point d’accès unique et cohérent aux données.
 *
 *
 * Cycle de vie d’une notification
 * --------------------------------
 * 1) L’application déclenche une notification Android
 * 2) Un AppNotificationEntity est créé
 * 3) upsert(entity) est appelé → sauvegarde en base Room
 * 4) Room notifie automatiquement les observateurs
 * 5) observeAll() émet une nouvelle liste vers l’UI (Flow)
 * 6) L’écran Notifications se met à jour automatiquement (Compose)
 *
 *
 * Pourquoi utiliser un Repository ?
 * ---------------------------------
 * - Sépare l’UI du stockage (architecture propre)
 * - Permet de changer la DB sans modifier l’UI
 * - Centralise la logique métier liée aux notifications
 * - Facilite les tests
 * - Évite les accès concurrents incohérents
 *
 *
 * Singleton
 * ----------
 * Le repository est un singleton : une seule instance existe dans toute l’application.
 * Cela garantit :
 *   - une seule source de vérité
 *   - aucune duplication d’accès à la base
 *   - un comportement déterministe multi-threads
 *
 *
 * Flow et mise à jour automatique
 * --------------------------------
 * observeAll() retourne un Flow Room.
 * Dès qu’une notification est ajoutée ou modifiée, Room réémet la liste.
 * Compose observe ce flux → l’écran se recompose automatiquement.
 *
 *
 * Important
 * ---------
 * Le repository ne contient PAS d’UI et ne doit PAS connaître Compose.
 * Il expose uniquement des données et des opérations métier.
 */

import android.content.Context
// Flow est un type de flux asynchrone
import kotlinx.coroutines.flow.Flow

/* Le constructeur privé oblige à passer par le get(context) dans le companion object,
garantissant une seule instance partagée (singleton) => Db unique => pas de fuites de mémoire.
 */
class AppNotificationsRepository private constructor(
    //Accès privé au Dao, ce qui garantit que le repository en est l'unique point d'accès.
    private val dao: AppNotificationsDao
) {
    // Méthode qui renvoie un Flow de la liste des notifications triées, sur lequel Compose (l'UI) peut faire un collect.
    fun observeAll(): Flow<List<AppNotificationEntity>> = dao.observeAll()
    // Expose une fonction pour enregistrer une notif.
    suspend fun upsert(entity: AppNotificationEntity) = dao.upsert(entity)

    companion object { // contient des membres statiques.
        @Volatile private var instance: AppNotificationsRepository? = null // instance = variable statique

        fun get(context: Context): AppNotificationsRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = AppNotificationsDb.get(context) // Récupère l'instance singleton de la base Room.
                    val repo = AppNotificationsRepository(db.dao()) // Crée le répo en lui injectant le DAO (intermédiaire entre l'app et la base de données)
                    instance = repo
                    repo
                }
            }
        }
    }
}
