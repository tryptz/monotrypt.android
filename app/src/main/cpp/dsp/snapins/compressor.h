#pragma once
#include "snapin_processor.h"
#include "envelope_follower.h"
#include <cmath>
#include <algorithm>

// Feed-forward compressor with RMS/peak detection.
class CompressorProcessor : public SnapinProcessor {
public:
    enum Params { ATTACK = 0, RELEASE, MODE, RATIO, THRESHOLD, MAKEUP, SIDECHAIN, NUM_PARAMS };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        envL_.prepare(sampleRate);
        envR_.prepare(sampleRate);
        envL_.setAttack(attackMs_);
        envL_.setRelease(releaseMs_);
        envR_.setAttack(attackMs_);
        envR_.setRelease(releaseMs_);
        envL_.setMode(mode_ == 0 ? DetectionMode::RMS : DetectionMode::Peak);
        envR_.setMode(mode_ == 0 ? DetectionMode::RMS : DetectionMode::Peak);
        gainSmooth_ = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        float smoothCoeff = 1.0f - std::exp(-1.0f / (0.002f * static_cast<float>(sampleRate_)));

        for (int i = 0; i < numFrames; i++) {
            // Detect level (linked stereo: max of L/R)
            float envValL = envL_.process(left[i]);
            float envValR = envR_.process(right[i]);
            float envVal = std::max(envValL, envValR);

            // Convert to dB
            float levelDb = (envVal > 1e-10f)
                ? 20.0f * std::log10(envVal)
                : -200.0f;

            // Compute gain reduction in dB
            float gainReductionDb = 0.0f;
            if (levelDb > thresholdDb_) {
                gainReductionDb = thresholdDb_ + (levelDb - thresholdDb_) / ratio_ - levelDb;
            }

            // Add makeup gain
            float totalGainDb = gainReductionDb + makeupDb_;

            // Smooth the gain
            gainSmooth_ += smoothCoeff * (totalGainDb - gainSmooth_);

            // Apply
            float gain = std::pow(10.0f, gainSmooth_ / 20.0f);
            left[i] *= gain;
            right[i] *= gain;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case ATTACK:
                attackMs_ = std::max(0.1f, std::min(300.0f, value));
                envL_.setAttack(attackMs_);
                envR_.setAttack(attackMs_);
                break;
            case RELEASE:
                releaseMs_ = std::max(1.0f, std::min(3000.0f, value));
                envL_.setRelease(releaseMs_);
                envR_.setRelease(releaseMs_);
                break;
            case MODE:
                mode_ = static_cast<int>(value);
                envL_.setMode(mode_ == 0 ? DetectionMode::RMS : DetectionMode::Peak);
                envR_.setMode(mode_ == 0 ? DetectionMode::RMS : DetectionMode::Peak);
                break;
            case RATIO:
                ratio_ = std::max(1.0f, std::min(100.0f, value));
                break;
            case THRESHOLD:
                thresholdDb_ = std::max(-60.0f, std::min(0.0f, value));
                break;
            case MAKEUP:
                makeupDb_ = std::max(0.0f, std::min(40.0f, value));
                break;
            case SIDECHAIN:
                sidechain_ = value > 0.5f;
                break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case ATTACK:    return attackMs_;
            case RELEASE:   return releaseMs_;
            case MODE:      return static_cast<float>(mode_);
            case RATIO:     return ratio_;
            case THRESHOLD: return thresholdDb_;
            case MAKEUP:    return makeupDb_;
            case SIDECHAIN: return sidechain_ ? 1.0f : 0.0f;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Compressor"; }
    SnapinType getType() const override { return SnapinType::COMPRESSOR; }

private:
    float attackMs_ = 10.0f;
    float releaseMs_ = 100.0f;
    int mode_ = 0;  // 0=RMS, 1=Peak
    float ratio_ = 4.0f;
    float thresholdDb_ = -18.0f;
    float makeupDb_ = 0.0f;
    bool sidechain_ = false;

    EnvelopeFollower envL_, envR_;
    float gainSmooth_ = 0.0f;
};
