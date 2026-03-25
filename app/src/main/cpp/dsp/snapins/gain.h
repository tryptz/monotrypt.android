#pragma once
#include "snapin_processor.h"
#include "parameter_smoother.h"
#include <cmath>

class GainProcessor : public SnapinProcessor {
public:
    enum Params { GAIN_DB = 0, NUM_PARAMS };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        gainSmooth_.prepare(sampleRate, 5.0);
        gainSmooth_.reset(1.0f);  // 0 dB
    }

    void process(float* left, float* right, int numFrames) override {
        for (int i = 0; i < numFrames; i++) {
            float g = gainSmooth_.next();
            left[i] *= g;
            right[i] *= g;
        }
    }

    void setParameter(int index, float value) override {
        if (index == GAIN_DB) {
            gainDb_ = value;
            float linear = (value <= -100.0f) ? 0.0f : std::pow(10.0f, value / 20.0f);
            gainSmooth_.setTarget(linear);
        }
    }

    float getParameter(int index) const override {
        if (index == GAIN_DB) return gainDb_;
        return 0.0f;
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Gain"; }
    SnapinType getType() const override { return SnapinType::GAIN; }

private:
    float gainDb_ = 0.0f;
    ParameterSmoother gainSmooth_;
};
