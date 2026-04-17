#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include <cmath>
#include <algorithm>

// Simulates tape slowdown/speedup via variable-rate playback.
class TapeStopProcessor : public SnapinProcessor {
public:
    enum Params {
        PLAY = 0, STOP_TIME, START_TIME, CURVE, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // 5 seconds buffer for slowdown accumulation
        int maxSamples = static_cast<int>(5.0 * sampleRate) + 16;
        bufL_.prepare(maxSamples);
        bufR_.prepare(maxSamples);
        currentRate_ = 1.0f;
        readPhase_ = 0.0;
        playing_ = true;
    }

    void process(float* left, float* right, int numFrames) override {
        float targetRate = playing_ ? 1.0f : 0.0f;
        float transTimeMs = playing_ ? startTimeMs_ : stopTimeMs_;
        float transTimeSamples = transTimeMs * 0.001f * static_cast<float>(sampleRate_);
        float rateStep = 1.0f / std::max(1.0f, transTimeSamples);

        // Curve: 0% = linear, 100% = exponential
        float curveAmt = curve_ / 100.0f;

        for (int i = 0; i < numFrames; i++) {
            // Write input
            bufL_.write(left[i]);
            bufR_.write(right[i]);

            // Ramp rate towards target
            if (currentRate_ < targetRate) {
                currentRate_ += rateStep;
                if (currentRate_ > targetRate) currentRate_ = targetRate;
            } else if (currentRate_ > targetRate) {
                currentRate_ -= rateStep;
                if (currentRate_ < targetRate) currentRate_ = targetRate;
            }

            // Apply curve shaping to rate
            float shapedRate = currentRate_;
            if (curveAmt > 0.0f) {
                // Exponential curve
                float linear = currentRate_;
                float exponential = (currentRate_ > 0.001f)
                    ? std::pow(currentRate_, 2.0f) : 0.0f;
                shapedRate = linear * (1.0f - curveAmt) + exponential * curveAmt;
            }

            // Variable-rate read from buffer
            readPhase_ += static_cast<double>(shapedRate);
            int readOffset = static_cast<int>(readPhase_);

            if (readOffset > 0 && shapedRate > 0.001f) {
                float frac = static_cast<float>(readPhase_ - static_cast<double>(readOffset));
                // Simple linear interpolation for variable rate
                float s0L = bufL_.readReverse(1);
                float s1L = bufL_.readReverse(0);
                float s0R = bufR_.readReverse(1);
                float s1R = bufR_.readReverse(0);
                left[i]  = s0L + frac * (s1L - s0L);
                right[i] = s0R + frac * (s1R - s0R);
                readPhase_ -= readOffset;
            } else if (shapedRate <= 0.001f) {
                // Stopped: output silence or last sample
                left[i] = 0.0f;
                right[i] = 0.0f;
            }
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case PLAY:
                playing_ = value > 0.5f;
                break;
            case STOP_TIME:  stopTimeMs_ = std::max(50.0f, std::min(5000.0f, value)); break;
            case START_TIME: startTimeMs_ = std::max(50.0f, std::min(5000.0f, value)); break;
            case CURVE:      curve_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case PLAY:       return playing_ ? 1.0f : 0.0f;
            case STOP_TIME:  return stopTimeMs_;
            case START_TIME: return startTimeMs_;
            case CURVE:      return curve_;
            default: return 0.0f;
        }
    }

    void reset() override {
        bufL_.reset(); bufR_.reset();
        currentRate_ = 1.0f;
        readPhase_ = 0.0;
        playing_ = true;
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Tape Stop"; }
    SnapinType getType() const override { return SnapinType::TAPE_STOP; }

private:
    bool playing_ = true;
    float stopTimeMs_ = 500.0f;
    float startTimeMs_ = 500.0f;
    float curve_ = 50.0f;

    DelayLine bufL_, bufR_;
    float currentRate_ = 1.0f;
    double readPhase_ = 0.0;
};
