#include "zekals_core.h"
#include <array>
#include <cassert>
#include <cmath>
#include <limits>
int main() {
    auto state = zekals::smooth({}, .2, .4, 0, 100, true);
    state = zekals::smooth(state, .8, .6, 100, 100, true);
    assert(std::abs(state.x - .5) < 1e-9);
    state = zekals::smooth(state, std::numeric_limits<double>::quiet_NaN(), 0, 110, 100, true);
    assert(!state.valid);
    assert(zekals::smooth(state, .9, .1, 120, 100, true).x == .9);
    zekals::Dwell dwell;
    assert(!zekals::select(dwell, 2, 0, 1000));
    assert(zekals::select(dwell, 2, 1000, 1000));
    assert(!zekals::select(dwell, 2, 5000, 1000));
    assert(!zekals::select(dwell, -1, 6000, 1000));
    assert(!zekals::select(dwell, 2, 6100, 1000));
    assert(zekals::select(dwell, 2, 7100, 1000));
    std::array<std::uint8_t, 8> y{16, 235, 0, 0, 81, 145, 0, 0};
    std::array<std::uint8_t, 2> uv{128, 128};
    std::array<std::uint8_t, 16> output{};
    zekals::Plane yp{y.data(), y.size(), 4, 1}, cp{uv.data(), uv.size(), 2, 2};
    assert(zekals::rgba(yp, cp, cp, 2, 2, 0, false, output.data(), output.size()));
    assert(output[0] == 0 && output[4] == 255 && output[3] == 255);
    assert(zekals::rgba(yp, cp, cp, 2, 2, 90, true, output.data(), output.size()));
    assert(output[0] == 0 && output[8] == 255);
    assert(!zekals::rgba(yp, cp, cp, 2, 2, 0, false, output.data(), 4));
    yp.size = 1;
    assert(!zekals::rgba(yp, cp, cp, 2, 2, 0, false, output.data(), output.size()));
    assert(!zekals::rgba(yp, cp, cp, 99999, 2, 0, false, output.data(), output.size()));
}
