import base64
import json
import math
import sys
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/zyrox"
BLOCK_UNITS = 16.0

FACES = {
    "north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0), (0, 0, -1)),
    "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 0, 1)),
    "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1), (-1, 0, 0)),
    "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0), (1, 0, 0)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)),
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1), (0, -1, 0)),
}

def rotate(point, rotation):
    x, y, z = point
    rx, ry, rz = (math.radians(angle) for angle in rotation)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x, y, z

def bake(element, resolution):
    low = element["from"]
    high = element["to"]
    origin = element.get("origin", [8, 8, 8])
    rotation = element.get("rotation") or [0, 0, 0]
    quads = []
    for name, face in element["faces"].items():
        if face.get("texture") is None or name not in FACES:
            continue
        *corners, normal = FACES[name]
        u1, v1, u2, v2 = face["uv"]
        uvs = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
        steps = int(face.get("rotation", 0)) // 90
        values = []
        for index, corner in enumerate(corners):
            point = [high[axis] if corner[axis] else low[axis] for axis in range(3)]
            moved = rotate([point[axis] - origin[axis] for axis in range(3)], rotation)
            values += [round((moved[axis] + origin[axis]) / BLOCK_UNITS, 5) for axis in range(3)]
            u, v = uvs[(index + steps) % 4]
            values += [round(u / resolution["width"], 5), round(v / resolution["height"], 5)]
        values += [round(value, 5) for value in rotate(normal, rotation)]
        quads.append(values)
    return quads

def main():
    source, cosmetic = sys.argv[1], sys.argv[2]
    model = json.loads(Path(source).read_text(encoding="utf-8"))
    used = {face.get("texture") for element in model["elements"] for face in element.get("faces", {}).values()} - {None}
    if len(used) != 1:
        raise SystemExit("Only models with one texture are supported")
    source = next(texture for index, texture in enumerate(model["textures"]) if index in used or texture.get("id") in used)["source"]
    head = model["display"]["head"]

    texture = ASSETS / "textures/cosmetic" / f"{cosmetic}.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    texture.write_bytes(base64.b64decode(source.split(",", 1)[1]))

    quads = []
    for element in model["elements"]:
        if element.get("visibility", True):
            quads += bake(element, model["resolution"])
    output = ASSETS / "cosmetic" / f"{cosmetic}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({
        "translation": head.get("translation", [0, 0, 0]),
        "rotation": head.get("rotation", [0, 0, 0]),
        "scale": head.get("scale", [1, 1, 1]),
        "quads": quads
    }, separators=(",", ":")), encoding="utf-8")
    print(cosmetic, len(quads), "faces")

main()
