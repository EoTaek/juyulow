package eotaek.juyulow.feature.main.completion

import androidx.lifecycle.ViewModel
import eotaek.juyulow.core.data.JourneyRepository
import eotaek.juyulow.core.model.NavigationSelection

class CompletionViewModel(selectionId: String, journeys: JourneyRepository) : ViewModel() {
    val selection: NavigationSelection? = journeys.selection(selectionId)
}
