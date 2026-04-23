#pragma once
#include "snapin_processor.h"
#include "crossfade_buffer.h"
#include <cmath>
#include <algorithm>
#include <cstdlib>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Granular pitch shifter with overlap-add crossfading.
class PitchShifterProcessor : public SnapinProcessor {
public:
    enum Params {
        PITCH = 0, JITTER, GRAIN_SIZE, MIX, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // Max grain: 200ms at any sample rate
        int maxSamples = static_cast<int>(0.2 * sampleRate) + 16;
        bufL_.prepare(maxSamples);
        bufR_.prepare(maxSamples);
        grainPhase_ = 0.0f;
        readOffsetL_ = 0.0f;
        readOffsetR_ = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        float ratio = std::pow(2.0f, pitch_ / 12.0f);
        float rateOffset = ratio - 1.0f;  // How much faster/slower to read
        int grainSamples = static_cast<int>(grainMs_ * 0.001f * static_cast<float>(sampleRate_));
        grainSamples = std::max(64, grainSamples);
        float grainInc = 1.0f / static_cast<float>(grainSamples);
        float m = mix_ / 100.0f;

        for (int i = 0; i < numFrames; i++) {
            bufL_.write(left[i]);
            bufR_.write(right[i]);

            // Two overlapping grains for crossfade
            float phase1 = grainPhase_;
            float phase2 = grainPhase_ + 0.5f;
            if (phase2 >= 1.0f) phase2 -= 1.0f;

            // Hann windows
            float win1 = CrossfadeBuffer::hannWindow(phase1);
            float win2 = CrossfadeBuffer::hannWindow(phase2);

            // Read positions with pitch offset
            float offset1 = phase1 * grainSamples * rateOffset;
            float offset2 = phase2 * grainSamples * rateOffset;

            // Add jitter
            if (jitter_ > 0.0f) {
                float j = (static_cast<float>(rand()) / RAND_MAX - 0.5f) * jitter_ / 100.0f * grainSamples * 0.1f;
                offset1 += j;
                offset2 += j;
            }

            int readIdx1 = static_cast<int>(std::fabs(offset1));
            int readIdx2 = static_cast<int>(std::fabs(offset2));

            float wetL = bufL_.readReverse(readIdx1) * win1 + bufL_.readReverse(readIdx2) * win2;
            float wetR = bufR_.readReverse(readIdx1) * win1 + bufR_.readReverse(readIdx2) * win2;

            left[i]  = left[i] * (1.0f - m) + wetL * m;
            right[i] = right[i] * (1.0f - m) + wetR * m;

            grainPhase_ += grainInc;
            if (grainPhase_ >= 1.0f) grainPhase_ -= 1.0f;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case PITCH:      pitch_ = std::max(-24.0f, std::min(24.0f, value)); break;
            case JITTER:     jitter_ = std::max(0.0f, std::min(100.0f, value)); break;
            case GRAIN_SIZE: grainMs_ = std::max(10.0f, std::min(200.0f, value)); break;
            case MIX:        mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case PITCH:      return pitch_;
            case JITTER:     return jitter_;
            case GRAIN_SIZE: return grainMs_;
            case MIX:        return mix_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Pitch Shifter"; }
    SnapinType getType() const override { return SnapinType::PITCH_SHIFTER; }

private:
    float pitch_ = 0.0f;
    float jitter_ = 0.0f;
    float grainMs_ = 50.0f;
    float mix_ = 100.0f;

    CrossfadeBuffer bufL_, bufR_;
    float grainPhase_ = 0.0f;
    float readOffsetL_ = 0.0f, readOffsetR_ = 0.0f;
};
