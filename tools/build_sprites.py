"""
Sprite sheet measuring tool.

The sprite sheets were drawn by hand, so the character is not in the same spot
inside every frame (and some sheets are different sizes for Player 1 vs Player 2).
Cutting them with fixed frame sizes is what made the players "pop", jitter and
sometimes disappear.

This script looks at the actual pixels of every frame and records:
  * the tight bounding box of the character
  * the feet anchor (bottom-center of the shoes) so every animation lines up
  * the hand position on each shooting frame (so the ball sits in the hands)
  * a per-sheet draw scale so the character is the same size in every animation

It writes the results for both versions of the game:
  * src/SpriteData.java   (Java version)
  * web/sprites.js        (browser version)
and also exports compressed .webp copies of the images for the web build.

Usage:  python3 tools/build_sprites.py
"""

import json
import os

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMG_DIR = os.path.join(ROOT, "src", "images")
WEB_ASSETS = os.path.join(ROOT, "web", "assets")

# How tall (in game pixels) a standing character should be on screen.
STANDING_HEIGHT = 215

ALPHA_THRESHOLD = 48

# sheet name -> (file, cols, rows, which way the side-view frames face)
# "faces" is per row: R = facing right, L = facing left, F = toward camera, A = away
SHEETS = {
    "p1_walk":    ("basketballPlayer1_walk_jog_run.png",          4, 3, ["R", "R", "R"]),
    "p1_updown":  ("basketball1Player_walkingForward_Away.png",   8, 2, ["A", "F"]),
    "p1_dribble": ("basketballPlayer1_dribbling.png",             5, 4, ["R", "L", "F", "A"]),
    "p1_shoot":   ("basketballPlayer1_shooting_motion.png",       5, 4, ["R", "L", "F", "A"]),
    "p1_idle":    ("basketballPlayer1_Idle.png",                  4, 1, ["L"]),
    "p1_jump":    ("basketballPlayer1_jumping_animation.png",     6, 1, ["R"]),
    "p1_reach":   ("basketballPlayer1_reach_animation.png",       3, 1, ["R"]),

    "p2_walk":    ("basketballPlayer2_walk_jog_run.png",          4, 3, ["R", "R", "R"]),
    "p2_updown":  ("basketballPlayer2_walkingForward_Away.png",   8, 2, ["A", "F"]),
    "p2_dribble": ("basketballPlayer2_dribbling.png",             5, 4, ["R", "L", "F", "A"]),
    "p2_shoot":   ("basketballPlayer2_shooting_motion.png",       5, 4, ["R", "L", "F", "A"]),
    "p2_idle":    ("basketballPlayer2_Idle.png",                  4, 1, ["L"]),
    "p2_jump":    ("basketballPlayer2_jumping_animation.png",     6, 1, ["L"]),
    "p2_reach":   ("basketballPlayer2_reach_animation.png",       3, 1, ["L"]),
}



PER_SHEET_TWEAK = {"p2_idle": 0.92}


def frame_mask(alpha, x0, y0, x1, y1):
    """Opaque pixels of one cell with stray bits from neighbouring frames removed."""
    cell = alpha[y0:y1, x0:x1] > ALPHA_THRESHOLD
    labels, n = ndimage.label(cell)
    if n == 0:
        return cell
    sizes = ndimage.sum(cell, labels, range(1, n + 1))
    biggest = sizes.max()
    keep = np.zeros(n + 1, dtype=bool)
    objs = ndimage.find_objects(labels)
    w = x1 - x0
    for i, sl in enumerate(objs):
        size = sizes[i]
        h = y1 - y0
        touches_edge = (sl[1].start == 0 or sl[1].stop == w or
                        sl[0].start == 0 or sl[0].stop == h)
        if size < max(30, biggest * 0.01):  # specks / dust
            continue
        if touches_edge and size < biggest * 0.25:
            continue                       # piece of the neighbouring frame
        keep[i + 1] = True
    return keep[labels]


