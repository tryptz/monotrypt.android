#pragma once
#include "snapin_processor.h"
#include "allpass.h"
#include "delay_line.h"
#include <cmath>
#include <algorithm>
#include <cstring>

// Algorithmic reverb: 4 allpass diffusers → 8-line FDN with damping.
class ReverbProcessor : public SnapinProcessor {
public:
    enum Params {
        DECAY = 0, DAMPEN, SIZE, WIDTH, EARLY, MIX, NUM_PARAMS
    };

    static constexpr int NUM_DIFFUSERS = 4;
    static constexpr int NUM_LINES = 8;

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;

        // Prime number delay lengths for FDN (scaled by size)
        updateDelayLengths();

        // Diffusers: short allpass delays
        for (int d = 0; d < NUM_DIFFUSERS; d++) {
            diffuserL_[d].prepare(static_cast<int>(0.01 * sampleRate) + 16);
            diffuserR_[d].prepare(static_cast<int>(0.01 * sampleRate) + 16);
        }

        // FDN delay lines
        for (int l = 0; l < NUM_LINES; l++) {
            lines_[l].prepare(static_cast<int>(0.15 * sampleRate) + 16);
            dampState_[l] = 0.0f;
        }

        std::memset(lineState_, 0, sizeof(lineState_));
    }

    void process(float* left, float* right, int numFrames) override {
        float m = mix_ / 100.0f;
        float earlyMix = early_ / 100.0f;
        float widthFactor = width_ / 100.0f;

        // Compute feedback gains from RT60
        float feedbackGains[NUM_LINES];
        for (int l = 0; l < NUM_LINES; l++) {
            float lenSec = static_cast<float>(delayLengths_[l]) / static_cast<float>(sampleRate_);
            feedbackGains[l] = std::pow(10.0f, -3.0f * lenSec / std::max(0.1f, decaySec_));
        }

        // Damping coefficient
        float dampCoeff = dampen_ / 100.0f * 0.7f;

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];
            float input = (dryL + dryR) * 0.5f;

            // Input diffusion via allpass chain
            float diffused = input;
            for (int d = 0; d < NUM_DIFFUSERS; d++) {
                int diffDelay = static_cast<int>((d + 1) * 0.002f * sampleRate_ * (size_ / 100.0f + 0.3f));
                diffDelay = std::max(1, diffDelay);
                diffuserL_[d].write(diffused);
                float delayed = diffuserL_[d].read(diffDelay);
                float out = -0.6f * diffused + delayed;
                diffuserL_[d].write(diffused + 0.6f * out);
                diffused = out;
            }

            // Early reflections (diffused input with short taps)
            float earlyL = 0.0f, earlyR = 0.0f;
            if (earlyMix > 0.0f) {
                for (int e = 0; e < 4; e++) {
                    int tap = static_cast<int>((e + 1) * 0.005f * sampleRate_ * (size_ / 100.0f + 0.2f));
                    float earlyTap = diffuserL_[e % NUM_DIFFUSERS].read(std::max(1, tap));
                    earlyL += earlyTap * ((e & 1) ? 0.7f : 1.0f);
                    earlyR += earlyTap * ((e & 1) ? 1.0f : 0.7f);
                }
                earlyL *= 0.25f;
                earlyR *= 0.25f;
            }

            // FDN: read from all delay lines
            float reads[NUM_LINES];
            for (int l = 0; l < NUM_LINES; l++) {
                reads[l] = lines_[l].read(delayLengths_[l]);
            }

            // Hadamard-like mixing (simplified: pairwise sum/difference)
            float mixed[NUM_LINES];
            for (int l = 0; l < NUM_LINES; l += 2) {
                mixed[l]     = (reads[l] + reads[l + 1]) * 0.7071067f;
                mixed[l + 1] = (reads[l] - reads[l + 1]) * 0.7071067f;
            }

            // Apply feedback, damping, and write back
            for (int l = 0; l < NUM_LINES; l++) {
                // One-pole LPF damping
                dampState_[l] += dampCoeff * (mixed[l] * feedbackGains[l] - dampState_[l]);
                float fbSample = mixed[l] * feedbackGains[l] * (1.0f - dampCoeff) + dampState_[l];

                // Inject diffused input
                lines_[l].write(fbSample + diffused * 0.25f);
                lineState_[l] = fbSample;
            }

            // Decorrelated L/R output taps
            float lateL = (lineState_[0] + lineState_[2] + lineState_[4] + lineState_[6]) * 0.25f;
            float lateR = (lineState_[1] + lineState_[3] + lineState_[5] + lineState_[7]) * 0.25f;

            // Width control
            float lateM = (lateL + lateR) * 0.5f;
            float lateS = (lateL - lateR) * 0.5f * widthFactor;
            lateL = lateM + lateS;
            lateR = lateM - lateS;

            // Combine early + late
            float wetL = earlyL * earlyMix + lateL * (1.0f - earlyMix * 0.5f);
            float wetR = earlyR * earlyMix + lateR * (1.0f - earlyMix * 0.5f);

            left[i]  = dryL * (1.0f - m) + wetL * m;
            right[i] = dryR * (1.0f - m) + wetR * m;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case DECAY:  decaySec_ = std::max(0.1f, std::min(30.0f, value)); break;
            case DAMPEN: dampen_ = std::max(0.0f, std::min(100.0f, value)); break;
            case SIZE:
                size_ = std::max(0.0f, std::min(100.0f, value));
                updateDelayLengths();
                break;
            case WIDTH: width_ = std::max(0.0f, std::min(100.0f, value)); break;
            case EARLY: early_ = std::max(0.0f, std::min(100.0f, value)); break;
            case MIX:   mix_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case DECAY:  return decaySec_;
            case DAMPEN: return dampen_;
            case SIZE:   return size_;
            case WIDTH:  return width_;
            case EARLY:  return early_;
            case MIX:    return mix_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Reverb"; }
    SnapinType getType() const override { return SnapinType::REVERB; }

private:
    void updateDelayLengths() {
        // Prime number base lengths (in ms) scaled by size
        static const float baseLengths[NUM_LINES] = {
            29.7f, 37.1f, 41.1f, 43.7f, 53.0f, 59.9f, 67.7f, 73.1f
        };
        float sizeScale = 0.3f + (size_ / 100.0f) * 0.7f;
        for (int l = 0; l < NUM_LINES; l++) {
            delayLengths_[l] = static_cast<int>(baseLengths[l] * 0.001f * sampleRate_ * sizeScale);
            delayLengths_[l] = std::max(1, delayLengths_[l]);
        }
    }

    float decaySec_ = 2.0f;
    float dampen_ = 50.0f;
    float size_ = 50.0f;
    float width_ = 100.0f;
    float early_ = 50.0f;
    float mix_ = 30.0f;

    DelayLine diffuserL_[NUM_DIFFUSERS], diffuserR_[NUM_DIFFUSERS];
    DelayLine lines_[NUM_LINES];
    int delayLengths_[NUM_LINES] = {};
    float dampState_[NUM_LINES] = {};
    float lineState_[NUM_LINES] = {};
};
