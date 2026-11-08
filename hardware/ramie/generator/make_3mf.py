"""Projekty 3MF dla Bambu Studio (jeden plik = jeden stół P2S 256 × 256 mm).

Struktura jak w plikach zapisywanych przez Bambu Studio: siatki w 3D/Objects/object_1.model,
obiekty jako komponenty w 3D/3dmodel.model, nazwy i filamenty części w Metadata/model_settings.config.
Obiekt może mieć kilka części: korpus (filament 1) + wkładka (filament 2, druk wielokolorowy z AMS).
Uruchom: python3 make_3mf.py ../stl ../3mf
"""
import numpy as np, zipfile, uuid, os, sys, glob

BED = 256.0
MARGIN, GAP = 8.0, 6.0


def load_stl(p):
    d = np.fromfile(p, dtype=[('n', '<f4', 3), ('v', '<f4', (3, 3)), ('a', '<u2')], offset=84)
    v = d['v'].reshape(-1, 3).astype(np.float64)
    q, inv = np.unique(np.round(v, 4), axis=0, return_inverse=True)
    return q, inv.reshape(-1, 3)


def mesh_xml(oid, v, f):
    out = [f'  <object id="{oid}" p:UUID="{uuid.uuid4()}" type="model">\n   <mesh>\n    <vertices>\n']
    out += [f'     <vertex x="{x:.4f}" y="{y:.4f}" z="{z:.4f}"/>\n' for x, y, z in v]
    out.append('    </vertices>\n    <triangles>\n')
    out += [f'     <triangle v1="{a}" v2="{b}" v3="{c}"/>\n' for a, b, c in f]
    out.append('    </triangles>\n   </mesh>\n  </object>\n')
    return ''.join(out)


def arrange(sizes):
    """Proste układanie półkami. sizes: [(w, d)]. Zwraca środki (x, y) albo None, gdy się nie mieści."""
    order = sorted(range(len(sizes)), key=lambda i: -sizes[i][1])
    pos = [None] * len(sizes)
    x, y, row_d = MARGIN, MARGIN, 0.0
    for i in order:
        w, d = sizes[i]
        if x + w > BED - MARGIN:
            x, y, row_d = MARGIN, y + row_d + GAP, 0.0
        if y + d > BED - MARGIN or w > BED - 2 * MARGIN:
            return None
        pos[i] = (x + w / 2, y + d / 2)
        x += w + GAP
        row_d = max(row_d, d)
    # wyśrodkuj cały układ na stole
    xs = [p[0] - sizes[i][0] / 2 for i, p in enumerate(pos)] + [p[0] + sizes[i][0] / 2 for i, p in enumerate(pos)]
    ys = [p[1] - sizes[i][1] / 2 for i, p in enumerate(pos)] + [p[1] + sizes[i][1] / 2 for i, p in enumerate(pos)]
    dx = BED / 2 - (min(xs) + max(xs)) / 2
    dy = BED / 2 - (min(ys) + max(ys)) / 2
    return [(px + dx, py + dy) for px, py in pos]