def measure(name, file, cols, rows, faces):
    im = Image.open(os.path.join(IMG_DIR, file)).convert("RGBA")
    W, H = im.size
    alpha = np.array(im)[:, :, 3]
    cw, ch = W / cols, H / rows
    kind = name.split("_")[1]

    frames = []
    for r in range(rows):
        row = []
        for c in range(cols):
            x0, y0 = int(round(c * cw)), int(round(r * ch))
            x1, y1 = int(round((c + 1) * cw)), int(round((r + 1) * ch))
            m = frame_mask(alpha, x0, y0, x1, y1)
            ys, xs = np.nonzero(m)
            bx0, bx1 = xs.min(), xs.max() + 1
            by0, by1 = ys.min(), ys.max() + 1
            h = by1 - by0

            # feet anchor: average x of the shoe pixels (bottom 10% of the box)
            feet = ys >= by1 - max(4, int(h * 0.10))
            ax = xs[feet].mean()
            ay = by1

            f = {
                "x": int(x0 + bx0), "y": int(y0 + by0),
                "w": int(bx1 - bx0), "h": int(h),
                "ax": round(float(x0 + ax), 1), "ay": int(y0 + ay),
            }

            if kind == "shoot":
                f["hx"], f["hy"] = hand_point(xs, ys, by0, by1, faces[r], c, ax)
                f["hx"] = round(float(x0 + f["hx"]), 1)
                f["hy"] = round(float(y0 + f["hy"]), 1)
            row.append(f)
        frames.append(row)

    # draw scale -> standing character is STANDING_HEIGHT tall
    # (jump/shoot frames have arms raised, so only their first frame is used)
    if kind in ("shoot", "jump"):
        ref = np.median([row[0]["h"] for row in frames])
    else:
        ref = np.median([f["h"] for row in frames for f in row])
    # reach is a crouched lunge, so its scale is matched by head size later (match_head_scale)
    scale = None if kind == "reach" else STANDING_HEIGHT / ref
    return {"file": file, "cols": cols, "rows": rows, "faces": faces,
            "w": W, "h": H, "scale": scale, "frames": frames}


def hand_point(xs, ys, by0, by1, face, frame, ax):
    """Where the ball should be held on a shooting frame (cell-local coords)."""
    h = by1 - by0
    if face in ("R", "L"):
        # hands are the furthest point forward in the upper body
        upper = ys < by0 + h * 0.62
        ux, uy = xs[upper], ys[upper]
        if face == "R":
            tip = ux.max()
            sel = ux >= tip - 10
        else:
            tip = ux.min()
            sel = ux <= tip + 10
        hx = ux[sel].mean()
        hy = uy[sel].mean()
        # ball sits slightly behind the finger tips
        hx = hx - 14 if face == "R" else hx + 14
        return hx, hy - 6
    # toward / away from camera
    if frame <= 1:
        return ax, by0 + h * (0.42 if frame == 0 else 0.30)
    top = ys < by0 + 30
    return xs[top].mean(), by0 + 10


def main():
    data = {}
    for name, (file, cols, rows, faces) in SHEETS.items():
        data[name] = measure(name, file, cols, rows, faces)

    # reach sheets: match the body size of that player's walk sheet using the
    # head width (top 18% of the frame) as a size reference
    for p in ("p1", "p2"):
        data[f"{p}_reach"]["scale"] = match_head_scale(data, p, "reach")

    # small by-eye corrections so bodies match across animations
    for k, mult in PER_SHEET_TWEAK.items():
        data[k]["scale"] *= mult

    for k in data:
        data[k]["scale"] = round(float(data[k]["scale"]), 4)
        print(f"{k:12s} scale={data[k]['scale']}")

    write_js(data)
    write_java(data)
    export_web_images(data)


