#pragma once
#include "snapin_processor.h"
#include <cmath>
#include <algorithm>
#include <cstring>

// Rhythmic volume sequencer with programmable patterns and ADSR.
class TranceGateProcessor : public SnapinProcessor {
public:
    enum Params {
        PATTERN = 0, LENGTH, ATTACK, DECAY, SUSTAIN, RELEASE,
        MIX, RESOLUTION, NUM_PARAMS
    };

    static constexpr int MAX_STEPS = 32;
    static constexpr int NUM_PATTERNS = 8;

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        stepPos_ = 0.0f;
        envValue_ = 0.0f;
        gateOpen_ = false;
        initDefaultPatterns();
    }

    void process(float* left, float* right, int numFrames) override {
        // Steps per second based on BPM and resolution
        float bpm = 120.0f;  // Default BPM (could be synced from metadata)
        float beatsPerSec = bpm / 60.0f;
        float stepsPerBeat;
        switch (resolution_) {
            case 0: stepsPerBeat = 1.0f; break;     // 1/4
            case 1: stepsPerBeat = 2.0f; break;     // 1/8
            case 2: stepsPerBeat = 4.0f; break;     // 1/16
            case 3: stepsPerBeat = 8.0f; break;     // 1/32
            default: stepsPerBeat = 4.0f;
        }
        float stepsPerSec = beatsPerSec * stepsPerBeat;
        float stepInc = stepsPerSec / static_cast<float>(sampleRate_);

        float atkCoeff = (attackMs_ <= 0.1f) ? 1.0f
            : 1.0f - std::exp(-1.0f / (attackMs_ * 0.001f * static_cast<float>(sampleRate_)));
        float decCoeff = (decayMs_ <= 0.1f) ? 1.0f
            : 1.0f - std::exp(-1.0f / (decayMs_ * 0.001f * static_cast<float>(sampleRate_)));
        float relCoeff = (releaseMs_ <= 0.1f) ? 1.0f
            : 1.0f - std::exp(-1.0f / (releaseMs_ * 0.001f * static_cast<float>(sampleRate_)));
        float susLevel = sustain_ / 100.0f;
        float m = mix_ / 100.0f;

        int len = std::max(1, std::min(MAX_STEPS, length_));
        int patIdx = std::max(0, std::min(NUM_PATTERNS - 1, pattern_));

        for (int i = 0; i < numFrames; i++) {
            // Current step
            int step = static_cast<int>(stepPos_) % len;
            bool stepOn = patterns_[patIdx][step];

            // ADSR envelope
            float target;
            if (stepOn) {
                if (envValue_ < susLevel) {
                    // Attack phase
                    target = 1.0f;
                    envValue_ += atkCoeff * (target - envValue_);
                } else {
                    // Decay/Sustain phase
                    target = susLevel;
                    envValue_ += decCoeff * (target - envValue_);
                }
                gateOpen_ = true;
            } else {
                // Release
                target = 0.0f;
                envValue_ += relCoeff * (target - envValue_);
                gateOpen_ = false;
            }

            float gain = std::max(0.0f, std::min(1.0f, envValue_));
            float wetGain = 1.0f * (1.0f - m) + gain * m;
            left[i] *= wetGain;
            right[i] *= wetGain;

            stepPos_ += stepInc;
            if (stepPos_ >= static_cast<float>(len)) {
                stepPos_ -= static_cast<float>(len);
            }
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case PATTERN:    pattern_ = static_cast<int>(std::max(0.0f, std::min(7.0f, value))); break;
            case LENGTH:     length_ = static_cast<int>(std::max(1.0f, std::min(32.0f, value))); break;
            case ATTACK:     attackMs_ = std::max(0.1f, std::min(100.0f, value)); break;
            case DECAY:      decayMs_ = std::max(0.1f, std::min(500.0f, value)); break;
            case SUSTAIN:    sustain_ = std::max(0.0f, std::min(100.0f, value)); break;
            case RELEASE:    releaseMs_ = std::max(0.1f, std::min(500.0f, value)); break;
            case MIX:        mix_ = std::max(0.0f, std::min(100.0f, value)); break;
            case RESOLUTION: resolution_ = static_cast<int>(std::max(0.0f, std::min(3.0f, value))); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case PATTERN:    return static_cast<float>(pattern_);
            case LENGTH:     return static_cast<float>(length_);
            case ATTACK:     return attackMs_;
            case DECAY:      return decayMs_;
            case SUSTAIN:    return sustain_;
            case RELEASE:    return releaseMs_;
            case MIX:        return mix_;
            case RESOLUTION: return static_cast<float>(resolution_);
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Trance Gate"; }
    SnapinType getType() const override { return SnapinType::TRANCE_GATE; }

private:
    void initDefaultPatterns() {
        std::memset(patterns_, 0, sizeof(patterns_));
        // Pattern 0: four-on-the-floor
        for (int s = 0; s < 16; s += 4) patterns_[0][s] = true;
        // Pattern 1: offbeat
        for (int s = 2; s < 16; s += 4) patterns_[1][s] = true;
        // Pattern 2: 8th notes
        for (int s = 0; s < 16; s += 2) patterns_[2][s] = true;
        // Pattern 3: syncopated
        patterns_[3][0]=true; patterns_[3][3]=true; patterns_[3][6]=true;
        patterns_[3][8]=true; patterns_[3][11]=true; patterns_[3][14]=true;
        // Pattern 4: dotted
        patterns_[4][0]=true; patterns_[4][3]=true; patterns_[4][6]=true;
        patterns_[4][9]=true; patterns_[4][12]=true; patterns_[4][15]=true;
        // Pattern 5: buildup
        for (int s = 0; s < 16; s++) patterns_[5][s] = (s >= 8);
        // Pattern 6: stutter
        patterns_[6][0]=true; patterns_[6][1]=true; patterns_[6][4]=true;
        patterns_[6][5]=true; patterns_[6][8]=true; patterns_[6][9]=true;
        patterns_[6][12]=true; patterns_[6][13]=true;
        // Pattern 7: all on
        for (int s = 0; s < 32; s++) patterns_[7][s] = true;
    }

    int pattern_ = 0;
    int length_ = 16;
    float attackMs_ = 1.0f;
    float decayMs_ = 50.0f;
    float sustain_ = 100.0f;
    float releaseMs_ = 10.0f;
    float mix_ = 100.0f;
    int resolution_ = 2;  // 0=1/4, 1=1/8, 2=1/16, 3=1/32

    bool patterns_[NUM_PATTERNS][MAX_STEPS];
    float stepPos_ = 0.0f;
    float envValue_ = 0.0f;
    bool gateOpen_ = false;
};
