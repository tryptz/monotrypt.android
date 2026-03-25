#pragma once
#include <cmath>

// One-pole exponential parameter smoother.
// Default smoothing time ~5ms. Call setTarget() from UI thread,
// next() from audio thread.
class ParameterSmoother {
public:
    ParameterSmoother(float initialValue = 0.0f)
        : current_(initialValue), target_(initialValue) {}

    void prepare(double sampleRate, double smoothTimeMs = 5.0) {
        if (smoothTimeMs <= 0.0 || sampleRate <= 0.0) {
            coeff_ = 1.0f;
        } else {
            coeff_ = 1.0f - std::exp(-1.0 / (smoothTimeMs * 0.001 * sampleRate));
        }
    }

    void setTarget(float v) { target_ = v; }
    float getTarget() const { return target_; }

    float next() {
        current_ += coeff_ * (target_ - current_);
        return current_;
    }

    void reset(float v) {
        current_ = v;
        target_ = v;
    }

    bool isSmoothing() const {
        return std::fabs(current_ - target_) > 1e-6f;
    }

private:
    float current_ = 0.0f;
    float target_ = 0.0f;
    float coeff_ = 1.0f;
};
