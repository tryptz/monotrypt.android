#pragma once
#include "snapin_processor.h"
#include <cmath>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// State-variable filter with internal nonlinearities for coloring.
class NonlinearFilterProcessor : public SnapinProcessor {
public:
    enum Params {
        TYPE = 0, CUTOFF, Q_VAL, DRIVE, MODE, NUM_PARAMS
    };
    // Types: 0=LP, 1=BP, 2=HP, 3=Notch
    // Modes: 0=Clean, 1=tanh, 2=soft-clip, 3=fold, 4=hard-clip

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        icL_[0] = icL_[1] = 0.0f;
        icR_[0] = icR_[1] = 0.0f;
    }

    void process(float* left, float* right, int numFrames) override {
        float g = std::tan(static_cast<float>(M_PI) * cutoff_ / static_cast<float>(sampleRate_));
        float k = 1.0f / std::max(0.1f, q_);
        float driveLin = std::pow(10.0f, driveDb_ / 20.0f);

        float a1 = 1.0f / (1.0f + g * (g + k));
        float a2 = g * a1;
        float a3 = g * a2;

        for (int i = 0; i < numFrames; i++) {
            left[i]  = processSVF(left[i] * driveLin, icL_, g, k, a1, a2, a3);
            right[i] = processSVF(right[i] * driveLin, icR_, g, k, a1, a2, a3);
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case TYPE:   type_ = static_cast<int>(std::max(0.0f, std::min(3.0f, value))); break;
            case CUTOFF: cutoff_ = std::max(20.0f, std::min(20000.0f, value)); break;
            case Q_VAL:  q_ = std::max(0.1f, std::min(20.0f, value)); break;
            case DRIVE:  driveDb_ = std::max(0.0f, std::min(48.0f, value)); break;
            case MODE:   mode_ = static_cast<int>(std::max(0.0f, std::min(4.0f, value))); break;
        }
    }

    float getParameter(int index) const override {
        switch (index) {
            case TYPE:   return static_cast<float>(type_);
            case CUTOFF: return cutoff_;
            case Q_VAL:  return q_;
            case DRIVE:  return driveDb_;
            case MODE:   return static_cast<float>(mode_);
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "Nonlinear Filter"; }
    SnapinType getType() const override { return SnapinType::NONLINEAR_FILTER; }

private:
    float processSVF(float v0, float ic[2], float g, float k,
                     float a1, float a2, float a3) {
        float v3 = v0 - ic[1];
        float v1 = a1 * ic[0] + a2 * v3;
        float v2 = ic[1] + a2 * ic[0] + a3 * v3;

        ic[0] = 2.0f * v1 - ic[0];
        ic[1] = 2.0f * v2 - ic[1];

        // Apply nonlinearity to integrator states
        if (mode_ > 0) {
            ic[0] = applyNonlinearity(ic[0]);
            ic[1] = applyNonlinearity(ic[1]);
        }

        // Output selection
        switch (type_) {
            case 0: return v2;                  // LP
            case 1: return v1;                  // BP
            case 2: return v0 - k * v1 - v2;   // HP
            case 3: return v0 - k * v1;         // Notch
            default: return v2;
        }
    }

    float applyNonlinearity(float x) const {
        switch (mode_) {
            case 1: return std::tanh(x);
            case 2: return x / (1.0f + std::fabs(x));
            case 3: { // fold
                while (x > 1.0f || x < -1.0f) {
                    if (x > 1.0f) x = 2.0f - x;
                    if (x < -1.0f) x = -2.0f - x;
                }
                return x;
            }
            case 4: return std::max(-1.0f, std::min(1.0f, x));
            default: return x;
        }
    }

    int type_ = 0;
    float cutoff_ = 1000.0f;
    float q_ = 0.707f;
    float driveDb_ = 0.0f;
    int mode_ = 0;

    float icL_[2] = {};
    float icR_[2] = {};
};
