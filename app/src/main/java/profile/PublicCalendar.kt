package profile

data class PublicCalendar(
    val id: String,
    val ownerId: String,
    val title: String,
    val completion: Double,
    val itemCount: Int,
    val updatedAt: Long,
    val likesCount: Int
)
