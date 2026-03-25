#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include "allpass.h"
#include "lfo.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Multi-voice unison with allpass phase modulation for smooth detuning.
// Richer than chorus due to allpass chains providing non-metallic character.
class EnsembleProcessor : public SnapinProcessor {
public:
    enum Params {
        VOICES = 0, DETUNE, SPREAD, MIX, MOTION, NUM_PARAMS
    };

    static constexpr int MAX_VOICES = 8;
    static constexpr int AP_STAGES = 4;  // Allpass stages per voice

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;

        int maxDelay = static_cast<int>(0.03 * sampleRate) + 16;
        for (int v = 0; v < MAX_VOICES; v++) {
            delayL_[v].prepare(maxDelay);
            delayR_[v].prepare(maxDelay);
            lfo_[v].prepare(sampleRate);
            lfo_[v].setShape(LfoShape::Sine);
            for (int s = 0; s < AP_STAGES; s++) {
                apL_[v][s].reset();
                apR_[v][s].reset();
            }
        }
        updateMotion();
    }

    void process(float* left, float* right, int numFrames) override {
        int voices = std::max(2, std::min(MAX_VOICES, voices_));
        float detuneAmount = detune_ / 100.0f;
        float spreadAmount = spread_ / 100.0f;
        float mixWet = mix_ / 100.0f;

        // Delay modulation depth in samples (~1-15ms range)
        float depthSamples = detuneAmount * 0.015f * static_cast<float>(sampleRate_);

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];
            float wetL = 0.0f;
            float wetR = 0.0f;

            for (int v = 0; v < voices; v++) {
                delayL_[v].write(dryL);
                delayR_[v].write(dryR);

                float mod = lfo_[v].next();
                float baseDel = 5.0f + depthSamples;  // ~5 samples base
                float readPos = baseDel + mod * depthSamples;
                readPos = std::max(1.0f, readPos);

                float tapL = delayL_[v].readCubic(readPos);
                float tapR = delayR_[v].readCubic(readPos);

                // Allpass phase modulation for richer character
                for (int s = 0; s < AP_STAGES; s++) {
                    float coeff = 0.3f + 0.1f * static_cast<float>(s);
                    apL_[v][s].setCoefficient(coeff);
                    apR_[v][s].setCoefficient(coeff);
                    tapL = apL_[v][s].process(tapL);
                    tapR = apR_[v][s].process(tapR);
                }

                // Stereo spread
                float pan = spreadAmount * (static_cast<float>(v) / static_cast<float>(voices - 1) - 0.5f);
                float panNorm = (pan + 0.5f);
                float gL = std::cos(panNorm * static_cast<float>(M_PI) * 0.5f);
                float gR = std::sin(panNorm * static_cast<float>(M_PI) * 0.5f);

                wetL += tapL * gL;
                wetR += tapR * gR;
            }

            float norm = 1.0f / static_cast<float>(voices);
            wetL *= norm;
            wetR *= norm;

            left[i]  = dryL * (1.0f - mixWet) + wetL * mixWet;
            right[i] = dryR * (1.0f - mixWet) + wetR * mixWet;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case VOICES:
                voices_ = static_cast<int>(std::max(2.0f, std::min(8.0f, value)));
                updateMotion();
                break;
            case DETUNE: detune_ = std::max(0.0f, std::min(100.0f, value)); break;
            case SPREAD: spread_ = std::max(0.0f, std::min(100.0f, value)); break;
            case MIX:    mix_ = std::max(0.0f, std::min(100.0f, value)); break;
            case MOTION:
                motion_ = static_cast<int>(std::max(0.0f, std::min(2.0f, value)));
                updateMotion();
                break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case VOICES: return static_cast<float>(voices_);
            case DETUNE: return detune_;
            case SPREAD: return spread_;
            case MIX:    return mix_;
            case MOTION: return static_cast<float>(motion_);
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Ensemble"; }
    SnapinType getType() const override { return SnapinType::ENSEMBLE; }

private:
    void updateMotion() {
        // Motion modes select different LFO rate/phase patterns
        float baseRate;
        switch (motion_) {
            case 0: baseRate = 0.5f; break;   // A: slow
            case 1: baseRate = 1.2f; break;   // B: medium
            case 2: baseRate = 2.5f; break;   // C: fast
            default: baseRate = 0.5f;
        }
        for (int v = 0; v < MAX_VOICES; v++) {
            // Slightly different rate per voice for organic feel
            float rateOffset = 1.0f + 0.05f * static_cast<float>(v);
            lfo_[v].setRate(baseRate * rateOffset);
            float phase = 360.0f * static_cast<float>(v) / static_cast<float>(std::max(2, voices_));
            lfo_[v].setPhaseOffset(phase);
        }
    }

    int voices_ = 4;
    float detune_ = 50.0f;
    float spread_ = 80.0f;
    float mix_ = 50.0f;
    int motion_ = 0;

    DelayLine delayL_[MAX_VOICES], delayR_[MAX_VOICES];
    Lfo lfo_[MAX_VOICES];
    Allpass apL_[MAX_VOICES][AP_STAGES], apR_[MAX_VOICES][AP_STAGES];
};
