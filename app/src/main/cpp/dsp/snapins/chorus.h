#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include "lfo.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Multi-voice chorus with LFO-modulated delay lines and stereo spread.
class ChorusProcessor : public SnapinProcessor {
public:
    enum Params {
        DELAY_MS = 0, RATE, DEPTH, SPREAD, MIX, TAPS, NUM_PARAMS
    };

    static constexpr int MAX_TAPS = 6;

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // Max delay: 50ms at any sample rate
        int maxSamples = static_cast<int>(0.05 * sampleRate) + 64;
        for (int t = 0; t < MAX_TAPS; t++) {
            delayL_[t].prepare(maxSamples);
            delayR_[t].prepare(maxSamples);
            lfoL_[t].prepare(sampleRate);
            lfoR_[t].prepare(sampleRate);
            lfoL_[t].setShape(LfoShape::Sine);
            lfoR_[t].setShape(LfoShape::Sine);
        }
        updateLfos();
    }

    void process(float* left, float* right, int numFrames) override {
        float baseDelaySamples = delayMs_ * 0.001f * static_cast<float>(sampleRate_);
        float depthSamples = baseDelaySamples * (depth_ / 100.0f);
        int taps = std::max(1, std::min(MAX_TAPS, taps_));

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];
            float wetL = 0.0f;
            float wetR = 0.0f;

            for (int t = 0; t < taps; t++) {
                // Write input to each tap's delay line
                delayL_[t].write(dryL);
                delayR_[t].write(dryR);

                // Modulated delay read position
                float modL = lfoL_[t].next();
                float modR = lfoR_[t].next();
                float readL = baseDelaySamples + modL * depthSamples;
                float readR = baseDelaySamples + modR * depthSamples;

                // Clamp to valid range
                readL = std::max(1.0f, readL);
                readR = std::max(1.0f, readR);

                float tapL = delayL_[t].readCubic(readL);
                float tapR = delayR_[t].readCubic(readR);

                // Stereo spread panning per voice
                float panAngle = (spread_ / 100.0f) * (static_cast<float>(t) / std::max(1, taps - 1) - 0.5f);
                float panNorm = (panAngle + 0.5f);  // 0..1
                float gainL = std::cos(panNorm * static_cast<float>(M_PI) * 0.5f);
                float gainR = std::sin(panNorm * static_cast<float>(M_PI) * 0.5f);

                wetL += tapL * gainL;
                wetR += tapR * gainR;
            }

            // Normalize by tap count
            float norm = 1.0f / static_cast<float>(taps);
            wetL *= norm;
            wetR *= norm;

            // Mix
            float m = mix_ / 100.0f;
            left[i]  = dryL * (1.0f - m) + wetL * m;
            right[i] = dryR * (1.0f - m) + wetR * m;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case DELAY_MS: delayMs_ = std::max(1.0f, std::min(40.0f, value)); break;
            case RATE:
                rate_ = std::max(0.01f, std::min(10.0f, value));
                updateLfos();
                break;
            case DEPTH:  depth_ = std::max(0.0f, std::min(100.0f, value)); break;
            case SPREAD: spread_ = std::max(0.0f, std::min(100.0f, value)); break;
            case MIX:    mix_ = std::max(0.0f, std::min(100.0f, value)); break;
            case TAPS:
                taps_ = static_cast<int>(std::max(1.0f, std::min(6.0f, value)));
                updateLfos();
                break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case DELAY_MS: return delayMs_;
            case RATE:     return rate_;
            case DEPTH:    return depth_;
            case SPREAD:   return spread_;
            case MIX:      return mix_;
            case TAPS:     return static_cast<float>(taps_);
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Chorus"; }
    SnapinType getType() const override { return SnapinType::CHORUS; }

private:
    void updateLfos() {
        for (int t = 0; t < MAX_TAPS; t++) {
            lfoL_[t].setRate(rate_);
            lfoR_[t].setRate(rate_);
            // Phase offset: evenly distributed + slight L/R offset
            float phaseOffset = 360.0f * static_cast<float>(t) / static_cast<float>(std::max(1, taps_));
            lfoL_[t].setPhaseOffset(phaseOffset);
            lfoR_[t].setPhaseOffset(phaseOffset + 90.0f);
        }
    }

    float delayMs_ = 7.0f;
    float rate_ = 1.0f;
    float depth_ = 50.0f;
    float spread_ = 50.0f;
    float mix_ = 50.0f;
    int taps_ = 2;

    DelayLine delayL_[MAX_TAPS], delayR_[MAX_TAPS];
    Lfo lfoL_[MAX_TAPS], lfoR_[MAX_TAPS];
};
