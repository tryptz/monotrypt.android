#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include <cmath>
#include <algorithm>

// Feedforward comb filter — evenly spaced spectral peaks/troughs.
class CombFilterProcessor : public SnapinProcessor {
public:
    enum Params {
        CUTOFF = 0, MIX, POLARITY, STEREO_MODE, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // Max delay for 20Hz fundamental
        int maxSamples = static_cast<int>(sampleRate / 20.0) + 16;
        delayL_.prepare(maxSamples);
        delayR_.prepare(maxSamples);
    }

    void process(float* left, float* right, int numFrames) override {
        float delaySamples = static_cast<float>(sampleRate_) / std::max(20.0f, cutoff_);
        float pol = polarity_ ? -1.0f : 1.0f;
        float m = mix_ / 100.0f;

        for (int i = 0; i < numFrames; i++) {
            delayL_.write(left[i]);
            delayR_.write(right[i]);

            float delL = delayL_.readCubic(delaySamples);
            float delR = delayR_.readCubic(delaySamples);

            float wetL = left[i] + pol * delL * m;
            float wetR;
            if (stereoMode_) {
                // Flip polarity on R for mono-compatible widening
                wetR = right[i] + (-pol) * delR * m;
            } else {
                wetR = right[i] + pol * delR * m;
            }

            left[i] = wetL;
            right[i] = wetR;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case CUTOFF:      cutoff_ = std::max(20.0f, std::min(20000.0f, value)); break;
            case MIX:         mix_ = std::max(0.0f, std::min(100.0f, value)); break;
            case POLARITY:    polarity_ = value > 0.5f; break;
            case STEREO_MODE: stereoMode_ = value > 0.5f; break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case CUTOFF:      return cutoff_;
            case MIX:         return mix_;
            case POLARITY:    return polarity_ ? 1.0f : 0.0f;
            case STEREO_MODE: return stereoMode_ ? 1.0f : 0.0f;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Comb Filter"; }
    SnapinType getType() const override { return SnapinType::COMB_FILTER; }

private:
    float cutoff_ = 440.0f;
    float mix_ = 50.0f;
    bool polarity_ = false;  // false=positive, true=negative
    bool stereoMode_ = false;

    DelayLine delayL_, delayR_;
};
