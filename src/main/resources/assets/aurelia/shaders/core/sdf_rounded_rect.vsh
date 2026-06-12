#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in vec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 localPosition;
out vec2 halfSize;
out float cornerRadius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPosition = UV0;
    cornerRadius = UV1.x / 32.0;
    halfSize = UV2 / 32.0;
}
