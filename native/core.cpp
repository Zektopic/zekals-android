#include "zekals_core.h"
#include <algorithm>
#include <cmath>
namespace zekals {
Filter smooth(Filter previous, double x, double y, double timestamp, double tau, bool valid) {
    if (!valid || !std::isfinite(x) || !std::isfinite(y) || !std::isfinite(timestamp) ||
        !std::isfinite(tau) || tau <= 0 || x < 0 || x > 1 || y < 0 || y > 1) return {};
    const double elapsed = timestamp - previous.timestamp;
    const double alpha = previous.valid && std::isfinite(previous.x) && std::isfinite(previous.y)
        && elapsed > 0 && elapsed <= 500 ? elapsed / (tau + elapsed) : 1;
    if (alpha == 1) return {x, y, timestamp, true};
    return {previous.x + alpha * (x - previous.x), previous.y + alpha * (y - previous.y), timestamp, true};
}
bool select(Dwell& state, int target, std::int64_t now, std::int64_t duration) {
    if (target < 0 || now < 0 || duration < 500 || duration > 5000) { state = {}; return false; }
    if (state.target != target || now < state.since) state = {target, now, false};
    if (!state.fired && now - state.since >= duration) { state.fired = true; return true; }
    return false;
}
static bool valid_plane(Plane plane, int width, int height) {
    if (!plane.data || plane.row_stride <= 0 || plane.pixel_stride <= 0) return false;
    const auto last = static_cast<std::uint64_t>(height - 1) * plane.row_stride +
                      static_cast<std::uint64_t>(width - 1) * plane.pixel_stride;
    return last < plane.size;
}
bool rgba(Plane y, Plane u, Plane v, int width, int height, int rotation, bool mirror,
          std::uint8_t* output, std::size_t output_size) {
    if (width < 2 || height < 2 || width > 4096 || height > 4096 || !output ||
        output_size < static_cast<std::size_t>(width) * height * 4 ||
        (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) ||
        !valid_plane(y, width, height) || !valid_plane(u, (width + 1) / 2, (height + 1) / 2) ||
        !valid_plane(v, (width + 1) / 2, (height + 1) / 2)) return false;
    const int out_width = rotation == 90 || rotation == 270 ? height : width;
    for (int row = 0; row < height; ++row) for (int column = 0; column < width; ++column) {
        const int yy = std::max(0, static_cast<int>(y.data[row * y.row_stride + column * y.pixel_stride]) - 16);
        const int uu = u.data[(row / 2) * u.row_stride + (column / 2) * u.pixel_stride] - 128;
        const int vv = v.data[(row / 2) * v.row_stride + (column / 2) * v.pixel_stride] - 128;
        int ox = column, oy = row;
        if (rotation == 90) { ox = height - 1 - row; oy = column; }
        if (rotation == 180) { ox = width - 1 - column; oy = height - 1 - row; }
        if (rotation == 270) { ox = row; oy = width - 1 - column; }
        if (mirror) ox = out_width - 1 - ox;
        const auto offset = static_cast<std::size_t>(oy * out_width + ox) * 4;
        output[offset] = static_cast<std::uint8_t>(std::clamp((298 * yy + 409 * vv + 128) >> 8, 0, 255));
        output[offset + 1] = static_cast<std::uint8_t>(std::clamp((298 * yy - 100 * uu - 208 * vv + 128) >> 8, 0, 255));
        output[offset + 2] = static_cast<std::uint8_t>(std::clamp((298 * yy + 516 * uu + 128) >> 8, 0, 255));
        output[offset + 3] = 255;
    }
    return true;
}
}
