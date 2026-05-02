#pragma once
#include "snapin_processor.h"
#include "hilbert.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Single-sideband frequency shifter via Hilbert transform.
class FrequencyShifterProcessor : public SnapinProcessor {
public:
    enum Params { SHIFT = 0, NUM_PARAMS };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        hilbertL_.prepare(sampleRate);
        hilbertR_.prepare(sampleRate);
        oscPhase_ = 0.0;
    }

    void process(float* left, float* right, int numFrames) override {
        double phaseInc = 2.0 * M_PI * static_cast<double>(shift_) / sampleRate_;

        for (int i = 0; i < numFrames; i++) {
            float realL, imagL, realR, imagR;
            hilbertL_.process(left[i], realL, imagL);
            hilbertR_.process(right[i], realR, imagR);

            float cosOsc = static_cast<float>(std::cos(oscPhase_));
            float sinOsc = static_cast<float>(std::sin(oscPhase_));

            // SSB modulation: Re(analytic * e^(j*2π*shift*t))
            left[i]  = realL * cosOsc - imagL * sinOsc;
            right[i] = realR * cosOsc - imagR * sinOsc;

            oscPhase_ += phaseInc;
            if (oscPhase_ > 2.0 * M_PI) oscPhase_ -= 2.0 * M_PI;
            if (oscPhase_ < -2.0 * M_PI) oscPhase_ += 2.0 * M_PI;
        }
    }

    void setParameter(int index, float value) override {
        if (index == SHIFT) shift_ = std::max(-5000.0f, std::min(5000.0f, value));
    }

    float getParameter(int index) const override {
        if (index == SHIFT) return shift_;
        return 0.0f;
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Frequency Shifter"; }
    SnapinType getType() const override { return SnapinType::FREQUENCY_SHIFTER; }

private:
    float shift_ = 0.0f;
    HilbertTransform hilbertL_, hilbertR_;
    double oscPhase_ = 0.0;
};
