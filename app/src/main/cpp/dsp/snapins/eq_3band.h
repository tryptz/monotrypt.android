#pragma once
#include "snapin_processor.h"
#include "biquad.h"
#include <cmath>
#include <algorithm>

// 3-Band EQ: Linkwitz-Riley crossover (2x cascaded Butterworth)
// splitting into low/mid/high bands with independent gain.
class Eq3BandProcessor : public SnapinProcessor {
public:
    enum Params { SPLIT_LOW_MID = 0, SPLIT_MID_HIGH, LOW_GAIN, MID_GAIN, HIGH_GAIN, NUM_PARAMS };

    void prepare(double sampleRate, int maxBlockSize) override {
        sampleRate_ = sampleRate;
        maxBlockSize_ = maxBlockSize;
        resetFilters();
        updateCoeffs();
    }

    void process(float* left, float* right, int numFrames) override {
        for (int i = 0; i < numFrames; i++) {
            // Process left channel
            left[i] = processSample(left[i], lpL1_, lpL2_, hpL1_, hpL2_,
                                     lpMidL1_, lpMidL2_, hpMidL1_, hpMidL2_);
            // Process right channel
            right[i] = processSample(right[i], lpR1_, lpR2_, hpR1_, hpR2_,
                                      lpMidR1_, lpMidR2_, hpMidR1_, hpMidR2_);
        }
    }

    void setParameter(int index, float value) override {
        switch (index) {
            case SPLIT_LOW_MID:
                splitLowMid_ = std::max(20.0f, std::min(5000.0f, value));
                break;
            case SPLIT_MID_HIGH:
                splitMidHigh_ = std::max(200.0f, std::min(20000.0f, value));
                break;
            case LOW_GAIN:
                lowGain_ = std::pow(10.0f, std::max(-24.0f, std::min(24.0f, value)) / 20.0f);
                lowGainDb_ = value;
                break;
            case MID_GAIN:
                midGain_ = std::pow(10.0f, std::max(-24.0f, std::min(24.0f, value)) / 20.0f);
                midGainDb_ = value;
                break;
            case HIGH_GAIN:
                highGain_ = std::pow(10.0f, std::max(-24.0f, std::min(24.0f, value)) / 20.0f);
                highGainDb_ = value;
                break;
        }
        // Ensure low split ≤ high split
        if (splitLowMid_ > splitMidHigh_) splitLowMid_ = splitMidHigh_;
        updateCoeffs();
    }

    float getParameter(int index) const override {
        switch (index) {
            case SPLIT_LOW_MID:  return splitLowMid_;
            case SPLIT_MID_HIGH: return splitMidHigh_;
            case LOW_GAIN:       return lowGainDb_;
            case MID_GAIN:       return midGainDb_;
            case HIGH_GAIN:      return highGainDb_;
            default: return 0.0f;
        }
    }

    int getNumParameters() const override { return NUM_PARAMS; }
    const char* getName() const override { return "3-Band EQ"; }
    SnapinType getType() const override { return SnapinType::EQ_3BAND; }

private:
    float processSample(float in,
                        Biquad& lp1, Biquad& lp2, Biquad& hp1, Biquad& hp2,
                        Biquad& lpMid1, Biquad& lpMid2, Biquad& hpMid1, Biquad& hpMid2) {
        // Low band: 2x cascaded LP at low-mid split
        float low = lp2.process(lp1.process(in));

        // High pass at low-mid split to get mid+high
        float midHigh = hp2.process(hp1.process(in));

        // Mid band: LP at mid-high split on the midHigh signal
        float mid = lpMid2.process(lpMid1.process(midHigh));

        // High band: HP at mid-high split on the midHigh signal
        float high = hpMid2.process(hpMid1.process(midHigh));

        return low * lowGain_ + mid * midGain_ + high * highGain_;
    }

    void resetFilters() {
        lpL1_.reset(); lpL2_.reset(); hpL1_.reset(); hpL2_.reset();
        lpR1_.reset(); lpR2_.reset(); hpR1_.reset(); hpR2_.reset();
        lpMidL1_.reset(); lpMidL2_.reset(); hpMidL1_.reset(); hpMidL2_.reset();
        lpMidR1_.reset(); lpMidR2_.reset(); hpMidR1_.reset(); hpMidR2_.reset();
    }

    void updateCoeffs() {
        double q = 0.7071067811865476;  // Butterworth Q = 1/sqrt(2)

        // Low-mid crossover
        lpL1_.configure(BiquadType::LowPass, sampleRate_, splitLowMid_, q);
        lpL2_.configure(BiquadType::LowPass, sampleRate_, splitLowMid_, q);
        hpL1_.configure(BiquadType::HighPass, sampleRate_, splitLowMid_, q);
        hpL2_.configure(BiquadType::HighPass, sampleRate_, splitLowMid_, q);
        lpR1_.configure(BiquadType::LowPass, sampleRate_, splitLowMid_, q);
        lpR2_.configure(BiquadType::LowPass, sampleRate_, splitLowMid_, q);
        hpR1_.configure(BiquadType::HighPass, sampleRate_, splitLowMid_, q);
        hpR2_.configure(BiquadType::HighPass, sampleRate_, splitLowMid_, q);

        // Mid-high crossover
        lpMidL1_.configure(BiquadType::LowPass, sampleRate_, splitMidHigh_, q);
        lpMidL2_.configure(BiquadType::LowPass, sampleRate_, splitMidHigh_, q);
        hpMidL1_.configure(BiquadType::HighPass, sampleRate_, splitMidHigh_, q);
        hpMidL2_.configure(BiquadType::HighPass, sampleRate_, splitMidHigh_, q);
        lpMidR1_.configure(BiquadType::LowPass, sampleRate_, splitMidHigh_, q);
        lpMidR2_.configure(BiquadType::LowPass, sampleRate_, splitMidHigh_, q);
        hpMidR1_.configure(BiquadType::HighPass, sampleRate_, splitMidHigh_, q);
        hpMidR2_.configure(BiquadType::HighPass, sampleRate_, splitMidHigh_, q);
    }

    float splitLowMid_ = 200.0f;
    float splitMidHigh_ = 5000.0f;
    float lowGain_ = 1.0f, midGain_ = 1.0f, highGain_ = 1.0f;
    float lowGainDb_ = 0.0f, midGainDb_ = 0.0f, highGainDb_ = 0.0f;

    // L-R crossover: 2x cascaded Butterworth per split, per channel
    // Low-mid split
    Biquad lpL1_, lpL2_, hpL1_, hpL2_;
    Biquad lpR1_, lpR2_, hpR1_, hpR2_;
    // Mid-high split
    Biquad lpMidL1_, lpMidL2_, hpMidL1_, hpMidL2_;
    Biquad lpMidR1_, lpMidR2_, hpMidR1_, hpMidR2_;
};
