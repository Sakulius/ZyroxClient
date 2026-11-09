import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/zyrox"
QUAD = "minecraft:core/screenquad"


def blur_pass(source, output, direction, radius):
    return {"vertex_shader": QUAD, "fragment_shader": "minecraft:post/box_blur",
            "inputs": [{"sampler_name": "In", "target": source, "bilinear": True}], "output": output,
            "uniforms": {"BlurConfig": [{"name": "BlurDir", "type": "vec2", "value": direction}, {"name": "Radius", "type": "float", "value": float(radius)}]}}


def blit(source, output):
    return {"vertex_shader": QUAD, "fragment_shader": "minecraft:post/blit",
            "inputs": [{"sampler_name": "In", "target": source}], "output": output,
            "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]}}


(ASSETS / "post_effect").mkdir(parents=True, exist_ok=True)
for level in range(1, 9):
    passes = []
    for _ in range(2):
        passes.append(blur_pass("minecraft:main", "swap", [1.0, 0.0], level))
        passes.append(blur_pass("swap", "minecraft:main", [0.0, 1.0], level))
    (ASSETS / f"post_effect/menu_blur_{level}.json").write_text(json.dumps({"targets": {"swap": {}}, "passes": passes}), encoding="ascii")

for name, strength in {"low": 0.45, "medium": 0.6, "high": 0.75}.items():
    passes = [
        {"vertex_shader": QUAD, "fragment_shader": "zyrox:post/motion_blur",
         "inputs": [{"sampler_name": "In", "target": "minecraft:main"}, {"sampler_name": "Previous", "target": "previous"}], "output": "swap",
         "uniforms": {"MotionConfig": [{"name": "Strength", "type": "float", "value": strength}]}},
        blit("swap", "previous"),
        blit("swap", "minecraft:main"),
    ]
    (ASSETS / f"post_effect/motion_blur_{name}.json").write_text(json.dumps({"targets": {"swap": {}, "previous": {"persistent": True}}, "passes": passes}), encoding="ascii")

(ASSETS / "shaders/post").mkdir(parents=True, exist_ok=True)
(ASSETS / "shaders/post/motion_blur.fsh").write_text("""#version 330
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
""", encoding="ascii")

sounds = {}
for name in ["hover", "click", "toggle_on", "toggle_off", "open", "close", "slide", "tab", "locked", "type", "equip"]:
    sounds[f"ui.{name}"] = {"sounds": [{"name": f"zyrox:ui/{name}"}]}
(ASSETS / "sounds.json").write_text(json.dumps(sounds, indent=2), encoding="ascii")
print("ok")
