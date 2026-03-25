#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include "envelope_follower.h"
#include <cmath>
#include <algorithm>

// Tempo-syncable delay with feedback, ducking, and ping-pong.
class DelayProcessor : public SnapinProcessor {
public:
    enum Params {
        TIME_MS = 0, SYNC, FEEDBACK, PAN, PING_PONG,
        DUCK, MIX, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        // Max 2 seconds at any sample rate
        int maxSamples = static_cast<int>(2.0 * sampleRate) + 16;
        delayL_.prepare(maxSamples);
        delayR_.prepare(maxSamples);
        duckEnv_.prepare(sampleRate);
        duckEnv_.setAttack(1.0f);
        duckEnv_.setRelease(50.0f);
        duckEnv_.setMode(DetectionMode::Peak);
        feedbackL_ = 0.0f;
        feedbackR_ = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        float delaySamples = timeMs_ * 0.001f * static_cast<float>(sampleRate_);
        delaySamples = std::max(1.0f, std::min(delaySamples, 2.0f * static_cast<float>(sampleRate_)));
        float fb = feedback_ / 100.0f;
        float duckAmount = duck_ / 100.0f;
        float m = mix_ / 100.0f;
        float panNorm = (pan_ + 100.0f) / 200.0f;  // 0..1

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // Duck: reduce wet when dry is loud
            float duckLevel = duckEnv_.process(std::max(std::fabs(dryL), std::fabs(dryR)));
            float duckGain = 1.0f - duckAmount * std::min(1.0f, duckLevel * 4.0f);

            // Write to delay with feedback
            if (pingPong_) {
                // Ping-pong: cross-feed L/R
                delayL_.write(dryL + feedbackR_ * fb);
                delayR_.write(dryR + feedbackL_ * fb);
            } else {
                delayL_.write(dryL + feedbackL_ * fb);
                delayR_.write(dryR + feedbackR_ * fb);
            }

            // Read
            float wetL = delayL_.readCubic(delaySamples);
            float wetR = delayR_.readCubic(delaySamples);

            // Store for next iteration feedback
            feedbackL_ = wetL;
            feedbackR_ = wetR;

            // Apply pan to wet signal
            float wetPanL = wetL * std::cos(panNorm * 1.5707963f);
            float wetPanR = wetR * std::sin(panNorm * 1.5707963f);

            // Apply duck and mix
            left[i]  = dryL * (1.0f - m) + wetPanL * m * duckGain;
            right[i] = dryR * (1.0f - m) + wetPanR * m * duckGain;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case TIME_MS:   timeMs_ = std::max(1.0f, std::min(2000.0f, value)); break;
            case SYNC:      sync_ = value > 0.5f; break;
            case FEEDBACK:  feedback_ = std::max(0.0f, std::min(100.0f, value)); break;
            case PAN:       pan_ = std::max(-100.0f, std::min(100.0f, value)); break;
            case PING_PONG: pingPong_ = value > 0.5f; break;
            case DUCK:      duck_ = std::max(0.0f, std::min(100.0f, value)); break;
            case MIX:       mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case TIME_MS:   return timeMs_;
            case SYNC:      return sync_ ? 1.0f : 0.0f;
            case FEEDBACK:  return feedback_;
            case PAN:       return pan_;
            case PING_PONG: return pingPong_ ? 1.0f : 0.0f;
            case DUCK:      return duck_;
            case MIX:       return mix_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Delay"; }
    SnapinType getType() const override { return SnapinType::DELAY; }

private:
    float timeMs_ = 250.0f;
    bool sync_ = false;
    float feedback_ = 30.0f;
    float pan_ = 0.0f;
    bool pingPong_ = false;
    float duck_ = 0.0f;
    float mix_ = 50.0f;

    DelayLine delayL_, delayR_;
    EnvelopeFollower duckEnv_;
    float feedbackL_ = 0.0f, feedbackR_ = 0.0f;
};
