#pragma once
#include "snapin_processor.h"
#include "envelope_follower.h"
#include <cmath>
#include <algorithm>

// Envelope-based transient and sustain control.
// Dual envelope followers: fast (transient) and slow (sustain).
// Transient component = fast - slow.
class TransientShaperProcessor : public SnapinProcessor {
public:
    enum Params {
        ATTACK_AMT = 0, PUMP, SUSTAIN_AMT, SPEED, CLIP, SIDECHAIN, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;

        // Fast envelope: short attack/release for transient detection
        fastEnvL_.prepare(sampleRate);
        fastEnvR_.prepare(sampleRate);
        fastEnvL_.setMode(DetectionMode::Peak);
        fastEnvR_.setMode(DetectionMode::Peak);
        updateSpeed();

        // Slow envelope: long attack/release for sustain
        slowEnvL_.prepare(sampleRate);
        slowEnvR_.prepare(sampleRate);
        slowEnvL_.setMode(DetectionMode::Peak);
        slowEnvR_.setMode(DetectionMode::Peak);

        pumpEnvL_ = 0.0f;
        pumpEnvR_ = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        float pumpRelease = 1.0f - std::exp(-1.0f / (0.05f * static_cast<float>(sampleRate_)));

        for (int i = 0; i < numFrames; i++) {
            // Get envelopes
            float fastL = fastEnvL_.process(left[i]);
            float fastR = fastEnvR_.process(right[i]);
            float slowL = slowEnvL_.process(left[i]);
            float slowR = slowEnvR_.process(right[i]);

            // Transient component = fast - slow (positive when transient detected)
            float transientL = std::max(0.0f, fastL - slowL);
            float transientR = std::max(0.0f, fastR - slowR);

            // Sustain component approximation
            float sustainL = slowL;
            float sustainR = slowR;

            // Compute gain adjustments
            float attackGain = attackAmt_ / 100.0f;   // -1 to +1
            float sustainGain = sustainAmt_ / 100.0f;  // -1 to +1
            float pumpAmt = pump_ / 100.0f;

            // Transient enhancement/attenuation
            float transGainL = 1.0f;
            float transGainR = 1.0f;
            if (slowL > 1e-6f) {
                transGainL = 1.0f + attackGain * (transientL / slowL);
            }
            if (slowR > 1e-6f) {
                transGainR = 1.0f + attackGain * (transientR / slowR);
            }

            // Pump: duck after transient
            pumpEnvL_ = std::max(pumpEnvL_, transientL);
            pumpEnvR_ = std::max(pumpEnvR_, transientR);
            pumpEnvL_ += pumpRelease * (0.0f - pumpEnvL_);
            pumpEnvR_ += pumpRelease * (0.0f - pumpEnvR_);

            float pumpGainL = 1.0f - pumpAmt * std::min(1.0f, pumpEnvL_ * 2.0f);
            float pumpGainR = 1.0f - pumpAmt * std::min(1.0f, pumpEnvR_ * 2.0f);

            // Sustain enhancement
            float susGainL = 1.0f + sustainGain * 0.5f;
            float susGainR = 1.0f + sustainGain * 0.5f;

            // Combine
            float gainL = transGainL * pumpGainL * susGainL;
            float gainR = transGainR * pumpGainR * susGainR;

            left[i]  *= gainL;
            right[i] *= gainR;

            // Optional hard clip
            if (clip_) {
                left[i]  = std::max(-1.0f, std::min(1.0f, left[i]));
                right[i] = std::max(-1.0f, std::min(1.0f, right[i]));
            }
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case ATTACK_AMT:  attackAmt_ = std::max(-100.0f, std::min(100.0f, value)); break;
            case PUMP:        pump_ = std::max(0.0f, std::min(100.0f, value)); break;
            case SUSTAIN_AMT: sustainAmt_ = std::max(-100.0f, std::min(100.0f, value)); break;
            case SPEED:
                speed_ = std::max(0.0f, std::min(100.0f, value));
                updateSpeed();
                break;
            case CLIP:      clip_ = value > 0.5f; break;
            case SIDECHAIN: sidechain_ = value > 0.5f; break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case ATTACK_AMT:  return attackAmt_;
            case PUMP:        return pump_;
            case SUSTAIN_AMT: return sustainAmt_;
            case SPEED:       return speed_;
            case CLIP:        return clip_ ? 1.0f : 0.0f;
            case SIDECHAIN:   return sidechain_ ? 1.0f : 0.0f;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Transient Shaper"; }
    SnapinType getType() const override { return SnapinType::TRANSIENT_SHAPER; }

private:
    void updateSpeed() {
        // Speed 0 = slow detection, Speed 100 = fast detection
        float fastAttack = 0.1f + (1.0f - speed_ / 100.0f) * 9.9f;   // 0.1–10ms
        float fastRelease = 5.0f + (1.0f - speed_ / 100.0f) * 45.0f;  // 5–50ms
        float slowAttack = 20.0f + (1.0f - speed_ / 100.0f) * 80.0f;  // 20–100ms
        float slowRelease = 100.0f + (1.0f - speed_ / 100.0f) * 400.0f; // 100–500ms

        fastEnvL_.setAttack(fastAttack);
        fastEnvL_.setRelease(fastRelease);
        fastEnvR_.setAttack(fastAttack);
        fastEnvR_.setRelease(fastRelease);
        slowEnvL_.setAttack(slowAttack);
        slowEnvL_.setRelease(slowRelease);
        slowEnvR_.setAttack(slowAttack);
        slowEnvR_.setRelease(slowRelease);
    }

    float attackAmt_ = 0.0f;
    float pump_ = 0.0f;
    float sustainAmt_ = 0.0f;
    float speed_ = 50.0f;
    bool clip_ = false;
    bool sidechain_ = false;

    EnvelopeFollower fastEnvL_, fastEnvR_;
    EnvelopeFollower slowEnvL_, slowEnvR_;
    float pumpEnvL_ = 0.0f, pumpEnvR_ = 0.0f;
};
