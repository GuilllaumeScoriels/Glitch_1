package data.model

import androidx.annotation.Keep

@Keep
data class User(
    val uid: String = "",
    val pseudo: String = "",
    val email: String? = null,
    val photoUrl: String? = null
)
