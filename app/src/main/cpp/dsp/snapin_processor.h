#pragma once
#include <cstdint>

// Plugin type enum — matches SnapinType.kt ordinal
enum class SnapinType : int32_t {
    GAIN = 0, STEREO, FILTER, EQ_3BAND, COMPRESSOR, LIMITER,
    GATE, DYNAMICS, COMPACTOR, TRANSIENT_SHAPER, DISTORTION, SHAPER,
    CHORUS, ENSEMBLE, FLANGER, PHASER, DELAY, REVERB,
    BITCRUSH, COMB_FILTER, CHANNEL_MIXER, FORMANT_FILTER,
    FREQUENCY_SHIFTER, HAAS, LADDER_FILTER, NONLINEAR_FILTER,
    PHASE_DISTORTION, PITCH_SHIFTER, RESONATOR, REVERSER,
    RING_MOD, TAPE_STOP, TRANCE_GATE,
    COUNT
};

class SnapinProcessor {
public:
    virtual ~SnapinProcessor() = default;

    virtual void prepare(double sampleRate, int maxBlockSize) = 0;
    virtual void process(float* left, float* right, int numFrames) = 0;
    virtual void setParameter(int index, float value) = 0;
    virtual float getParameter(int index) const = 0;
    virtual int getNumParameters() const = 0;
    virtual const char* getName() const = 0;
    virtual SnapinType getType() const = 0;

    bool isBypassed() const { return bypassed_; }
    void setBypassed(bool b) { bypassed_ = b; }

protected:
    double sampleRate_ = 44100.0;
    int maxBlockSize_ = 512;
    bool bypassed_ = false;
};

// Factory — implemented in dsp_engine.cpp
SnapinProcessor* createSnapin(SnapinType type);
