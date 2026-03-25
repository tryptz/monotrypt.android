#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include <cmath>
#include <algorithm>

// Stereo widening via inter-channel delay (precedence effect).
class HaasProcessor : public SnapinProcessor {
public:
    enum Params { CHANNEL = 0, DELAY_MS, NUM_PARAMS };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // Max 30ms
        int maxSamples = static_cast<int>(0.03 * sampleRate) + 16;
        delay_.prepare(maxSamples);
    }

    void process(float* left, float* right, int numFrames) override {
        float delaySamples = delayMs_ * 0.001f * static_cast<float>(sampleRate_);
        delaySamples = std::max(0.0f, delaySamples);

        for (int i = 0; i < numFrames; i++) {
            if (channel_ == 0) {
                // Delay left channel
                delay_.write(left[i]);
                left[i] = delay_.readCubic(delaySamples);
            } else {
                // Delay right channel
                delay_.write(right[i]);
                right[i] = delay_.readCubic(delaySamples);
            }
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case CHANNEL:  channel_ = static_cast<int>(value > 0.5f ? 1 : 0); break;
            case DELAY_MS: delayMs_ = std::max(0.0f, std::min(30.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case CHANNEL:  return static_cast<float>(channel_);
            case DELAY_MS: return delayMs_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Haas"; }
    SnapinType getType() const override { return SnapinType::HAAS; }

private:
    int channel_ = 0;  // 0=Left, 1=Right
    float delayMs_ = 10.0f;
    DelayLine delay_;
};
