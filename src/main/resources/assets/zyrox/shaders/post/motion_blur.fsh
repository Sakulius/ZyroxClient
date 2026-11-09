#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D PreviousSampler;

layout(std140) uniform MotionConfig {
    float Strength;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 previous = texture(PreviousSampler, texCoord).rgb;
    float weight = dot(previous, vec3(1.0)) < 0.001 ? 0.0 : Strength;
    fragColor = vec4(mix(current, previous, weight), 1.0);
}
