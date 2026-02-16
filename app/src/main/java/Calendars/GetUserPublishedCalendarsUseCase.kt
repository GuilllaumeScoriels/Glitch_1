package Calendars

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.text.Typography.dagger

class GetUserPublishedCalendarsUseCase @Inject constructor(
    private val currentUserProvider: CurrentUserProvider,
    @ApplicationContext private val context: Context
) {
    fun execute(): Flow<List<CalendarSummary>> = flow {
        val me = currentUserProvider.currentUserId()
        val all = CalendarRepository.list(context)
        // TODO: quand CalendarSummary aura authorId/isPublished, filtre ici:
        // val filtered = all.filter { it.authorId == me && it.isPublished }
        emit(all)
    }
}
