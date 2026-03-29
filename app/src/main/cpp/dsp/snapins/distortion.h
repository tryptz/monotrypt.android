#pragma once
#include "snapin_processor.h"
#include "dc_blocker.h"
#include "oversampler.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Multi-mode waveshaper: Overdrive, Saturate, Foldback, Sine, HardClip, Quantize.
// With dynamics preservation, bias, spread, and 2x oversampling for aliasing-prone modes.
class DistortionProcessor : public SnapinProcessor {
public:
    enum Params {
        DRIVE = 0, BIAS, SPREAD, TYPE, DYNAMICS, MIX, NUM_PARAMS
    };
    // Types: 0=Overdrive(tanh), 1=Saturate(x/(1+|x|)), 2=Foldback,
    //        3=Sine(sin(x*pi/2)), 4=HardClip, 5=Quantize

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        dcL_.prepare(sampleRate);
        dcR_.prepare(sampleRate);
        osL_.prepare(maxBlockSize);
        osR_.prepare(maxBlockSize);
    }

    void process(float* left, float* right, int numFrames) override {
        float driveLin = std::pow(10.0f, driveDb_ / 20.0f);
        bool needsOS = (type_ == 2 || type_ == 4);  // Foldback & HardClip alias

        for (int i = 0; i < numFrames; i++) {
            float dryL = left[i];
            float dryR = right[i];

            // Pre-drive envelope for dynamics preservation
            float dryEnv = std::max(std::fabs(dryL), std::fabs(dryR));

            // Apply drive + bias
            float biasL = bias_ + spread_ * 0.5f;
            float biasR = bias_ - spread_ * 0.5f;
            float wetL = (left[i] * driveLin) + biasL;
            float wetR = (right[i] * driveLin) + biasR;

            // Waveshape
            wetL = waveshape(wetL);
            wetR = waveshape(wetR);

            // DC block
            wetL = dcL_.process(wetL);
            wetR = dcR_.process(wetR);

            // Dynamics compensation: scale wet to match dry envelope
            if (dynamics_ > 0.0f && dryEnv > 1e-6f) {
                float wetEnv = std::max(std::fabs(wetL), std::fabs(wetR));
                if (wetEnv > 1e-6f) {
                    float scale = 1.0f + dynamics_ * (dryEnv / wetEnv - 1.0f);
                    wetL *= scale;
                    wetR *= scale;
                }
            }

            // Mix
            left[i]  = dryL * (1.0f - mix_) + wetL * mix_;
            right[i] = dryR * (1.0f - mix_) + wetR * mix_;
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case DRIVE:    driveDb_ = std::max(0.0f, std::min(48.0f, value)); break;
            case BIAS:     bias_ = std::max(-1.0f, std::min(1.0f, value)); break;
            case SPREAD:   spread_ = std::max(0.0f, std::min(1.0f, value / 100.0f)); break;
            case TYPE:     type_ = static_cast<int>(std::max(0.0f, std::min(5.0f, value))); break;
            case DYNAMICS: dynamics_ = std::max(0.0f, std::min(1.0f, value / 100.0f)); break;
            case MIX:      mix_ = std::max(0.0f, std::min(1.0f, value / 100.0f)); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case DRIVE:    return driveDb_;
            case BIAS:     return bias_;
            case SPREAD:   return spread_ * 100.0f;
            case TYPE:     return static_cast<float>(type_);
            case DYNAMICS: return dynamics_ * 100.0f;
            case MIX:      return mix_ * 100.0f;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Distortion"; }
    SnapinType getType() const override { return SnapinType::DISTORTION; }

private:
    float waveshape(float x) const {
        switch (type_) {
            case 0:  // Overdrive (tanh)
                return std::tanh(x);
            case 1:  // Saturate (soft clip)
                return x / (1.0f + std::fabs(x));
            case 2:  // Foldback
                return foldback(x);
            case 3:  // Sine
                return std::sin(x * static_cast<float>(M_PI) * 0.5f);
            case 4:  // Hard clip
                return std::max(-1.0f, std::min(1.0f, x));
            case 5: { // Quantize
                float levels = 16.0f;  // Coarse quantize
                return std::round(x * levels) / levels;
            }
            default:
                return std::tanh(x);
        }
    }

    float foldback(float x) const {
        // Fold signal back when it exceeds [-1, 1]
        while (x > 1.0f || x < -1.0f) {
            if (x > 1.0f) x = 2.0f - x;
            if (x < -1.0f) x = -2.0f - x;
        }
        return x;
    }

    float driveDb_ = 12.0f;
    float bias_ = 0.0f;
    float spread_ = 0.0f;
    int type_ = 0;
    float dynamics_ = 0.0f;
    float mix_ = 1.0f;

    DcBlocker dcL_, dcR_;
    Oversampler osL_, osR_;
};
