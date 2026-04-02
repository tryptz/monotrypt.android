#pragma once
#include "snapin_processor.h"
#include "delay_line.h"
#include "biquad.h"
#include "lfo.h"
#include <cmath>
#include <algorithm>
#include <cstring>

// ── Industry-standard algorithmic reverb ────────────────────────────────
//
// Architecture:
//   Pre-delay → Input diffusion (4-stage allpass) → 8-line FDN
//   with proper Hadamard mixing, per-line modulated delay,
//   dual-band damping (low-cut + hi-cut), and freeze mode.
//
// References:
//   - Signalsmith Audio "Let's Write A Reverb" (Hadamard FDN)
//   - Jon Dattorro "Effect Design Part 1" (plate reverb topology)
//   - Sean Costello / Valhalla DSP (modulated FDN principles)
//
class ReverbProcessor : public SnapinProcessor {
public:
    enum Params {
        PRE_DELAY = 0,  // 0-500 ms
        DECAY,          // 0.1-30 s (RT60)
        SIZE,           // 0-100 % (scales delay lengths)
        DAMPING,        // 0-100 % (hi-freq decay)
        DIFFUSION,      // 0-100 % (allpass feedback coefficient)
        MOD_RATE,       // 0.05-5 Hz
        MOD_DEPTH,      // 0-100 %
        TONE,           // 500-20000 Hz (hi-cut on tail)
        LOW_CUT,        // 20-500 Hz (hi-pass on tail)
        EARLY_LATE,     // 0-100 % (early vs late balance)
        WIDTH,          // 0-100 %
        MIX,            // 0-100 %
        NUM_PARAMS
    };

    static constexpr int NUM_DIFFUSERS = 4;
    static constexpr int NUM_LINES = 8;
    static constexpr int MAX_PREDELAY_MS = 500;

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;

        // Pre-delay line
        int maxPreDelaySamples = static_cast<int>(MAX_PREDELAY_MS * 0.001 * sampleRate) + 4;
        preDelayL_.prepare(maxPreDelaySamples);
        preDelayR_.prepare(maxPreDelaySamples);

        // Input diffusion allpass delay lines
        int maxDiffSamples = static_cast<int>(0.05 * sampleRate) + 16;
        for (int d = 0; d < NUM_DIFFUSERS; d++) {
            diffL_[d].prepare(maxDiffSamples);
            diffR_[d].prepare(maxDiffSamples);
        }

        // FDN delay lines (max ~200ms per line for large rooms)
        // Separate L/R delay lines to avoid cubic interpolation crosstalk
        int maxLineSamples = static_cast<int>(0.2 * sampleRate) + 16;
        for (int l = 0; l < NUM_LINES; l++) {
            linesL_[l].prepare(maxLineSamples);
            linesR_[l].prepare(maxLineSamples);

            // LFOs with staggered phases for decorrelation
            modLfos_[l].prepare(sampleRate);
            modLfos_[l].setRate(modRate_);
            modLfos_[l].setShape(LfoShape::Sine);
            modLfos_[l].setPhaseOffset(45.0f * l);  // 0°, 45°, 90°, ...
        }

        // Early reflection taps (delay line shared with FDN input)
        earlyL_.prepare(static_cast<int>(0.1 * sampleRate) + 16);
        earlyR_.prepare(static_cast<int>(0.1 * sampleRate) + 16);

        // Tone filters (per-line for smooth damping)
        for (int l = 0; l < NUM_LINES; l++) {
            hiCutL_[l].reset();
            hiCutR_[l].reset();
            loCutL_[l].reset();
            loCutR_[l].reset();
        }

        updateDelayLengths();
        updateFilters();

