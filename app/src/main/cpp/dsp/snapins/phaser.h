#pragma once
#include "snapin_processor.h"
#include "allpass.h"
#include "lfo.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Modern phaser with feedback, cascaded first-order allpass filters,
// exponential LFO sweep, and true stereo with phase offset.
class PhaserProcessor : public SnapinProcessor {
public:
    enum Params {
        STAGES = 0, RATE, DEPTH, CENTER, FEEDBACK, SPREAD, STEREO, MIX,
        NUM_PARAMS
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
        feedbackL_ = 0.0f;
        feedbackR_ = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        int stages = std::max(2, std::min(MAX_STAGES, stages_));
        // Ensure even number of stages
        stages = (stages / 2) * 2;

        float fb = feedback_ / 100.0f;
        float mixAmt = mix_ / 100.0f;
        float stereoWidth = stereo_ / 100.0f;

        // Depth factor: how far the sweep goes from center.
        // At 100%, depthRatio = large sweep; at 0%, no sweep (stays at center).
        // We map depth 0-100% to a ratio of 1.0 (no sweep) to some max ratio.
        float depthNorm = depth_ / 100.0f;
        // Exponential sweep range: centerFreq / ratio .. centerFreq * ratio
        // ratio goes from 1 (no depth) to ~8 (full depth)
        float depthRatio = 1.0f + depthNorm * 7.0f;

        float minFreq = std::max(20.0f, center_ / depthRatio);
        float maxFreq = std::min(center_ * depthRatio, static_cast<float>(sampleRate_) * 0.45f);

        lfoL_.setRate(rate_);
        lfoR_.setRate(rate_);
        lfoR_.setPhaseOffset(spread_);

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // LFO output: -1..1, map to 0..1 for exponential sweep
            float modL = (lfoL_.next() + 1.0f) * 0.5f;
            float modR = (lfoR_.next() + 1.0f) * 0.5f;

            // Exponential frequency sweep between minFreq and maxFreq
            float freqL = minFreq * std::pow(maxFreq / minFreq, modL);
            float freqR = minFreq * std::pow(maxFreq / minFreq, modR);

            // Compute allpass coefficients from swept frequencies
            float coeffL = computeAllpassCoeff(freqL);
            float coeffR = computeAllpassCoeff(freqR);

            // Inject feedback before allpass chain
            float wetL = dryL + feedbackL_ * fb;
            float wetR = dryR + feedbackR_ * fb;

            // Cascade through allpass stages
            for (int s = 0; s < stages; s++) {
                stagesL_[s].setCoefficient(coeffL);
                stagesR_[s].setCoefficient(coeffR);
                wetL = stagesL_[s].process(wetL);
                wetR = stagesR_[s].process(wetR);
            }

            // Store feedback (from allpass output)
            feedbackL_ = wetL;
            feedbackR_ = wetR;

            // Apply stereo width: blend L/R wet signals
            // At width=0, both channels get mono sum; at width=1, fully independent
            float monoWet = (wetL + wetR) * 0.5f;
            float finalWetL = monoWet + (wetL - monoWet) * stereoWidth;
            float finalWetR = monoWet + (wetR - monoWet) * stereoWidth;

            // Mix dry + wet (sum for classic phaser notch/peak effect)
            left[i]  = dryL * (1.0f - mixAmt * 0.5f) + finalWetL * mixAmt * 0.5f;
            right[i] = dryR * (1.0f - mixAmt * 0.5f) + finalWetR * mixAmt * 0.5f;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case STAGES:   stages_ = static_cast<int>(std::max(2.0f, std::min(12.0f, value))); break;
            case RATE:     rate_ = std::max(0.01f, std::min(10.0f, value)); break;
            case DEPTH:    depth_ = std::max(0.0f, std::min(100.0f, value)); break;
            case CENTER:   center_ = std::max(200.0f, std::min(10000.0f, value)); break;
            case FEEDBACK: feedback_ = std::max(-90.0f, std::min(90.0f, value)); break;
            case SPREAD:   spread_ = std::max(0.0f, std::min(360.0f, value)); break;
            case STEREO:   stereo_ = std::max(0.0f, std::min(100.0f, value)); break;
            case MIX:      mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case STAGES:   return static_cast<float>(stages_);
            case RATE:     return rate_;
            case DEPTH:    return depth_;
            case CENTER:   return center_;
            case FEEDBACK: return feedback_;
            case SPREAD:   return spread_;
            case STEREO:   return stereo_;
            case MIX:      return mix_;
            default: return 0.0f;
        }
    }

    void reset() override {
        lfoL_.reset(); lfoR_.reset();
        for (int s = 0; s < MAX_STAGES; s++) {
            stagesL_[s].reset();
            stagesR_[s].reset();
        }
        feedbackL_ = 0.0f;
        feedbackR_ = 0.0f;
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Phaser"; }
    SnapinType getType() const override { return SnapinType::PHASER; }

private:
    float computeAllpassCoeff(float freq) const {
        float w = static_cast<float>(M_PI) * freq / static_cast<float>(sampleRate_);
        float t = std::tan(w);
        return (t - 1.0f) / (t + 1.0f);
    }

    int stages_ = 4;
    float rate_ = 0.5f;
    float depth_ = 50.0f;
    float center_ = 1000.0f;
    float feedback_ = 0.0f;
    float spread_ = 0.0f;
    float stereo_ = 100.0f;
    float mix_ = 50.0f;

    Lfo lfoL_, lfoR_;
    Allpass stagesL_[MAX_STAGES], stagesR_[MAX_STAGES];
    float feedbackL_ = 0.0f, feedbackR_ = 0.0f;
};
