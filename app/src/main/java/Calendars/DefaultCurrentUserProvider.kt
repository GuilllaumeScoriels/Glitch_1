package Calendars

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

class DefaultCurrentUserProvider @Inject constructor() : CurrentUserProvider {
    override fun currentUserId(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
}
