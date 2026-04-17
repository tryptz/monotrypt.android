#pragma once

// First-order allpass filter.
// H(z) = (a + z^-1) / (1 + a*z^-1)
class Allpass {
public:
    void setCoefficient(float a) { a_ = a; }

    void reset() { z1_ = 0.0f; }

    float process(float in) {
        float out = a_ * in + z1_;
        z1_ = in - a_ * out;
        return out;
    }

private:
    float a_ = 0.0f;
    float z1_ = 0.0f;
};
