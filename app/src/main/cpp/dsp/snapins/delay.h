#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include "biquad.h"
#include "lfo.h"
#include "envelope_follower.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Modern stereo delay with feedback filtering, modulation, ducking, and ping-pong.
class DelayProcessor : public SnapinProcessor {
public:
    enum Params {
        TIME = 0,       // 1-2000 ms
        FEEDBACK,       // 0-100%
        PING_PONG,      // 0/1
        PAN,            // -100 to 100
        DUCK,           // 0-100%
        FB_LOWCUT,      // 20-2000 Hz (highpass on feedback)
        FB_HICUT,       // 500-20000 Hz (lowpass on feedback)
        MOD_DEPTH,      // 0-100% (LFO modulation on delay time)
        MIX,            // 0-100%
        NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;

        // Max 2 seconds + headroom for modulation at any sample rate
        int maxSamples = static_cast<int>(2.2 * sampleRate) + 64;
        delayL_.prepare(maxSamples);
        delayR_.prepare(maxSamples);

        // Feedback filters
        fbHpL_.reset(); fbHpR_.reset();
        fbLpL_.reset(); fbLpR_.reset();
        configureFeedbackFilters();

        // Modulation LFO (~1 Hz, sine, L/R offset for stereo movement)
        modLfoL_.prepare(sampleRate);
        modLfoR_.prepare(sampleRate);
        modLfoL_.setShape(LfoShape::Sine);
        modLfoR_.setShape(LfoShape::Sine);
        modLfoL_.setRate(0.7f);
        modLfoR_.setRate(0.7f);
        modLfoL_.setPhaseOffset(0.0f);
        modLfoR_.setPhaseOffset(90.0f);

        // Duck envelope
        duckEnv_.prepare(sampleRate);
        duckEnv_.setAttack(1.0f);
        duckEnv_.setRelease(80.0f);
        duckEnv_.setMode(DetectionMode::Peak);

        feedbackL_ = 0.0f;
        feedbackR_ = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        const float baseDelaySamples = timeMs_ * 0.001f * static_cast<float>(sampleRate_);
        const float fb = feedback_ * 0.01f;
        const float duckAmount = duck_ * 0.01f;
        const float m = mix_ * 0.01f;
        const float modDepthSamples = baseDelaySamples * (modDepth_ * 0.01f) * 0.02f; // max ~2% swing
        const float panNorm = (pan_ + 100.0f) / 200.0f; // 0..1
        const float panL = std::cos(panNorm * static_cast<float>(M_PI) * 0.5f);
        const float panR = std::sin(panNorm * static_cast<float>(M_PI) * 0.5f);
        const float maxDelay = 2.0f * static_cast<float>(sampleRate_);