        std::memset(lineStateL_, 0, sizeof(lineStateL_));
        std::memset(lineStateR_, 0, sizeof(lineStateR_));
        std::memset(dampStateL_, 0, sizeof(dampStateL_));
        std::memset(dampStateR_, 0, sizeof(dampStateR_));
        freeze_ = false;
    }

    void process(float* left, float* right, int numFrames) override {
        float wet = mix_ / 100.0f;
        float dry = 1.0f - wet;
        float earlyMix = earlyLate_ / 100.0f;
        float lateMix = 1.0f - earlyMix * 0.5f;
        float widthFactor = width_ / 100.0f;
        float diffCoeff = diffusion_ / 100.0f * 0.75f;  // Max 0.75 for stability
        int preDelaySamples = static_cast<int>(preDelayMs_ * 0.001f * sampleRate_);
        preDelaySamples = std::max(0, preDelaySamples);

        // Compute per-line feedback gains from RT60
        float feedbackGains[NUM_LINES];
        for (int l = 0; l < NUM_LINES; l++) {
            float lenSec = static_cast<float>(delayLengths_[l]) / static_cast<float>(sampleRate_);
            feedbackGains[l] = freeze_ ? 1.0f
                : std::pow(10.0f, -3.0f * lenSec / std::max(0.1f, decaySec_));
        }

        // Modulation depth in samples
        float modDepthSamples = (modDepth_ / 100.0f) * 0.008f * static_cast<float>(sampleRate_);

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // ── Pre-delay ──────────────────────────────────────────
            preDelayL_.write(dryL);
            preDelayR_.write(dryR);
            float pdL = (preDelaySamples > 0) ? preDelayL_.read(preDelaySamples) : dryL;
            float pdR = (preDelaySamples > 0) ? preDelayR_.read(preDelaySamples) : dryR;

            // Feed early reflection lines
            earlyL_.write(pdL);
            earlyR_.write(pdR);

            // ── Input diffusion (allpass chain, true stereo) ───────
            float diffL = pdL;
            float diffR = pdR;
            for (int d = 0; d < NUM_DIFFUSERS; d++) {
                int dSamples = diffDelaySamples_[d];
                diffL = processAllpass(diffL_[d], diffL, dSamples, diffCoeff);
                diffR = processAllpass(diffR_[d], diffR, dSamples, diffCoeff);
            }

            // ── Early reflections ──────────────────────────────────
            float erL = 0.0f, erR = 0.0f;
            if (earlyMix > 0.001f) {
                // 6 taps at prime-number-ish delays scaled by size
                static const float erTapMs[6] = { 3.1f, 7.3f, 13.7f, 19.1f, 29.3f, 37.9f };
                float sizeScale = 0.3f + (size_ / 100.0f) * 0.7f;
                for (int e = 0; e < 6; e++) {
                    int tap = std::max(1, static_cast<int>(erTapMs[e] * 0.001f * sampleRate_ * sizeScale));
                    float tL = earlyL_.read(tap);
                    float tR = earlyR_.read(tap);
                    // Alternate pan for spatial width
                    float gL = (e % 2 == 0) ? 1.0f : 0.6f;
                    float gR = (e % 2 == 0) ? 0.6f : 1.0f;
                    erL += tL * gL * (1.0f / 6.0f);
                    erR += tR * gR * (1.0f / 6.0f);
                }
            }

            // ── FDN: Read with modulated delays ────────────────────
            float readsL[NUM_LINES], readsR[NUM_LINES];
            for (int l = 0; l < NUM_LINES; l++) {
                float mod = modLfos_[l].next() * modDepthSamples;
                float effDelay = static_cast<float>(delayLengths_[l]) + mod;
                effDelay = std::max(1.0f, effDelay);
                // Separate L/R delay lines — no interpolation crosstalk
                readsL[l] = linesL_[l].readCubic(effDelay);
                readsR[l] = linesR_[l].readCubic(effDelay);
            }

            // ── Hadamard mixing (Fast Walsh-Hadamard, 8-point) ─────
            float mixedL[NUM_LINES], mixedR[NUM_LINES];
            std::copy(readsL, readsL + NUM_LINES, mixedL);
            std::copy(readsR, readsR + NUM_LINES, mixedR);
            hadamard8(mixedL);
            hadamard8(mixedR);

            // ── Feedback: damping + tone + write back ──────────────
            float inputScale = freeze_ ? 0.0f : (1.0f / static_cast<float>(NUM_LINES));
            for (int l = 0; l < NUM_LINES; l++) {
                float fbL = mixedL[l] * feedbackGains[l];
                float fbR = mixedR[l] * feedbackGains[l];

                // Dual-band damping (hi-cut + lo-cut)
                if (!freeze_) {
                    fbL = hiCutL_[l].process(fbL);
                    fbL = loCutL_[l].process(fbL);
                    fbR = hiCutR_[l].process(fbR);
                    fbR = loCutR_[l].process(fbR);
                }

                // Simple one-pole damping for additional hi-freq decay
                float dampCoeff = damping_ / 100.0f * 0.6f;
                dampStateL_[l] += dampCoeff * (fbL - dampStateL_[l]);
                dampStateR_[l] += dampCoeff * (fbR - dampStateR_[l]);
                fbL = fbL * (1.0f - dampCoeff) + dampStateL_[l] * dampCoeff;
                fbR = fbR * (1.0f - dampCoeff) + dampStateR_[l] * dampCoeff;

                // Write back: feedback + new diffused input
                linesL_[l].write(fbL + diffL * inputScale);
                linesR_[l].write(fbR + diffR * inputScale);

                lineStateL_[l] = fbL;
                lineStateR_[l] = fbR;
            }

            // ── Late reverb output (decorrelated L/R taps) ─────────
            float lateL = (lineStateL_[0] + lineStateL_[2] - lineStateL_[4] + lineStateL_[6]) * 0.25f;
            float lateR = (lineStateR_[1] - lineStateR_[3] + lineStateR_[5] + lineStateR_[7]) * 0.25f;

            // Width control (mid/side)
            float lateM = (lateL + lateR) * 0.5f;
            float lateS = (lateL - lateR) * 0.5f * widthFactor;
            lateL = lateM + lateS;
            lateR = lateM - lateS;

            // ── Combine early + late ───────────────────────────────
            float wetL = erL * earlyMix + lateL * lateMix;
            float wetR = erR * earlyMix + lateR * lateMix;

            left[i]  = dryL * dry + wetL * wet;
            right[i] = dryR * dry + wetR * wet;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case PRE_DELAY: preDelayMs_ = clamp(value, 0.0f, 500.0f); break;
            case DECAY:     decaySec_ = clamp(value, 0.1f, 30.0f); break;
            case SIZE:
                size_ = clamp(value, 0.0f, 100.0f);
                updateDelayLengths();
                break;
            case DAMPING:   damping_ = clamp(value, 0.0f, 100.0f); break;
            case DIFFUSION: diffusion_ = clamp(value, 0.0f, 100.0f); break;
            case MOD_RATE:
                modRate_ = clamp(value, 0.05f, 5.0f);
                for (int l = 0; l < NUM_LINES; l++) modLfos_[l].setRate(modRate_);
                break;
            case MOD_DEPTH: modDepth_ = clamp(value, 0.0f, 100.0f); break;
            case TONE:
                toneHz_ = clamp(value, 500.0f, 20000.0f);
                updateFilters();
                break;
            case LOW_CUT:
                lowCutHz_ = clamp(value, 20.0f, 500.0f);
                updateFilters();
                break;
            case EARLY_LATE: earlyLate_ = clamp(value, 0.0f, 100.0f); break;
            case WIDTH:     width_ = clamp(value, 0.0f, 100.0f); break;
            case MIX:       mix_ = clamp(value, 0.0f, 100.0f); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case PRE_DELAY: return preDelayMs_;
            case DECAY:     return decaySec_;
            case SIZE:      return size_;
            case DAMPING:   return damping_;
            case DIFFUSION: return diffusion_;
            case MOD_RATE:  return modRate_;
            case MOD_DEPTH: return modDepth_;
            case TONE:      return toneHz_;
            case LOW_CUT:   return lowCutHz_;
            case EARLY_LATE:return earlyLate_;
            case WIDTH:     return width_;
            case MIX:       return mix_;
            default:        return 0.0f;
        }
    }

    void reset() override {
        preDelayL_.reset(); preDelayR_.reset();
        for (int d = 0; d < NUM_DIFFUSERS; d++) { diffL_[d].reset(); diffR_[d].reset(); }
        earlyL_.reset(); earlyR_.reset();
        for (int l = 0; l < NUM_LINES; l++) {
            linesL_[l].reset(); linesR_[l].reset();
            modLfos_[l].setPhaseOffset(45.0f * l);
            hiCutL_[l].reset(); hiCutR_[l].reset();
            loCutL_[l].reset(); loCutR_[l].reset();
        }
        std::memset(lineStateL_, 0, sizeof(lineStateL_));
        std::memset(lineStateR_, 0, sizeof(lineStateR_));
        std::memset(dampStateL_, 0, sizeof(dampStateL_));
        std::memset(dampStateR_, 0, sizeof(dampStateR_));
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Reverb"; }
    SnapinType getType() const override { return SnapinType::REVERB; }

private:
    static float clamp(float v, float lo, float hi) { return std::max(lo, std::min(hi, v)); }

    // ── Allpass using delay line ────────────────────────────────────────
    float processAllpass(DelayLine& dl, float input, int delaySamples, float g) {
        float delayed = dl.read(delaySamples);
        float out = -g * input + delayed;
        dl.write(input + g * out);
        return out;
    }

    // ── Fast Walsh-Hadamard transform (in-place, 8-point) ──────────────
    static void hadamard8(float* x) {
        // Stage 1: pairs
        for (int i = 0; i < 4; i++) {
            float a = x[i * 2], b = x[i * 2 + 1];
            x[i * 2]     = a + b;
            x[i * 2 + 1] = a - b;
        }
        // Stage 2: quads
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                float a = x[i * 4 + j], b = x[i * 4 + j + 2];
                x[i * 4 + j]     = a + b;
                x[i * 4 + j + 2] = a - b;
            }
        }
        // Stage 3: full
        for (int j = 0; j < 4; j++) {
            float a = x[j], b = x[j + 4];
            x[j]     = a + b;
            x[j + 4] = a - b;
        }
        // Normalize: 1/sqrt(8)
        for (int i = 0; i < 8; i++) x[i] *= 0.35355339f;
    }

    // ── Update FDN delay lengths from size parameter ────────────────────
    void updateDelayLengths() {
        // Mutually prime base delays (ms) — avoids comb-filtering artifacts
        static const float baseLengths[NUM_LINES] = {
            29.7f, 37.1f, 41.1f, 43.7f, 53.0f, 59.9f, 67.7f, 73.1f
        };
        float sizeScale = 0.3f + (size_ / 100.0f) * 0.7f;
        for (int l = 0; l < NUM_LINES; l++) {
            delayLengths_[l] = std::max(1, static_cast<int>(
                baseLengths[l] * 0.001f * sampleRate_ * sizeScale));
        }

        // Diffuser delays (short, scaled by size)
        static const float diffBaseMs[NUM_DIFFUSERS] = { 1.5f, 3.2f, 5.1f, 7.3f };
        for (int d = 0; d < NUM_DIFFUSERS; d++) {
            diffDelaySamples_[d] = std::max(1, static_cast<int>(
                diffBaseMs[d] * 0.001f * sampleRate_ * sizeScale));
        }
    }

    // ── Update tone-shaping filters ─────────────────────────────────────
    void updateFilters() {
        for (int l = 0; l < NUM_LINES; l++) {
            hiCutL_[l].configure(BiquadType::LowPass, sampleRate_, toneHz_, 0.707);
            hiCutR_[l].configure(BiquadType::LowPass, sampleRate_, toneHz_, 0.707);
            loCutL_[l].configure(BiquadType::HighPass, sampleRate_, lowCutHz_, 0.707);
            loCutR_[l].configure(BiquadType::HighPass, sampleRate_, lowCutHz_, 0.707);
        }
    }

    // ── Parameters ──────────────────────────────────────────────────────
    float preDelayMs_ = 20.0f;
    float decaySec_   = 2.0f;
    float size_       = 50.0f;
    float damping_    = 50.0f;
    float diffusion_  = 70.0f;
    float modRate_    = 0.8f;
    float modDepth_   = 20.0f;
    float toneHz_     = 8000.0f;
    float lowCutHz_   = 80.0f;
    float earlyLate_  = 30.0f;
    float width_      = 100.0f;
    float mix_        = 30.0f;
    bool  freeze_     = false;

    // ── DSP state ───────────────────────────────────────────────────────
    DelayLine preDelayL_, preDelayR_;
    DelayLine diffL_[NUM_DIFFUSERS], diffR_[NUM_DIFFUSERS];
    int diffDelaySamples_[NUM_DIFFUSERS] = {};

    DelayLine earlyL_, earlyR_;

    DelayLine linesL_[NUM_LINES];
    DelayLine linesR_[NUM_LINES];
    int delayLengths_[NUM_LINES] = {};
    Lfo modLfos_[NUM_LINES];

    Biquad hiCutL_[NUM_LINES], hiCutR_[NUM_LINES];
    Biquad loCutL_[NUM_LINES], loCutR_[NUM_LINES];

    float dampStateL_[NUM_LINES] = {};
    float dampStateR_[NUM_LINES] = {};
    float lineStateL_[NUM_LINES] = {};
    float lineStateR_[NUM_LINES] = {};
};