def write_3mf(path, plate_name, objects):
    """objects: [dict(name=..., parts=[(nazwa, ścieżka_stl, filament)])]"""
    meshes, obj_entries, sizes = [], [], []
    next_id = 1
    for o in objects:
        parts = []
        lo, hi = np.full(3, np.inf), np.full(3, -np.inf)
        for pname, stl, ext in o['parts']:
            v, f = load_stl(stl)
            lo, hi = np.minimum(lo, v.min(0)), np.maximum(hi, v.max(0))
            meshes.append(mesh_xml(next_id, v, f))
            parts.append((next_id, pname, ext, len(f)))
            next_id += 1
        obj_entries.append(dict(name=o['name'], parts=parts, lo=lo, hi=hi))
        sizes.append((hi[0] - lo[0], hi[1] - lo[1]))
    pos = arrange(sizes)
    if pos is None:
        raise SystemExit(f'{path}: części nie mieszczą się na jednym stole')

    model = ['<?xml version="1.0" encoding="UTF-8"?>\n<model unit="millimeter" xml:lang="en-US" '
             'xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02" '
             'xmlns:BambuStudio="http://schemas.bambulab.com/package/2021" '
             'xmlns:p="http://schemas.microsoft.com/3dmanufacturing/production/2015/06" requiredextensions="p">\n'
             ' <metadata name="Application">DeskMate generator</metadata>\n'
             ' <metadata name="BambuStudio:3mfVersion">1</metadata>\n'
             f' <metadata name="Title">DeskMate – {plate_name}</metadata>\n <resources>\n']
    build = [' <build>\n']
    cfg = ['<?xml version="1.0" encoding="UTF-8"?>\n<config>\n']
    plate = ['  <plate>\n    <metadata key="plater_id" value="1"/>\n'
             f'    <metadata key="plater_name" value="{plate_name}"/>\n    <metadata key="locked" value="false"/>\n']
    for k, (e, (cx, cy)) in enumerate(zip(obj_entries, pos)):
        oid = next_id + k
        model.append(f'  <object id="{oid}" p:UUID="{uuid.uuid4()}" type="model">\n   <components>\n')
        for pid, _, _, _ in e['parts']:
            model.append(f'    <component p:path="/3D/Objects/object_1.model" objectid="{pid}" '
                         f'p:UUID="{uuid.uuid4()}" transform="1 0 0 0 1 0 0 0 1 0 0 0"/>\n')
        model.append('   </components>\n  </object>\n')
        tx = cx - (e['lo'][0] + e['hi'][0]) / 2
        ty = cy - (e['lo'][1] + e['hi'][1]) / 2
        tz = -e['lo'][2]
        build.append(f'  <item objectid="{oid}" p:UUID="{uuid.uuid4()}" '
                     f'transform="1 0 0 0 1 0 0 0 1 {tx:.4f} {ty:.4f} {tz:.4f}" printable="1"/>\n')
        cfg.append(f'  <object id="{oid}">\n    <metadata key="name" value="{e["name"]}"/>\n'
                   f'    <metadata key="extruder" value="{e["parts"][0][2]}"/>\n')
        for pid, pname, ext, nf in e['parts']:
            cfg.append(f'    <part id="{pid}" subtype="normal_part">\n'
                       f'      <metadata key="name" value="{pname}"/>\n'
                       '      <metadata key="matrix" value="1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1"/>\n'
                       f'      <metadata key="extruder" value="{ext}"/>\n'
                       f'      <mesh_stat face_count="{nf}" edges_fixed="0" degenerate_facets="0" facets_removed="0" facets_reversed="0" backwards_edges="0"/>\n'
                       '    </part>\n')
        cfg.append('  </object>\n')
        plate.append(f'    <model_instance>\n      <metadata key="object_id" value="{oid}"/>\n'
                     f'      <metadata key="instance_id" value="0"/>\n      <metadata key="identify_id" value="{100 + k}"/>\n'
                     '    </model_instance>\n')
    model.append(' </resources>\n')
    build.append(' </build>\n</model>\n')
    plate.append('  </plate>\n')
    cfg += plate + ['</config>\n']
    objfile = ('<?xml version="1.0" encoding="UTF-8"?>\n<model unit="millimeter" xml:lang="en-US" '
               'xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02" '
               'xmlns:BambuStudio="http://schemas.bambulab.com/package/2021" '
               'xmlns:p="http://schemas.microsoft.com/3dmanufacturing/production/2015/06" requiredextensions="p">\n'
               ' <metadata name="BambuStudio:3mfVersion">1</metadata>\n <resources>\n' + ''.join(meshes) +
               ' </resources>\n <build/>\n</model>\n')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        z.writestr('[Content_Types].xml',
                   '<?xml version="1.0" encoding="UTF-8"?>\n<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\n'
                   ' <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\n'
                   ' <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>\n</Types>\n')
        z.writestr('_rels/.rels',
                   '<?xml version="1.0" encoding="UTF-8"?>\n<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\n'
                   ' <Relationship Target="/3D/3dmodel.model" Id="rel-1" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>\n</Relationships>\n')
        z.writestr('3D/_rels/3dmodel.model.rels',
                   '<?xml version="1.0" encoding="UTF-8"?>\n<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\n'
                   ' <Relationship Target="/3D/Objects/object_1.model" Id="rel-1" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>\n</Relationships>\n')
        z.writestr('3D/3dmodel.model', ''.join(model) + ''.join(build))
        z.writestr('3D/Objects/object_1.model', objfile)
        z.writestr('Metadata/model_settings.config', ''.join(cfg))
    return pos


if __name__ == '__main__':
    S, OUT = sys.argv[1], sys.argv[2]
    W = S + '/_wklady_wielokolor'

    def simple(folder, ext=1):
        return [dict(name=os.path.basename(p)[:-4], parts=[(os.path.basename(p)[:-4], p, ext)])
                for p in sorted(glob.glob(f'{S}/{folder}/*.stl'))]

    def with_inlay(folder):
        objs = []
        for p in sorted(glob.glob(f'{S}/{folder}/*.stl')):
            n = os.path.basename(p)[:-4]
            parts = [(n, p, 1)]
            ip = f'{W}/{n}_wklad.stl'
            if os.path.exists(ip):
                parts.append((n + '_wklad', ip, 2))
            objs.append(dict(name=n, parts=parts))
        return objs

    jobs = [
        ('00_test_pasowania.3mf', 'Test pasowania', simple('00_test_pasowania_PETG')),
        ('01_korpus.3mf', 'Korpus', simple('01_korpus_PETG_szary')),
        ('02_dno_ze_stala.3mf', 'Dno z przegródkami', simple('02_korpus_dno_PETG_szary')),
        ('02b_dno_piaskowe.3mf', 'Cokół piaskowy', simple('02b_dno_piaskowe_PETG_szary')),
        ('03_wieza.3mf', 'Wieża', simple('03_wieza_PETG_szary')),
        ('04_ramie.3mf', 'Ramię', with_inlay('04_ramie_PETG_szary')),
        ('05_przedramie.3mf', 'Przedramię', with_inlay('05_przedramie_PETG_szary')),
        ('06_szyjka.3mf', 'Szyjka', simple('06_szyjka_PETG_szary')),
        ('07_uchwyt_telefonu.3mf', 'Uchwyt telefonu', simple('07_uchwyt_telefonu_PETG_szary')),
        ('08_akcenty.3mf', 'Akcenty pomarańczowe', simple('08_akcent_pomaranczowy_PETG')),
        ('09_galki.3mf', 'Gałki czarne', simple('09_galki_czarne_PETG')),
        ('10_tpu.3mf', 'Nakładki TPU', simple('10_TPU')),
    ]
    for fn, name, objs in jobs:
        pos = write_3mf(f'{OUT}/{fn}', name, objs)
        print(fn, len(objs), 'obiektów', 'OK')
