package tf.monochrome.android.radio

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val radioQueueManager: RadioQueueManager,
) : ViewModel() {
    val radioState = radioQueueManager.radioState
    val events = radioQueueManager.events

    fun startRadio(seed: RadioSeed) {
        radioQueueManager.startRadio(seed)
    }
}
