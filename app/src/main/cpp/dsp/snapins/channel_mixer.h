#pragma once
#include "snapin_processor.h"
#include <algorithm>

// 2x2 matrix mixer for stereo routing.
class ChannelMixerProcessor : public SnapinProcessor {
public:
    enum Params { LL = 0, RL, LR, RR, NUM_PARAMS };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
    }

    void process(float* left, float* right, int numFrames) override {
        for (int i = 0; i < numFrames; i++) {
            float inL = left[i];
            float inR = right[i];
            left[i]  = inL * ll_ + inR * rl_;
            right[i] = inL * lr_ + inR * rr_;
        }
    }

    void setParameter(int index, float value) override {
        value = std::max(-1.0f, std::min(1.0f, value));
        switch (index) {
            case LL: ll_ = value; break;
            case RL: rl_ = value; break;
            case LR: lr_ = value; break;
            case RR: rr_ = value; break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case LL: return ll_;
            case RL: return rl_;
            case LR: return lr_;
            case RR: return rr_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Channel Mixer"; }
    SnapinType getType() const override { return SnapinType::CHANNEL_MIXER; }

private:
    float ll_ = 1.0f, rl_ = 0.0f, lr_ = 0.0f, rr_ = 1.0f;
};
