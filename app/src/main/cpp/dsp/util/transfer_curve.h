#pragma once
#include <cmath>
#include <algorithm>

// 256-point lookup table with cubic interpolation for waveshaping.
class TransferCurve {
public:
    static constexpr int TABLE_SIZE = 256;

    TransferCurve() {
        // Default: linear identity
        for (int i = 0; i < TABLE_SIZE; i++) {
            table_[i] = (static_cast<float>(i) / (TABLE_SIZE - 1)) * 2.0f - 1.0f;
        }
    }

    void setTable(const float* data) {
        for (int i = 0; i < TABLE_SIZE; i++) {
            table_[i] = data[i];
        }
    }

    // Set to common preset curves
    void setSoftClip() {
        for (int i = 0; i < TABLE_SIZE; i++) {
            float x = (static_cast<float>(i) / (TABLE_SIZE - 1)) * 2.0f - 1.0f;
            table_[i] = std::tanh(x * 2.0f) * 0.7f;
        }
    }

    // Lookup with cubic interpolation. Input should be in [-1, 1].
    float process(float input) const {
        // Map input [-1, 1] to table index [0, TABLE_SIZE-1]
        float idx = (input * 0.5f + 0.5f) * (TABLE_SIZE - 1);
        idx = std::max(0.0f, std::min(idx, static_cast<float>(TABLE_SIZE - 1)));

        int i = static_cast<int>(idx);
        float frac = idx - static_cast<float>(i);

        // Cubic interpolation with clamped indices
        float y0 = table_[std::max(0, i - 1)];
        float y1 = table_[i];
        float y2 = table_[std::min(TABLE_SIZE - 1, i + 1)];
        float y3 = table_[std::min(TABLE_SIZE - 1, i + 2)];

        float c0 = y1;
        float c1 = 0.5f * (y2 - y0);
        float c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3;
        float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);

        return ((c3 * frac + c2) * frac + c1) * frac + c0;
    }

    const float* getTable() const { return table_; }
    float* getTable() { return table_; }

private:
    float table_[TABLE_SIZE];
};
