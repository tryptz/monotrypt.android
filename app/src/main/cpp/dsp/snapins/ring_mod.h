#pragma once
#include "snapin_processor.h"
#include "lfo.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Ring modulation with internal sine oscillator.
class RingModProcessor : public SnapinProcessor {
public:
    enum Params {
        FREQUENCY = 0, BIAS_AMT, RECTIFY, SPREAD, MIX, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        oscPhase_ = 0.0;
    }

    void process(float* left, float* right, int numFrames) override {
        double phaseInc = 2.0 * M_PI * static_cast<double>(frequency_) / sampleRate_;
        float biasNorm = bias_ / 100.0f;
        float rectNorm = rectify_ / 100.0f;
        float spreadNorm = spread_ / 100.0f;
        float m = mix_ / 100.0f;

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // Carrier oscillator
            float carrierL = static_cast<float>(std::sin(oscPhase_));
            float carrierR = static_cast<float>(std::sin(oscPhase_ + spreadNorm * M_PI));

            // Bias: blend between full ring mod and just carrier added
            float modL = carrierL * (1.0f - biasNorm) + biasNorm;
            float modR = carrierR * (1.0f - biasNorm) + biasNorm;

            float wetL = dryL * modL;
            float wetR = dryR * modR;

            // Rectify: blend towards half/full wave rectification
            if (rectNorm != 0.0f) {
                float rectL, rectR;
                if (rectNorm > 0.0f) {
                    // Positive rectify
                    rectL = std::fabs(wetL);
                    rectR = std::fabs(wetR);
                } else {
                    // Negative rectify
                    rectL = -std::fabs(wetL);
                    rectR = -std::fabs(wetR);
                }
                float r = std::fabs(rectNorm);
                wetL = wetL * (1.0f - r) + rectL * r;
                wetR = wetR * (1.0f - r) + rectR * r;
            }

            left[i]  = dryL * (1.0f - m) + wetL * m;
            right[i] = dryR * (1.0f - m) + wetR * m;

            oscPhase_ += phaseInc;
            if (oscPhase_ > 2.0 * M_PI) oscPhase_ -= 2.0 * M_PI;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case FREQUENCY: frequency_ = std::max(1.0f, std::min(5000.0f, value)); break;
            case BIAS_AMT:  bias_ = std::max(0.0f, std::min(100.0f, value)); break;
            case RECTIFY:   rectify_ = std::max(-100.0f, std::min(100.0f, value)); break;
            case SPREAD:    spread_ = std::max(0.0f, std::min(100.0f, value)); break;
            case MIX:       mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case FREQUENCY: return frequency_;
            case BIAS_AMT:  return bias_;
            case RECTIFY:   return rectify_;
            case SPREAD:    return spread_;
            case MIX:       return mix_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Ring Mod"; }
    SnapinType getType() const override { return SnapinType::RING_MOD; }

private:
    float frequency_ = 440.0f;
    float bias_ = 0.0f;
    float rectify_ = 0.0f;
    float spread_ = 0.0f;
    float mix_ = 100.0f;

    double oscPhase_ = 0.0;
};
