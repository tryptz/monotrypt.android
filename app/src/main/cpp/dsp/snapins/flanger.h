#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include "lfo.h"
#include "allpass.h"
#include <cmath>
#include <algorithm>

// Classic flanger with optional barberpole (infinite scroll) mode.
class FlangerProcessor : public SnapinProcessor {
public:
    enum Params {
        DELAY_MS = 0, DEPTH, RATE, SCROLL, OFFSET,
        MOTION, SPREAD, FEEDBACK, MIX, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // Max 10ms + modulation headroom
        int maxSamples = static_cast<int>(0.015 * sampleRate) + 16;
        delayL_.prepare(maxSamples);
        delayR_.prepare(maxSamples);
        lfoL_.prepare(sampleRate);
        lfoR_.prepare(sampleRate);
        lfoL_.setShape(LfoShape::Sine);
        lfoR_.setShape(LfoShape::Sine);
        // Barberpole allpass filters
        for (int i = 0; i < 4; i++) {
            scrollApL_[i].reset();
            scrollApR_[i].reset();
        }
        feedbackL_ = 0.0f;
        feedbackR_ = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        float baseDelay = delayMs_ * 0.001f * static_cast<float>(sampleRate_);
        float depthSamples = baseDelay * (depth_ / 100.0f);
        float fb = feedback_ / 100.0f;

        lfoL_.setRate(rate_);
        lfoR_.setRate(rate_);
        lfoR_.setPhaseOffset(offset_ + spread_ * 0.5f);
        lfoL_.setPhaseOffset(0.0f);

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // Write with feedback
            delayL_.write(dryL + feedbackL_ * fb);
            delayR_.write(dryR + feedbackR_ * fb);

            // LFO modulated read
            float modL = lfoL_.next();
            float modR = lfoR_.next();
            float readL = baseDelay + modL * depthSamples;
            float readR = baseDelay + modR * depthSamples;
            readL = std::max(1.0f, readL);
            readR = std::max(1.0f, readR);

            float wetL = delayL_.readCubic(readL);
            float wetR = delayR_.readCubic(readR);

            // Barberpole scroll mode: cascade allpass for infinite flange illusion
            if (scroll_) {
                float scrollPhase = scrollPhase_;
                for (int s = 0; s < 4; s++) {
                    float coeff = 0.5f * std::sin(scrollPhase + s * 1.5707963f);
                    scrollApL_[s].setCoefficient(coeff);
                    scrollApR_[s].setCoefficient(coeff);
                    wetL = scrollApL_[s].process(wetL);
                    wetR = scrollApR_[s].process(wetR);
                }
                scrollPhase_ += motion_ * 2.0f * 3.14159265f / static_cast<float>(sampleRate_);
                if (scrollPhase_ > 6.28318530f) scrollPhase_ -= 6.28318530f;
            }

            feedbackL_ = wetL;
            feedbackR_ = wetR;

            float m = mix_ / 100.0f;
            left[i]  = dryL * (1.0f - m) + wetL * m;
            right[i] = dryR * (1.0f - m) + wetR * m;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case DELAY_MS: delayMs_ = std::max(0.1f, std::min(10.0f, value)); break;
            case DEPTH:    depth_ = std::max(0.0f, std::min(100.0f, value)); break;
            case RATE:     rate_ = std::max(0.01f, std::min(10.0f, value)); break;
            case SCROLL:   scroll_ = value > 0.5f; break;
            case OFFSET:   offset_ = std::max(0.0f, std::min(360.0f, value)); break;
            case MOTION:   motion_ = std::max(0.0f, std::min(10.0f, value)); break;
            case SPREAD:   spread_ = std::max(0.0f, std::min(100.0f, value)); break;
            case FEEDBACK: feedback_ = std::max(-100.0f, std::min(100.0f, value)); break;
            case MIX:      mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case DELAY_MS: return delayMs_;
            case DEPTH:    return depth_;
            case RATE:     return rate_;
            case SCROLL:   return scroll_ ? 1.0f : 0.0f;
            case OFFSET:   return offset_;
            case MOTION:   return motion_;
            case SPREAD:   return spread_;
            case FEEDBACK: return feedback_;
            case MIX:      return mix_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Flanger"; }
    SnapinType getType() const override { return SnapinType::FLANGER; }

private:
    float delayMs_ = 1.0f;
    float depth_ = 50.0f;
    float rate_ = 0.5f;
    bool scroll_ = false;
    float offset_ = 0.0f;
    float motion_ = 0.0f;
    float spread_ = 0.0f;
    float feedback_ = 30.0f;
    float mix_ = 50.0f;

    DelayLine delayL_, delayR_;
    Lfo lfoL_, lfoR_;
    Allpass scrollApL_[4], scrollApR_[4];
    float feedbackL_ = 0.0f, feedbackR_ = 0.0f;
    float scrollPhase_ = 0.0f;
};
