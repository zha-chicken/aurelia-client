#version 150

in vec4 vertexColor;
in vec2 localPosition;
in vec2 halfSize;
in float cornerRadius;

uniform vec4 ColorModulator;

out vec4 fragColor;

float roundedBoxDistance(vec2 point, vec2 bounds, float radius) {
    vec2 edge = abs(point) - bounds + radius;
    return min(max(edge.x, edge.y), 0.0) + length(max(edge, 0.0)) - radius;
}

void main() {
    float distance = roundedBoxDistance(localPosition, halfSize, cornerRadius);
    float antialias = max(fwidth(distance), 0.35);
    float coverage = 1.0 - smoothstep(-antialias, antialias, distance);
    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
