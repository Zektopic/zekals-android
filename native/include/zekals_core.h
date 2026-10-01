#pragma once
#include <cstddef>
#include <cstdint>
namespace zekals {
struct Filter { double x{}, y{}, timestamp{}; bool valid{}; };
Filter smooth(Filter previous, double x, double y, double timestamp, double tau, bool valid);
struct Dwell { int target{-1}; std::int64_t since{}; bool fired{}; };
bool select(Dwell& state, int target, std::int64_t now, std::int64_t duration);
struct Plane { const std::uint8_t* data; std::size_t size; int row_stride; int pixel_stride; };
// Converts strided YUV_420_888 into caller-owned, tightly packed RGBA. Rotation is clockwise.
bool rgba(Plane y, Plane u, Plane v, int width, int height, int rotation, bool mirror,
          std::uint8_t* output, std::size_t output_size);
}