def head_width(file, frame):
    im = Image.open(os.path.join(IMG_DIR, file)).convert("RGBA")
    a = np.array(im)[:, :, 3] > ALPHA_THRESHOLD
    sub = a[frame["y"]:frame["y"] + frame["h"], frame["x"]:frame["x"] + frame["w"]]
    band = sub[: int(frame["h"] * 0.12)]
    cols = np.nonzero(band.any(axis=0))[0]
    return cols.max() - cols.min() + 1


def match_head_scale(data, p, kind):
    """Scale a sheet so the head is the same size as in that player's walk sheet."""
    walk, other = data[f"{p}_walk"], data[f"{p}_{kind}"]
    hw_walk = np.median([head_width(walk["file"], f) for f in walk["frames"][0]])
    side_frames = other["frames"][0][:2] if kind == "idle" else other["frames"][0]
    hw_other = np.median([head_width(other["file"], f) for f in side_frames])
    return walk["scale"] * hw_walk / hw_other


def export_web_images(data):
    """Compressed copies for the browser build (smaller download, same look)."""
    os.makedirs(WEB_ASSETS, exist_ok=True)
    for k, s in data.items():
        # never store more resolution than ~1.5x what is drawn on screen
        img_scale = min(1.0, s["scale"] * 1.5)
        s["webScale"] = round(img_scale, 4)
    # rewrite JS now that webScale is known
    write_js(data)
    done = set()
    for k, s in data.items():
        if s["file"] in done:
            continue
        done.add(s["file"])
        im = Image.open(os.path.join(IMG_DIR, s["file"])).convert("RGBA")
        sc = s["webScale"]
        if sc < 1.0:
            im = im.resize((round(s["w"] * sc), round(s["h"] * sc)), Image.LANCZOS)
        im.save(os.path.join(WEB_ASSETS, s["file"].replace(".png", ".webp")),
                "WEBP", quality=88, method=6)

    ball = Image.open(os.path.join(IMG_DIR, "ball.png")).convert("RGBA")
    ball = ball.crop(ball.getbbox()).resize((96, 96), Image.LANCZOS)
    ball.save(os.path.join(WEB_ASSETS, "ball.webp"), "WEBP", quality=90, method=6)

    court = Image.open(os.path.join(IMG_DIR, "basketballCourtForGame.jpg")).convert("RGB")
    court = court.resize((1710, 1080), Image.LANCZOS)
    court.save(os.path.join(WEB_ASSETS, "court.webp"), "WEBP", quality=86, method=6)


def write_js(data):
    os.makedirs(os.path.dirname(WEB_ASSETS), exist_ok=True)
    with open(os.path.join(ROOT, "web", "sprites.js"), "w") as fh:
        fh.write("// Generated by tools/build_sprites.py - do not edit by hand.\n")
        fh.write("const SPRITES = ")
        fh.write(json.dumps(data, separators=(",", ":")))
        fh.write(";\n")


def write_java(data):
    out = []
    out.append("// Generated by tools/build_sprites.py - do not edit by hand.")
    out.append("// Measured frame boxes, feet anchors and hand points for every sprite sheet.")
    out.append("public final class SpriteData {")
    out.append("")
    out.append("\tprivate SpriteData() {}")
    out.append("")
    for k, s in data.items():
        const = k.upper()
        out.append(f"\t// {s['file']}")
        out.append(f"\tpublic static final SpriteSheet {const} = new SpriteSheet(")
        out.append(f"\t\t\"{s['file']}\", {s['scale']}, \"{''.join(s['faces'])}\",")
        out.append("\t\tnew double[][][] {")
        for row in s["frames"]:
            cells = []
            for f in row:
                vals = [f["x"], f["y"], f["w"], f["h"], f["ax"], f["ay"],
                        f.get("hx", 0), f.get("hy", 0)]
                cells.append("{" + ", ".join(str(v) for v in vals) + "}")
            out.append("\t\t\t{ " + ", ".join(cells) + " },")
        out.append("\t\t});")
        out.append("")
    out.append("}")
    with open(os.path.join(ROOT, "src", "SpriteData.java"), "w") as fh:
        fh.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
