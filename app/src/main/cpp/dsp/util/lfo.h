#pragma once
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

enum class LfoShape { Sine, Triangle, Saw };

// Simple LFO oscillator with phase offset.
class Lfo {
public:
    void prepare(double sampleRate) {
        sampleRate_ = sampleRate;
    }

    void setRate(float hz) { rate_ = hz; }
    void setShape(LfoShape shape) { shape_ = shape; }
    void setPhaseOffset(float degrees) { phaseOffset_ = degrees / 360.0f; }

    void reset() { phase_ = 0.0f; }

    float next() {
        float p = phase_ + phaseOffset_;
        p -= std::floor(p);  // wrap to [0, 1)

        float out;
        switch (shape_) {
            case LfoShape::Sine:
                out = std::sin(2.0f * static_cast<float>(M_PI) * p);
                break;
            case LfoShape::Triangle:
                out = 4.0f * std::fabs(p - 0.5f) - 1.0f;
                break;
            case LfoShape::Saw:
                out = 2.0f * p - 1.0f;
                break;
            default:
                out = 0.0f;
        }

        phase_ += rate_ / static_cast<float>(sampleRate_);
        phase_ -= std::floor(phase_);

        return out;
    }

    float getPhase() const { return phase_; }

private:
    double sampleRate_ = 44100.0;
    float rate_ = 1.0f;
    float phase_ = 0.0f;
    float phaseOffset_ = 0.0f;
    LfoShape shape_ = LfoShape::Sine;
};
