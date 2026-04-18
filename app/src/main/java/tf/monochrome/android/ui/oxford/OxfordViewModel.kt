package tf.monochrome.android.ui.oxford

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import tf.monochrome.android.audio.dsp.oxford.CompressorEffect
import tf.monochrome.android.audio.dsp.oxford.InflatorEffect
import javax.inject.Inject

@HiltViewModel
class OxfordViewModel @Inject constructor(
    val inflator: InflatorEffect,
    val compressor: CompressorEffect,
) : ViewModel()
