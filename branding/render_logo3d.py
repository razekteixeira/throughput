"""Renders the Throughput icon as a 3D voxel diorama with Blender (Cycles).

Every pixel of branding/icon-32.png becomes a bevelled block; its height depends on what it is
(tile, hopper or sparkline), so the logo reads as a small physical object. Original art only:
the input is our own icon, no game textures.

Run from the project root:
  /Applications/Blender.app/Contents/MacOS/Blender -b -P branding/render_logo3d.py -- branding/logo3d.png
"""

import math
import sys

import bmesh
import bpy

ICON = "branding/icon-32.png"
OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "branding/logo3d.png"

# Heights per layer, in block units. Amber pixels glow.
TILE, OUTLINE, IRON, AMBER = 0.6, 1.6, 2.2, 3.4


def classify(r, g, b):
    if r > 0.55 and b < 0.45 and r - b > 0.35:
        return "amber"
    if r > 0.25 and abs(r - g) < 0.08 and abs(g - b) < 0.1:
        return "iron"
    if (r, g, b) == (0, 0, 0):
        return None
    return "dark"


def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 256
    scene.cycles.use_denoising = True
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "METAL"
        prefs.get_devices()
        for device in prefs.devices:
            device.use = True
        scene.cycles.device = "GPU"
    except Exception as error:  # CPU fallback keeps the script portable
        print("GPU unavailable, using CPU:", error)
    scene.render.film_transparent = True
    scene.render.resolution_x = 1600
    scene.render.resolution_y = 1600
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.view_settings.view_transform = "AgX"
    scene.view_settings.look = "AgX - Punchy"
    world = bpy.data.worlds.new("World")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.012, 0.016, 0.024, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
    scene.world = world
    return scene


def material(name, metallic, roughness, emission=0.0):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    bsdf = nodes["Principled BSDF"]
    attr = nodes.new("ShaderNodeAttribute")
    attr.attribute_name = "Col"
    mat.node_tree.links.new(attr.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    if emission:
        mat.node_tree.links.new(attr.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = emission
    return mat


def build_voxels():
    image = bpy.data.images.load(bpy.path.abspath("//" + ICON) if bpy.data.filepath else ICON)
    w, h = image.size
    px = list(image.pixels)
    layers = {"dark": [], "iron": [], "amber": []}
    for y in range(h):
        for x in range(w):
            i = ((h - 1 - y) * w + x) * 4  # Blender stores rows bottom-up
            r, g, b, a = px[i:i + 4]
            if a < 0.5:
                continue
            kind = classify(r, g, b)
            if kind is None:
                continue
            layers[kind].append((x, y, (r, g, b)))
    # Outline pixels that touch the hopper or sparkline rise with them instead of staying flat.
    occupied = {(x, y): k for k, items in layers.items() for x, y, _ in items}
    mats = {
        "dark": material("Tile", 0.0, 0.55),
        "iron": material("Iron", 0.35, 0.38),
        "amber": material("Amber", 0.0, 0.35, emission=1.6),
    }
    for kind, items in layers.items():
        mesh = bpy.data.meshes.new(kind)
        bm = bmesh.new()
        colors = []
        for x, y, rgb in items:
            height = {"dark": TILE, "iron": IRON, "amber": AMBER}[kind]
            if kind == "dark" and max(rgb) < 0.06 and any(
                    occupied.get((x + dx, y + dy)) in ("iron", "amber") for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                height = OUTLINE
            cx, cy = x + 0.5, -(y + 0.5)
            verts = [bm.verts.new((cx + sx * 0.5, cy + sy * 0.5, z))
                     for z in (0.0, height) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
            faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
            for f in faces:
                bm.faces.new([verts[k] for k in f])
                colors.append(tuple(srgb_to_linear(c) for c in rgb) + (1.0,))
        bm.to_mesh(mesh)
        bm.free()
        attribute = mesh.color_attributes.new(name="Col", type="FLOAT_COLOR", domain="CORNER")
        for poly in mesh.polygons:
            for loop_index in poly.loop_indices:
                attribute.data[loop_index].color = colors[poly.index]
        obj = bpy.data.objects.new(kind, mesh)
        bpy.context.collection.objects.link(obj)
        obj.data.materials.append(mats[kind])
        bevel = obj.modifiers.new("Bevel", "BEVEL")
        bevel.width = 0.07
        bevel.segments = 3
        bevel.limit_method = "NONE"
    # Stand the tile up like a badge, slightly turned, pivoting on its centre.
    pivot = bpy.data.objects.new("Pivot", None)
    pivot.location = (16, -16, 0)
    bpy.context.collection.objects.link(pivot)
    for kind in layers:
        obj = bpy.data.objects[kind]
        obj.parent = pivot
        obj.location = (-16, 16, 0)
    pivot.rotation_euler = (math.radians(72), 0, math.radians(-18))
    pivot.location = (16, -16, 15)
    return w, h


def area_light(name, location, energy, color, size):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = energy
    data.color = color
    data.size = size
    obj = bpy.data.objects.new(name, data)
    obj.location = location
    bpy.context.collection.objects.link(obj)
    point_at(obj, (16, -16, 15))


def point_at(obj, target):
    direction = [t - o for t, o in zip(target, obj.location)]
    length = math.sqrt(sum(d * d for d in direction))
    dx, dy, dz = (d / length for d in direction)
    obj.rotation_euler = (math.atan2(math.hypot(dx, dy), -dz), 0, math.atan2(dy, dx) - math.pi / 2)


def camera():
    data = bpy.data.cameras.new("Camera")
    data.lens = 62
    cam = bpy.data.objects.new("Camera", data)
    cam.location = (16 + 22, -16 - 92, 22)
    bpy.context.collection.objects.link(cam)
    point_at(cam, (16, -16, 15))
    bpy.context.scene.camera = cam


scene = reset_scene()
build_voxels()
area_light("Key", (-25, -60, 45), 22000, (1.0, 0.95, 0.88), 30)
area_light("Top", (16, -30, 70), 9000, (1.0, 1.0, 1.0), 30)
area_light("Fill", (60, -45, 15), 4000, (0.6, 0.75, 1.0), 20)
area_light("Rim", (16, 25, 40), 9000, (1.0, 0.72, 0.35), 20)
camera()
scene.render.filepath = OUT
bpy.ops.render.render(write_still=True)
print("rendered", OUT)
