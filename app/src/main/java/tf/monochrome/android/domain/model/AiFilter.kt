package tf.monochrome.android.domain.model

enum class AiFilter(val displayName: String, val promptInstruction: String) {
    TEMPO(
        "Tempo",
        "Analyze the BPM, rhythm, and tempo of this track. Recommend tracks with a similar tempo and rhythmic feel."
    ),
    GENRE(
        "Genre",
        "Identify the genre, subgenre, and stylistic elements of this track. Recommend tracks from the same or closely related genres."
    ),
    YEAR(
        "Year",
        "Identify the era and approximate year/decade this track sounds like it belongs to. Recommend tracks from the same time period with a similar production style."
    ),
    SAMPLE(
        "Sample",
        "Identify any recognizable samples, interpolations, or musical elements borrowed from other tracks. Recommend the original sampled tracks, or tracks that use similar samples or production techniques."
    ),
    ALL(
        "All",
        "Perform a comprehensive analysis of this track including its tempo, genre, era, mood, instrumentation, production style, and any samples. Recommend tracks that are similar across all these dimensions."
    )
}
