#pragma once
#include "snapin_processor.h"
#include "crossfade_buffer.h"
#include <cmath>
#include <algorithm>

// Captures audio segments, plays them backwards with crossfade.
class ReverserProcessor : public SnapinProcessor {
public:
    enum Params {
        TIME_MS = 0, SYNC, CROSSFADE_PCT, MIX, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // Max 2s
        int maxSamples = static_cast<int>(2.0 * sampleRate) + 16;
        bufL_.prepare(maxSamples);
        bufR_.prepare(maxSamples);
        writeCount_ = 0;
        readPos_ = 0;
    }

    void process(float* left, float* right, int numFrames) override {
        int segmentSamples = static_cast<int>(timeMs_ * 0.001f * static_cast<float>(sampleRate_));
        segmentSamples = std::max(64, segmentSamples);
        int crossfadeSamples = static_cast<int>(segmentSamples * crossfadePct_ / 100.0f);
        crossfadeSamples = std::max(1, crossfadeSamples);
        float m = mix_ / 100.0f;

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // Write current sample
            bufL_.write(dryL);
            bufR_.write(dryR);
            writeCount_++;

            // Read backwards from the segment boundary
            float wetL = bufL_.readReverse(readPos_);
            float wetR = bufR_.readReverse(readPos_);

            // Crossfade at segment boundaries
            float window = 1.0f;
            if (readPos_ < crossfadeSamples) {
                window = static_cast<float>(readPos_) / static_cast<float>(crossfadeSamples);
            } else if (readPos_ > segmentSamples - crossfadeSamples) {
                window = static_cast<float>(segmentSamples - readPos_) / static_cast<float>(crossfadeSamples);
            }
            window = std::max(0.0f, std::min(1.0f, window));

            wetL *= window;
            wetR *= window;

            // Advance reverse read
            readPos_++;
            if (readPos_ >= segmentSamples) {
                readPos_ = 0;
            }

            left[i]  = dryL * (1.0f - m) + wetL * m;
            right[i] = dryR * (1.0f - m) + wetR * m;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case TIME_MS:       timeMs_ = std::max(50.0f, std::min(2000.0f, value)); break;
            case SYNC:          sync_ = value > 0.5f; break;
            case CROSSFADE_PCT: crossfadePct_ = std::max(1.0f, std::min(50.0f, value)); break;
            case MIX:           mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case TIME_MS:       return timeMs_;
            case SYNC:          return sync_ ? 1.0f : 0.0f;
            case CROSSFADE_PCT: return crossfadePct_;
            case MIX:           return mix_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Reverser"; }
    SnapinType getType() const override { return SnapinType::REVERSER; }

private:
    float timeMs_ = 250.0f;
    bool sync_ = false;
    float crossfadePct_ = 10.0f;
    float mix_ = 50.0f;

    CrossfadeBuffer bufL_, bufR_;
    int writeCount_ = 0;
    int readPos_ = 0;
};
