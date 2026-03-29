package tf.monochrome.android.visualizer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ProjectMRendererView @JvmOverloads constructor(
    context: Context,
    private val repository: ProjectMEngineRepository,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val visualizerRenderer = VisualizerRenderer(repository)

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(visualizerRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun updatePlayback(isPlaying: Boolean) {
        repository.setPlaybackPaused(!isPlaying)
        renderMode = if (isPlaying) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        if (!isPlaying) {
            requestRender()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        repository.onSurfaceDetached()
        onPause()
        super.onDetachedFromWindow()
    }

    private class VisualizerRenderer(
        private val repository: ProjectMEngineRepository
    ) : Renderer {
        private var surfaceAttached = false

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            repository.prepareEngine()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (!surfaceAttached) {
                repository.onSurfaceAttached(width, height)
                surfaceAttached = true
            } else {
                repository.onSurfaceResized(width, height)
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            repository.renderFrame(System.nanoTime())
        }
    }
}
