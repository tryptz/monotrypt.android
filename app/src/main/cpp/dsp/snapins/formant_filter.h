#pragma once
#include "snapin_processor.h"
#include "biquad.h"
#include <cmath>
#include <algorithm>

// Vowel-shaping dual bandpass filter with 2D vowel selector.
class FormantFilterProcessor : public SnapinProcessor {
public:
    enum Params {
        VOWEL_X = 0, VOWEL_Y, Q_VAL, LOWS, HIGHS, NUM_PARAMS
    };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        for (int i = 0; i < 2; i++) {
            bpL_[i].reset();
            bpR_[i].reset();
        }
        updateFormants();
    }

    void process(float* left, float* right, int numFrames) override {
        for (int i = 0; i < numFrames; i++) {
            float inL = left[i];
            float inR = right[i];

            // Two parallel bandpass filters at formant frequencies
            float f1L = bpL_[0].process(inL);
            float f1R = bpR_[0].process(inR);
            float f2L = bpL_[1].process(inL);
            float f2R = bpR_[1].process(inR);

            float wetL = (f1L + f2L) * 0.5f;
            float wetR = (f1R + f2R) * 0.5f;

            // Low/High pass blending for body/air
            float lowMix = lows_ / 100.0f;
            float highMix = highs_ / 100.0f;
            wetL = wetL + inL * lowMix * 0.3f;
            wetR = wetR + inR * lowMix * 0.3f;

            left[i]  = wetL + inL * highMix * 0.2f;
            right[i] = wetR + inR * highMix * 0.2f;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case VOWEL_X: vowelX_ = std::max(0.0f, std::min(1.0f, value)); updateFormants(); break;
            case VOWEL_Y: vowelY_ = std::max(0.0f, std::min(1.0f, value)); updateFormants(); break;
            case Q_VAL:   q_ = std::max(0.5f, std::min(20.0f, value)); updateFormants(); break;
            case LOWS:    lows_ = std::max(0.0f, std::min(100.0f, value)); break;
            case HIGHS:   highs_ = std::max(0.0f, std::min(100.0f, value)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case VOWEL_X: return vowelX_;
            case VOWEL_Y: return vowelY_;
            case Q_VAL:   return q_;
            case LOWS:    return lows_;
            case HIGHS:   return highs_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Formant Filter"; }
    SnapinType getType() const override { return SnapinType::FORMANT_FILTER; }

private:
    void updateFormants() {
        // Vowel map: interpolate between 5 vowels on 2D plane
        // /a/(800,1200) /e/(400,2200) /i/(300,2800) /o/(500,900) /u/(350,700)
        struct Vowel { float f1, f2; };
        static const Vowel vowels[] = {
            {800, 1200},  // a (center-bottom)
            {400, 2200},  // e (right)
            {300, 2800},  // i (top-right)
            {500, 900},   // o (left)
            {350, 700}    // u (top-left)
        };

        // Simple bilinear interpolation on X/Y
        // X: 0=o/u, 1=e/i; Y: 0=a/o, 1=i/u
        float f1 = vowels[0].f1 * (1.0f - vowelX_) * (1.0f - vowelY_)
                  + vowels[1].f1 * vowelX_ * (1.0f - vowelY_)
                  + vowels[2].f1 * vowelX_ * vowelY_
                  + vowels[4].f1 * (1.0f - vowelX_) * vowelY_;

        float f2 = vowels[0].f2 * (1.0f - vowelX_) * (1.0f - vowelY_)
                  + vowels[1].f2 * vowelX_ * (1.0f - vowelY_)
                  + vowels[2].f2 * vowelX_ * vowelY_
                  + vowels[4].f2 * (1.0f - vowelX_) * vowelY_;

        // Clamp to Nyquist
        float nyq = static_cast<float>(sampleRate_) * 0.45f;
        f1 = std::min(f1, nyq);
        f2 = std::min(f2, nyq);

        bpL_[0].configure(BiquadType::Peaking, sampleRate_, f1, q_, 12.0);
        bpR_[0].configure(BiquadType::Peaking, sampleRate_, f1, q_, 12.0);
        bpL_[1].configure(BiquadType::Peaking, sampleRate_, f2, q_, 12.0);
        bpR_[1].configure(BiquadType::Peaking, sampleRate_, f2, q_, 12.0);
    }

    float vowelX_ = 0.5f;
    float vowelY_ = 0.5f;
    float q_ = 5.0f;
    float lows_ = 0.0f;
    float highs_ = 0.0f;

    Biquad bpL_[2], bpR_[2];
};
