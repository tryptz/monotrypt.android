#pragma once
#include "allpass.h"

// Hilbert transform via allpass pair method.
// Produces analytic signal (real + imaginary) from real input.
// Uses 4 cascaded allpass filters per path for ~90° phase shift.
class HilbertTransform {
public:
    void prepare(double /*sampleRate*/) {
        // Allpass coefficients optimized for wideband 90° phase difference
        // Path A coefficients
        static const float coeffsA[] = {0.6923878f, 0.9360654322959f, 0.9882295226860f, 0.9987488452737f};
        // Path B coefficients
        static const float coeffsB[] = {0.4021921162426f, 0.8561710882420f, 0.9722909545651f, 0.9952884791278f};

        for (int i = 0; i < 4; i++) {
            apA_[i].setCoefficient(coeffsA[i]);
            apB_[i].setCoefficient(coeffsB[i]);
        }
    }

    void reset() {
        for (int i = 0; i < 4; i++) {
            apA_[i].reset();
            apB_[i].reset();
        }
        prevIn_ = 0.0f;
    }

    // Returns real (in-phase) and imaginary (quadrature) components
    void process(float in, float& outReal, float& outImag) {
        // Path A: process current sample
        float a = in;
        for (int i = 0; i < 4; i++) a = apA_[i].process(a);

        // Path B: process previous sample (one-sample delay)
        float b = prevIn_;
        for (int i = 0; i < 4; i++) b = apB_[i].process(b);

        prevIn_ = in;
        outReal = a;
        outImag = b;
    }

private:
    Allpass apA_[4];
    Allpass apB_[4];
    float prevIn_ = 0.0f;
};
