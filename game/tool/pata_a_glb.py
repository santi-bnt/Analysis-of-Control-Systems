"""Rig modelos/pata_modelo.glb for the 3D board. Two bones: root palm centered at the hand pose,
and fingers bending toward the pads at the knuckle. Blend nearby vertex weights for smooth
bending. Bake closure into pata_{x,o}_{0..CUADROS-1}.glb from open frame 0 to closed; the app
selects a frame without requiring glTF skin animation. Paw uses hand axes (-Z fingers, -Y pads),
meters, and separate fur/pad materials. Run python tool/pata_a_glb.py from Juego/.
"""

import json
import math
import struct
from pathlib import Path

import numpy as np

ENTRADA = Path("modelos/pata_modelo.glb")
SALIDA = Path("android/app/src/quest/assets/models")

LARGO_M = 0.15            # from wrist to fingertips
# Model units: Y toward fingers, Z toward pads, length about 2.
CENTRO_PALMA_Y = 0.1
NUDILLO_Y = 0.3           # a media altura del cojinete grande
MEZCLA = 0.1              # ± alrededor del nudillo: piel repartida entre los 2 huesos
CURL_RAD = 1.4            # finger bend with a closed hand
# Piece-specific fur colors match tool/cabeza_a_glb.py.
PELAJE = {"x": [0.62, 0.62, 0.66, 1.0], "o": [1.0, 0.62, 0.25, 1.0]}
CUADROS = 4               # igual que PAW_FRAMES en GameActivity3D.kt
PAD = [1.0, 0.55, 0.65, 1.0]


def leer_glb(path: Path):
    b = path.read_bytes()
    n = struct.unpack("<I", b[12:16])[0]
    j = json.loads(b[20:20 + n])
    bin0 = 20 + n + 8
    p = j["meshes"][0]["primitives"][0]

    def acc(i):
        a = j["accessors"][i]
        bv = j["bufferViews"][a["bufferView"]]
        tipo = {5126: np.float32, 5123: np.uint16, 5125: np.uint32}[a["componentType"]]
        k = {"SCALAR": 1, "VEC3": 3}[a["type"]]
        assert bv.get("byteStride", np.dtype(tipo).itemsize * k) == np.dtype(tipo).itemsize * k
        off = bin0 + bv.get("byteOffset", 0) + a.get("byteOffset", 0)
        return np.frombuffer(b, dtype=tipo, count=a["count"] * k, offset=off).reshape(a["count"], k)

    at = p["attributes"]
    return acc(at["POSITION"]).astype(np.float64), acc(at["NORMAL"]).astype(np.float64), acc(p["indices"]).reshape(-1, 3)


def cojinetes(pos: np.ndarray, tri: np.ndarray) -> np.ndarray:
    """Pad triangles protrude from the front of the palm and fingers."""
    c = pos[tri].mean(1)
    # Paw front is at z≈0.22 here; pads extend to 0.26–0.31.
    return (c[:, 1] > 0.2) & (c[:, 2] > 0.245)


def suave(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3 - 2 * t)


def doblar(pos, nor, f):
    """Return paw pose at closure f (0 open to 1 closed) in hand axes and meters, plus finger-bone weights."""
    w = suave((pos[:, 1] - (NUDILLO_Y - MEZCLA)) / (2 * MEZCLA))
    a = CURL_RAD * f          # alrededor de X en el nudillo: los dedos (+Y) van a +Z (cojinetes)
    c, s = math.cos(a), math.sin(a)
    giro = np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    q = pos - [0, NUDILLO_Y, 0]
    p = pos * (1 - w[:, None]) + (q @ giro.T + [0, NUDILLO_Y, 0]) * w[:, None]
    n = nor * (1 - w[:, None]) + (nor @ giro.T) * w[:, None]
    n /= np.linalg.norm(n, axis=1, keepdims=True)
    # Model-to-hand axes: x→-x, y fingers→-z, z pads→-y.
    rot = np.array([[-1, 0, 0], [0, 0, -1], [0, -1, 0]], dtype=np.float64)
    k = LARGO_M / (pos[:, 1].max() - pos[:, 1].min())
    return (((p - [0, CENTRO_PALMA_Y, 0]) * k) @ rot.T).astype(np.float32), (n @ rot.T).astype(np.float32), w


def glb(p, n, tri, pad, pelaje):
    idx_fur = tri[~pad].astype(np.uint16).ravel()
    idx_pad = tri[pad].astype(np.uint16).ravel()
    partes = [p, n, idx_fur, idx_pad]
    datos, vistas = b"", []
    for arr in partes:
        datos += b"\0" * (-len(datos) % 4)
        vistas.append({"buffer": 0, "byteOffset": len(datos), "byteLength": arr.nbytes})
        datos += arr.tobytes()
    datos += b"\0" * (-len(datos) % 4)

    def material(c):
        return {"pbrMetallicRoughness": {"baseColorFactor": c, "metallicFactor": 0.0, "roughnessFactor": 0.8}}

    gltf = {
        "asset": {"version": "2.0", "generator": "pata_a_glb.py"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0}],
        "meshes": [{"primitives": [
            {"attributes": {"POSITION": 0, "NORMAL": 1}, "indices": 2, "material": 0},
            {"attributes": {"POSITION": 0, "NORMAL": 1}, "indices": 3, "material": 1},
        ]}],
        "materials": [material(pelaje), material(PAD)],
        "buffers": [{"byteLength": len(datos)}],
        "bufferViews": vistas,
        "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": len(p), "type": "VEC3",
             "min": p.min(0).tolist(), "max": p.max(0).tolist()},
            {"bufferView": 1, "componentType": 5126, "count": len(p), "type": "VEC3"},
            {"bufferView": 2, "componentType": 5123, "count": len(idx_fur), "type": "SCALAR"},
            {"bufferView": 3, "componentType": 5123, "count": len(idx_pad), "type": "SCALAR"},
        ],
    }
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * (-len(js) % 4)
    total = 12 + 8 + len(js) + 8 + len(datos)
    return (struct.pack("<III", 0x46546C67, 2, total)
            + struct.pack("<II", len(js), 0x4E4F534A) + js
            + struct.pack("<II", len(datos), 0x004E4942) + datos)


if __name__ == "__main__":
    pos, nor, tri = leer_glb(ENTRADA)
    assert len(pos) < 65536, "32-bit indices are required"
    pad = cojinetes(pos, tri)
    for f in range(CUADROS):
        p, n, w = doblar(pos, nor, f / (CUADROS - 1))
        if f == 0:
            # Open paw length is LARGO_M; fingers point -Z and only finger vertices carry bone weights.
            assert abs(p[:, 2].max() - p[:, 2].min() - LARGO_M) < 1e-4
            assert w[p[:, 2].argmin()] == 1 and w[p[:, 2].argmax()] == 0
        else:
            # Closing moves fingertips toward the pads (-Y).
            assert p[w == 1][:, 1].mean() < 0
        for ficha, pelaje in PELAJE.items():
            out = glb(p, n, tri, pad, pelaje)
            (SALIDA / f"pata_{ficha}_{f}.glb").write_bytes(out)
    print(f"{ENTRADA} -> {SALIDA}/pata_{{x,o}}_{{0..{CUADROS - 1}}}.glb: {len(tri)} triangles, {len(out) // 1024} KB each")
