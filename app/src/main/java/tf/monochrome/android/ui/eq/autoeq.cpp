
#include <emscripten/bind.h>
#include <vector>
#include <cmath>
#include <algorithm>
#include <string>

using namespace emscripten;

// Constants
const double MAX_BOOST = 12.0;
const double MAX_CUT = 12.0;
const double MAX_Q = 8.0;
const double MIN_Q = 0.6;
const double PI = 3.14159265358979323846;

// Data Structures
struct FrequencyPoint {
    double freq;
    double gain;
};

struct EqBand {
    int id;
    std::string type; // "peaking", "lowshelf", "highshelf"
    double freq;
    double gain;
    double q;
    bool enabled;
};

// Helper: Linear Interpolation
double interpolate(double freq, const std::vector<FrequencyPoint>& data) {
    if (data.empty()) return 0.0;
    if (freq <= data.front().freq) return data.front().gain;
    if (freq >= data.back().freq) return data.back().gain;

    for (size_t i = 0; i < data.size() - 1; ++i) {
        if (freq >= data[i].freq && freq <= data[i + 1].freq) {
            double t = (freq - data[i].freq) / (data[i + 1].freq - data[i].freq);
            return data[i].gain + t * (data[i + 1].gain - data[i].gain);
        }
    }
    return 0.0;
}

// Normalization: Average energy between 250Hz and 2500Hz
double getNormalizationOffset(const std::vector<FrequencyPoint>& data) {
    double sum = 0.0;
    int count = 0;
    for (const auto& p : data) {
        if (p.freq >= 250.0 && p.freq <= 2500.0) {
            sum += p.gain;
            count++;
        }
    }
    if (count == 0) return interpolate(1000.0, data);
    return sum / count;
}

// Helper: Calculate Biquad Response
double calculateBiquadResponse(double freq, const EqBand& band, double sampleRate = 48000.0) {
    if (!band.enabled) return 0.0;

    double w0 = 2.0 * PI * band.freq / sampleRate;
    double phi = 2.0 * PI * freq / sampleRate;
    double alpha = std::sin(w0) / (2.0 * band.q);
    double A = std::pow(10.0, band.gain / 40.0);
    double cosw0 = std::cos(w0);

    double b0, b1, b2, a0, a1, a2;

    if (band.type == "peaking") {
        b0 = 1.0 + alpha * A;
        b1 = -2.0 * cosw0;
        b2 = 1.0 - alpha * A;
        a0 = 1.0 + alpha / A;
        a1 = -2.0 * cosw0;
        a2 = 1.0 - alpha / A;
    } else {
        // Fallback for shelves if manually enabled, though AutoEq won't generate them now
        return 0.0; 
    }

    double b0n = b0 / a0;
    double b1n = b1 / a0;
    double b2n = b2 / a0;
    double a1n = a1 / a0;
    double a2n = a2 / a0;

    double cosPhi = std::cos(phi);
    double cos2Phi = std::cos(2.0 * phi);

    double num = b0n * b0n + b1n * b1n + b2n * b2n + 2.0 * (b0n * b1n + b1n * b2n) * cosPhi + 2.0 * b0n * b2n * cos2Phi;
    double den = 1.0 + a1n * a1n + a2n * a2n + 2.0 * (a1n + a1n * a2n) * cosPhi + 2.0 * a2n * cos2Phi;

    return 10.0 * std::log10(num / den);
}

