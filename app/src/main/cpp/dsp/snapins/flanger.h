#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include "lfo.h"
#include "biquad.h"
#include <cmath>
#include <algorithm>

// Modern flanger with modulated delay, feedback with tone control,
// through-zero mode, and true stereo with LFO phase offset.
class FlangerProcessor : public SnapinProcessor {
public:
    enum Params {
        DELAY_MS = 0, DEPTH, RATE, FEEDBACK, STEREO, TONE, THROUGH_ZERO, MIX,
        NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // Max delay: 10ms base + 10ms depth modulation + headroom
        int maxSamples = static_cast<int>(0.025 * sampleRate) + 16;
        delayL_.prepare(maxSamples);
        delayR_.prepare(maxSamples);
        delayL_.reset();
        delayR_.reset();
        lfoL_.prepare(sampleRate);
        lfoR_.prepare(sampleRate);
        lfoL_.setShape(LfoShape::Sine);
        lfoR_.setShape(LfoShape::Sine);
        toneLpL_.reset();
        toneLpR_.reset();
        toneLpL_.configure(BiquadType::LowPass, sampleRate, 10000.0, 0.707);
        toneLpR_.configure(BiquadType::LowPass, sampleRate, 10000.0, 0.707);
        feedbackL_ = 0.0f;
        feedbackR_ = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        float baseDelaySamples = delayMs_ * 0.001f * static_cast<float>(sampleRate_);
        float depthNorm = depth_ / 100.0f;
        float depthSamples = baseDelaySamples * depthNorm;
        float fb = feedback_ / 100.0f;
        float mixAmt = mix_ / 100.0f;
        float tzPolarity = throughZero_ ? -1.0f : 1.0f;

        lfoL_.setRate(rate_);
        lfoR_.setRate(rate_);
        lfoL_.setPhaseOffset(0.0f);
        lfoR_.setPhaseOffset(stereoOffset_);

        // Update tone filter
        double toneFreqClamped = std::max(200.0, std::min(static_cast<double>(tone_),
                                          sampleRate_ * 0.45));
        toneLpL_.configure(BiquadType::LowPass, sampleRate_, toneFreqClamped, 0.707);
        toneLpR_.configure(BiquadType::LowPass, sampleRate_, toneFreqClamped, 0.707);

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // Apply tone filter to feedback, then tanh saturation
            float fbL = toneLpL_.process(feedbackL_);
            float fbR = toneLpR_.process(feedbackR_);
            fbL = std::tanh(fbL * fb);
            fbR = std::tanh(fbR * fb);

            // Write input + filtered feedback into delay line
            delayL_.write(dryL + fbL);
            delayR_.write(dryR + fbR);

            // LFO modulates delay time
            // LFO range: -1..1
            // Delay sweeps between (baseDelay - depth*baseDelay) and (baseDelay + depth*baseDelay)
            float modL = lfoL_.next();
            float modR = lfoR_.next();
            float readL = baseDelaySamples + modL * depthSamples;
            float readR = baseDelaySamples + modR * depthSamples;

            // Clamp to valid range (minimum 0.5 samples for interpolation)
            readL = std::max(0.5f, readL);
            readR = std::max(0.5f, readR);

            // Cubic interpolated read for smooth modulation
            float wetL = delayL_.readCubic(readL);
            float wetR = delayR_.readCubic(readR);

            // Store raw wet for feedback (before polarity/mix)
            feedbackL_ = wetL;
            feedbackR_ = wetR;

            // Apply through-zero polarity
            wetL *= tzPolarity;
            wetR *= tzPolarity;

            // Mix dry + wet
            left[i]  = dryL * (1.0f - mixAmt) + wetL * mixAmt;
            right[i] = dryR * (1.0f - mixAmt) + wetR * mixAmt;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case DELAY_MS:     delayMs_ = std::max(0.1f, std::min(10.0f, value)); break;
            case DEPTH:        depth_ = std::max(0.0f, std::min(100.0f, value)); break;
            case RATE:         rate_ = std::max(0.01f, std::min(10.0f, value)); break;
            case FEEDBACK:     feedback_ = std::max(-95.0f, std::min(95.0f, value)); break;
            case STEREO:       stereoOffset_ = std::max(0.0f, std::min(360.0f, value)); break;
            case TONE:         tone_ = std::max(200.0f, std::min(20000.0f, value)); break;
            case THROUGH_ZERO: throughZero_ = value > 0.5f; break;
            case MIX:          mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case DELAY_MS:     return delayMs_;
            case DEPTH:        return depth_;
            case RATE:         return rate_;
            case FEEDBACK:     return feedback_;
            case STEREO:       return stereoOffset_;
            case TONE:         return tone_;
            case THROUGH_ZERO: return throughZero_ ? 1.0f : 0.0f;
            case MIX:          return mix_;
            default: return 0.0f;
        }
    }

    void reset() override {
        delayL_.reset();
        delayR_.reset();
        lfoL_.reset(); lfoR_.reset();
        toneLpL_.reset(); toneLpR_.reset();
        feedbackL_ = 0.0f;
        feedbackR_ = 0.0f;
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Flanger"; }
    SnapinType getType() const override { return SnapinType::FLANGER; }

private:
    float delayMs_ = 1.0f;
    float depth_ = 50.0f;
    float rate_ = 0.5f;
    float feedback_ = 30.0f;
    float stereoOffset_ = 0.0f;
    float tone_ = 10000.0f;
    bool throughZero_ = false;
    float mix_ = 50.0f;

    DelayLine delayL_, delayR_;
    Lfo lfoL_, lfoR_;
    Biquad toneLpL_, toneLpR_;
    float feedbackL_ = 0.0f, feedbackR_ = 0.0f;
};
