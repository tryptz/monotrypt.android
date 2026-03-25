#pragma once
#include "snapin_processor.h"
#include "allpass.h"
#include "lfo.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Cascaded allpass filters with LFO modulation.
// Order/2 = number of notch/peak pairs.
class PhaserProcessor : public SnapinProcessor {
public:
    enum Params {
        ORDER = 0, CUTOFF, DEPTH, RATE, SPREAD, MIX, NUM_PARAMS
    };

    static constexpr int MAX_STAGES = 12;

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        lfoL_.prepare(sampleRate);
        lfoR_.prepare(sampleRate);
        lfoL_.setShape(LfoShape::Sine);
        lfoR_.setShape(LfoShape::Sine);
        for (int s = 0; s < MAX_STAGES; s++) {
            stagesL_[s].reset();
            stagesR_[s].reset();
        }
    }

    void process(float* left, float* right, int numFrames) override {
        int stages = std::max(2, std::min(MAX_STAGES, order_));
        float minFreq = 20.0f;
        float maxFreq = std::min(cutoff_ * 2.0f, static_cast<float>(sampleRate_) * 0.45f);
        float depthFactor = depth_ / 100.0f;

        lfoL_.setRate(rate_);
        lfoR_.setRate(rate_);
        lfoR_.setPhaseOffset(spread_);

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // LFO modulates the allpass cutoff frequency
            float modL = (lfoL_.next() + 1.0f) * 0.5f;  // 0..1
            float modR = (lfoR_.next() + 1.0f) * 0.5f;

            // Exponential frequency sweep
            float freqL = minFreq * std::pow(maxFreq / minFreq, modL * depthFactor);
            float freqR = minFreq * std::pow(maxFreq / minFreq, modR * depthFactor);

            // Compute allpass coefficient from frequency
            float coeffL = computeAllpassCoeff(freqL);
            float coeffR = computeAllpassCoeff(freqR);

            float wetL = dryL;
            float wetR = dryR;

            for (int s = 0; s < stages; s++) {
                stagesL_[s].setCoefficient(coeffL);
                stagesR_[s].setCoefficient(coeffR);
                wetL = stagesL_[s].process(wetL);
                wetR = stagesR_[s].process(wetR);
            }

            float m = mix_ / 100.0f;
            // Sum dry + phase-shifted for notch effect
            left[i]  = dryL * (1.0f - m * 0.5f) + wetL * m * 0.5f;
            right[i] = dryR * (1.0f - m * 0.5f) + wetR * m * 0.5f;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case ORDER:  order_ = static_cast<int>(std::max(2.0f, std::min(12.0f, value))); break;
            case CUTOFF: cutoff_ = std::max(20.0f, std::min(20000.0f, value)); break;
            case DEPTH:  depth_ = std::max(0.0f, std::min(100.0f, value)); break;
            case RATE:   rate_ = std::max(0.01f, std::min(10.0f, value)); break;
            case SPREAD: spread_ = std::max(0.0f, std::min(100.0f, value)); break;
            case MIX:    mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case ORDER:  return static_cast<float>(order_);
            case CUTOFF: return cutoff_;
            case DEPTH:  return depth_;
            case RATE:   return rate_;
            case SPREAD: return spread_;
            case MIX:    return mix_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Phaser"; }
    SnapinType getType() const override { return SnapinType::PHASER; }

private:
    float computeAllpassCoeff(float freq) const {
        // First-order allpass coefficient from break frequency
        float w = static_cast<float>(M_PI) * freq / static_cast<float>(sampleRate_);
        float t = std::tan(w);
        return (t - 1.0f) / (t + 1.0f);
    }

    int order_ = 4;
    float cutoff_ = 1000.0f;
    float depth_ = 50.0f;
    float rate_ = 0.5f;
    float spread_ = 0.0f;
    float mix_ = 50.0f;

    Lfo lfoL_, lfoR_;
    Allpass stagesL_[MAX_STAGES], stagesR_[MAX_STAGES];
};