// Main Algorithm
std::vector<EqBand> runAutoEqAlgorithm(
    const std::vector<FrequencyPoint>& measurement,
    const std::vector<FrequencyPoint>& target,
    int bandCount,
    double maxFrequency
) {
    double measOffset = getNormalizationOffset(measurement);
    double targetOffset = getNormalizationOffset(target);
    double diffOffset = targetOffset - measOffset;

    // Create Error Curve
    std::vector<FrequencyPoint> currentErrorCurve;
    currentErrorCurve.reserve(measurement.size());
    for (const auto& p : measurement) {
        double targetGain = interpolate(p.freq, target);
        currentErrorCurve.push_back({ p.freq, (p.gain + diffOffset) - targetGain });
    }

    std::vector<EqBand> bands;

    for (int i = 0; i < bandCount; ++i) {
        double maxDev = 0.0;
        double maxWeightedDev = 0.0;
        double peakFreq = 1000.0;
        int peakIdx = 0;

        // Find largest deviation with localized smoothing and priority weighting
        for (size_t j = 0; j < currentErrorCurve.size(); ++j) {
            double freq = currentErrorCurve[j].freq;
            if (freq < 20.0 || freq > maxFrequency) continue;

            double val = currentErrorCurve[j].gain;
            // 3-point smooth
            if (j > 0 && j < currentErrorCurve.size() - 1) {
                val = (currentErrorCurve[j-1].gain + val + currentErrorCurve[j+1].gain) / 3.0;
            }

            // Priority Weighting
            double priority = 1.0;
            if (freq < 300.0) priority = 1.5;        // Bass Bias
            else if (freq < 4000.0) priority = 1.0;  // Mids Baseline
            else if (freq < 8000.0) priority = 0.5;  // Lower Treble Reduced
            else priority = 0.25;                    // High Treble Suppressed

            double weightedAbs = std::abs(val * priority);

            if (weightedAbs > std::abs(maxWeightedDev)) {
                maxWeightedDev = weightedAbs;
                maxDev = val; // Store actual
                peakFreq = freq;
                peakIdx = (int)j;
            }
        }

        // Invert for correction
        double gain = -maxDev;
        
        // Treble Safety: Taper max boost in highs
        double safeBoost = MAX_BOOST;
        if (peakFreq > 3000.0) safeBoost = 6.0;
        if (peakFreq > 6000.0) safeBoost = 3.0;

        // Asymmetric Clamping
        if (gain > safeBoost) gain = safeBoost;
        if (gain < -MAX_CUT) gain = -MAX_CUT;

        if (std::abs(gain) < 0.2) break;

        // Smart Q Calculation (Half-energy points)
        double targetEnergy = maxDev / 2.0;
        double lowerFreq = peakFreq;
        double upperFreq = peakFreq;

        for (int k = peakIdx; k >= 0; --k) {
            if (std::abs(currentErrorCurve[k].gain) < std::abs(targetEnergy)) {
                lowerFreq = currentErrorCurve[k].freq;
                break;
            }
        }
        for (size_t k = peakIdx; k < currentErrorCurve.size(); ++k) {
            if (std::abs(currentErrorCurve[k].gain) < std::abs(targetEnergy)) {
                upperFreq = currentErrorCurve[k].freq;
                break;
            }
        }

        double bandwidth = std::log2(upperFreq / std::max(1.0, lowerFreq));
        if (bandwidth < 0.1) bandwidth = 0.1;

        double q = std::sqrt(std::pow(2.0, bandwidth)) / (std::pow(2.0, bandwidth) - 1.0);
        
        // Constraints
        if (q < MIN_Q) q = MIN_Q;
        if (q > MAX_Q) q = MAX_Q;
        if (peakFreq > 5000.0 && q > 3.0) q = 3.0; // Treble safety
        if (gain > 0.0 && q > 2.0) q = 2.0; // Boost safety

        std::string type = "peaking";

        EqBand newBand = { i, type, peakFreq, gain, q, true };
        bands.push_back(newBand);

        // Update Error Curve
        for (auto& p : currentErrorCurve) {
            p.gain += calculateBiquadResponse(p.freq, newBand);
        }
    }

    // Sort bands by frequency
    std::sort(bands.begin(), bands.end(), [](const EqBand& a, const EqBand& b) {
        return a.freq < b.freq;
    });

    for (int i = 0; i < bands.size(); ++i) {
        bands[i].id = i;
    }

    return bands;
}

// Emscripten Bindings
EMSCRIPTEN_BINDINGS(autoeq_module) {
    value_object<FrequencyPoint>("FrequencyPoint")
        .field("freq", &FrequencyPoint::freq)
        .field("gain", &FrequencyPoint::gain);

    value_object<EqBand>("EqBand")
        .field("id", &EqBand::id)
        .field("type", &EqBand::type)
        .field("freq", &EqBand::freq)
        .field("gain", &EqBand::gain)
        .field("q", &EqBand::q)
        .field("enabled", &EqBand::enabled);

    register_vector<FrequencyPoint>("VectorFrequencyPoint");
    register_vector<EqBand>("VectorEqBand");

    function("runAutoEqAlgorithm", &runAutoEqAlgorithm);
}