        for (int i = 0; i < numFrames; i++) {
            const float dryL = left[i];
            const float dryR = right[i];

            // Duck: reduce wet when dry signal is loud
            float duckLevel = duckEnv_.process(std::max(std::fabs(dryL), std::fabs(dryR)));
            float duckGain = 1.0f - duckAmount * std::min(1.0f, duckLevel * 4.0f);

            // LFO modulation on delay time
            float modL = modLfoL_.next() * modDepthSamples;
            float modR = modLfoR_.next() * modDepthSamples;
            float delaySamplesL = std::max(1.0f, std::min(baseDelaySamples + modL, maxDelay));
            float delaySamplesR = std::max(1.0f, std::min(baseDelaySamples + modR, maxDelay));

            // Process feedback through filters and soft saturation
            float fbL = feedbackL_;
            float fbR = feedbackR_;

            // Highpass to prevent bass buildup
            fbL = fbHpL_.process(fbL);
            fbR = fbHpR_.process(fbR);

            // Lowpass to tame harsh treble
            fbL = fbLpL_.process(fbL);
            fbR = fbLpR_.process(fbR);

            // Soft saturation (tanh) to prevent runaway
            fbL = std::tanh(fbL);
            fbR = std::tanh(fbR);

            // Write to delay lines
            if (pingPong_) {
                // Ping-pong: cross-feed L->R, R->L
                delayL_.write(dryL + fbR * fb);
                delayR_.write(dryR + fbL * fb);
            } else {
                delayL_.write(dryL + fbL * fb);
                delayR_.write(dryR + fbR * fb);
            }

            // Read with cubic interpolation for smooth time changes
            float wetL = delayL_.readCubic(delaySamplesL);
            float wetR = delayR_.readCubic(delaySamplesR);

            // Store for next iteration feedback
            feedbackL_ = wetL;
            feedbackR_ = wetR;

            // Apply pan to wet signal (equal-power)
            float wetPanL = wetL * panL;
            float wetPanR = wetR * panR;

            // Apply duck and mix
            left[i]  = dryL * (1.0f - m) + wetPanL * m * duckGain;
            right[i] = dryR * (1.0f - m) + wetPanR * m * duckGain;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case TIME:
                timeMs_ = std::max(1.0f, std::min(2000.0f, value));
                break;
            case FEEDBACK:
                feedback_ = std::max(0.0f, std::min(100.0f, value));
                break;
            case PING_PONG:
                pingPong_ = value > 0.5f;
                break;
            case PAN:
                pan_ = std::max(-100.0f, std::min(100.0f, value));
                break;
            case DUCK:
                duck_ = std::max(0.0f, std::min(100.0f, value));
                break;
            case FB_LOWCUT:
                fbLowcut_ = std::max(20.0f, std::min(2000.0f, value));
                configureFeedbackFilters();
                break;
            case FB_HICUT:
                fbHicut_ = std::max(500.0f, std::min(20000.0f, value));
                configureFeedbackFilters();
                break;
            case MOD_DEPTH:
                modDepth_ = std::max(0.0f, std::min(100.0f, value));
                break;
            case MIX:
                mix_ = std::max(0.0f, std::min(100.0f, value));
                break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case TIME:       return timeMs_;
            case FEEDBACK:   return feedback_;
            case PING_PONG:  return pingPong_ ? 1.0f : 0.0f;
            case PAN:        return pan_;
            case DUCK:       return duck_;
            case FB_LOWCUT:  return fbLowcut_;
            case FB_HICUT:   return fbHicut_;
            case MOD_DEPTH:  return modDepth_;
            case MIX:        return mix_;
            default:         return 0.0f;
        }
    }

    void reset() override {
        delayL_.reset();
        delayR_.reset();
        fbHpL_.reset(); fbHpR_.reset();
        fbLpL_.reset(); fbLpR_.reset();
        modLfoL_.reset(); modLfoR_.reset();
        duckEnv_.reset();
        feedbackL_ = 0.0f;
        feedbackR_ = 0.0f;
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Delay"; }
    SnapinType getType() const override { return SnapinType::DELAY; }

private:
    void configureFeedbackFilters() {
        if (sampleRate_ <= 0.0) return;
        fbHpL_.configure(BiquadType::HighPass, sampleRate_, static_cast<double>(fbLowcut_), 0.707);
        fbHpR_.configure(BiquadType::HighPass, sampleRate_, static_cast<double>(fbLowcut_), 0.707);
        fbLpL_.configure(BiquadType::LowPass, sampleRate_, static_cast<double>(fbHicut_), 0.707);
        fbLpR_.configure(BiquadType::LowPass, sampleRate_, static_cast<double>(fbHicut_), 0.707);
    }

    // Parameters
    float timeMs_ = 250.0f;
    float feedback_ = 30.0f;
    bool pingPong_ = false;
    float pan_ = 0.0f;
    float duck_ = 0.0f;
    float fbLowcut_ = 80.0f;
    float fbHicut_ = 8000.0f;
    float modDepth_ = 0.0f;
    float mix_ = 50.0f;

    // DSP state
    DelayLine delayL_, delayR_;
    Biquad fbHpL_, fbHpR_;   // Feedback highpass
    Biquad fbLpL_, fbLpR_;   // Feedback lowpass
    Lfo modLfoL_, modLfoR_;
    EnvelopeFollower duckEnv_;
    float feedbackL_ = 0.0f, feedbackR_ = 0.0f;
};
