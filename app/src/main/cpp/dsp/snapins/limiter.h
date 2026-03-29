#pragma once
#include "snapin_processor.h"
#include "lookahead_buffer.h"
#include <cmath>
#include <algorithm>

// Brickwall lookahead limiter with 5ms lookahead.
class LimiterProcessor : public SnapinProcessor {
public:
    enum Params { INPUT_GAIN = 0, OUTPUT_GAIN, THRESHOLD, RELEASE_MS, NUM_PARAMS };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;

        // 5ms lookahead
        int lookaheadSamples = static_cast<int>(0.005 * sampleRate);
        lookaheadL_.prepare(lookaheadSamples);
        lookaheadR_.prepare(lookaheadSamples);

        // Peak hold buffer for lookahead window
        peakBuf_.resize(lookaheadSamples, 0.0f);
        peakWritePos_ = 0;
        peakBufSize_ = lookaheadSamples;

        gainReduction_ = 0.0f;
        releaseCoeff_ = calcReleaseCoeff(releaseMs_);
    }

    void process(float* left, float* right, int numFrames) override {
        float inputLin = std::pow(10.0f, inputGainDb_ / 20.0f);
        float outputLin = std::pow(10.0f, outputGainDb_ / 20.0f);
        float threshLin = std::pow(10.0f, thresholdDb_ / 20.0f);

        for (int i = 0; i < numFrames; i++) {
            // Apply input gain
            float inL = left[i] * inputLin;
            float inR = right[i] * inputLin;

            // Delay the signal
            float delL = lookaheadL_.process(inL);
            float delR = lookaheadR_.process(inR);

            // True peak detection on input (before delay)
            float peak = std::max(std::fabs(inL), std::fabs(inR));

            // Track peak in lookahead window
            peakBuf_[peakWritePos_] = peak;
            peakWritePos_ = (peakWritePos_ + 1) % peakBufSize_;

            // Find max peak in lookahead window
            float maxPeak = 0.0f;
            for (int j = 0; j < peakBufSize_; j++) {
                if (peakBuf_[j] > maxPeak) maxPeak = peakBuf_[j];
            }

            // Compute required gain reduction
            float targetGainDb = 0.0f;
            if (maxPeak > threshLin && threshLin > 0.0f) {
                targetGainDb = 20.0f * std::log10(threshLin / maxPeak);
            }

            // Smooth: instant attack (within lookahead), exponential release
            if (targetGainDb < gainReduction_) {
                gainReduction_ = targetGainDb;  // instant attack
            } else {
                gainReduction_ += releaseCoeff_ * (targetGainDb - gainReduction_);
            }

            float gainLin = std::pow(10.0f, gainReduction_ / 20.0f);

            // Apply to delayed signal + output gain
            left[i]  = delL * gainLin * outputLin;
            right[i] = delR * gainLin * outputLin;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case INPUT_GAIN:
                inputGainDb_ = std::max(-24.0f, std::min(24.0f, value));
                break;
            case OUTPUT_GAIN:
                outputGainDb_ = std::max(-24.0f, std::min(24.0f, value));
                break;
            case THRESHOLD:
                thresholdDb_ = std::max(-24.0f, std::min(0.0f, value));
                break;
            case RELEASE_MS:
                releaseMs_ = std::max(1.0f, std::min(1000.0f, value));
                releaseCoeff_ = calcReleaseCoeff(releaseMs_);
                break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case INPUT_GAIN:  return inputGainDb_;
            case OUTPUT_GAIN: return outputGainDb_;
            case THRESHOLD:   return thresholdDb_;
            case RELEASE_MS:  return releaseMs_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Limiter"; }
    SnapinType getType() const override { return SnapinType::LIMITER; }

private:
    float calcReleaseCoeff(float ms) {
        return 1.0f - std::exp(-1.0f / (ms * 0.001f * static_cast<float>(sampleRate_)));
    }

    float inputGainDb_ = 0.0f;
    float outputGainDb_ = 0.0f;
    float thresholdDb_ = 0.0f;
    float releaseMs_ = 100.0f;
    float releaseCoeff_ = 0.01f;
    float gainReduction_ = 0.0f;

    LookaheadBuffer lookaheadL_, lookaheadR_;
    std::vector<float> peakBuf_;
    int peakWritePos_ = 0;
    int peakBufSize_ = 0;
};
