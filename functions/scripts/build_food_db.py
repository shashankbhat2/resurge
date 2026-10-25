#!/usr/bin/env python3
"""
Builds functions/data/food-db.json: one per-100 g nutrient table from three open datasets.

  IFCT 2017   Indian Food Composition Tables, ICMR-National Institute of Nutrition (542 foods).
              From the MIT-licensed npm package @ifct2017/compositions@2.0.9.
  CoFID 2021  McCance and Widdowson's Composition of Foods Integrated Dataset, UK (about 2,900 foods,
              including about 200 South Asian dishes). Open Government Licence v3.0.
  INPF 2026   Indian Packaged Foods Nutritional Composition Dataset, Mendeley Data,
              DOI 10.17632/rhswn9z9xr.1 (852 products). CC BY 4.0.

Run from functions/:  python3 scripts/build_food_db.py
Needs openpyxl (pip install openpyxl). Downloads are cached in scripts/.cache and checked by SHA-256.
"""
import csv
import hashlib
import io
import json
import os
import re
import sys
import tarfile
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, ".cache")
OUT = os.path.join(HERE, "..", "data", "food-db.json")

SOURCES = {
    "ifct": {
        "url": "https://registry.npmjs.org/@ifct2017/compositions/-/compositions-2.0.9.tgz",
        "sha256": "623e31d4ea355cc829429c2d8e776fbdb5eba953ea713bad2b01c962d3e2cfd5",
        "file": "ifct-compositions-2.0.9.tgz",
    },
    "cofid": {
        "url": "https://assets.publishing.service.gov.uk/media/60538b91e90e07527df82ae4/McCance_Widdowsons_Composition_of_Foods_Integrated_Dataset_2021..xlsx",
        "sha256": "436e9445ef2adb2a75f3d7edd51302de3adad25385f9795fc94ba58bd030e97d",
        "file": "cofid-2021.xlsx",
    },
    "inpf": {
        "url": "https://data.mendeley.com/public-files/datasets/rhswn9z9xr/files/e0b16335-a9ac-4fee-8b68-70fe7b421952/file_downloaded",
        "sha256": "f64832743d4918c428aab31fb8a3681cd2dee89f3c5f305185ec0edfabe8f303",
        "file": "packaged_foods_india.csv",
    },
}

# Nutrients kept, all per 100 g edible portion. Units: g unless noted.
NUTRIENTS = [
    "kcal", "protein", "carbs", "fat", "fiber", "sugar", "satFat",
    "cholesterol",  # mg
    "sodium", "potassium", "calcium", "iron", "magnesium", "zinc",  # mg
    "vitA",  # µg retinol activity equivalents
    "vitC",  # mg
    "vitD", "vitB12", "folate",  # µg
]


def fetch(key):
    src = SOURCES[key]
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, src["file"])
    if not os.path.exists(path):
        print(f"downloading {key}…", file=sys.stderr)
        with urllib.request.urlopen(src["url"]) as r, open(path, "wb") as f:
            f.write(r.read())
    data = open(path, "rb").read()
    digest = hashlib.sha256(data).hexdigest()
    if src["sha256"] and digest != src["sha256"]:
        sys.exit(f"{key}: checksum mismatch ({digest})")
    return data, digest


def num(v):
    """A numeric cell, or None for blank / not determined. 'Tr' (trace) counts as 0."""
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    s = str(v).strip()
    if s == "" or s.upper() in {"N", "NA", "-"}:
        return None
    if s.lower() in {"tr", "trace"}:
        return 0.0
    s = s.replace(",", "")
    try:
        return float(s)
    except ValueError:
        return None


def rnd(v, digits=3):
    if v is None:
        return None
    return float(f"{v:.{digits}g}") if v != 0 else 0


def scaled(v, factor):
    return None if v is None else v * factor


# ---------- IFCT 2017 ----------

def ifct_foods():
    data, digest = fetch("ifct")
    with tarfile.open(fileobj=io.BytesIO(data)) as t:
        text = t.extractfile("package/index.csv").read().decode("utf-8")
    rows = list(csv.reader(io.StringIO(text)))
    header = [h.split(";")[1].strip() for h in rows[0]]
    foods = []
    for row in rows[1:]:
        r = dict(zip(header, row))
        g = lambda k: num(r.get(k))  # IFCT stores every nutrient in g per 100 g; energy in kJ.
        retinol = g("retol")
        beta = g("cartb")
        vit_a = None
        if retinol is not None or beta is not None:
            vit_a = (retinol or 0) * 1e6 + (beta or 0) * 1e6 / 12
        # "A. Moricha guti; H. Ramdana; Kan. Danthu beeja" → names without the language prefix.
        local = [re.sub(r"^[A-Z][a-z]*\.\s*", "", part.strip()).strip() for part in (r.get("lang") or "").split(";")]
        foods.append({
            "id": f"ifct:{r['code']}",
            "name": r["name"],
            "aliases": sorted({a for a in local if a and len(a) <= 40}),
            "group": r.get("grup") or "",
            "n": {
                "kcal": scaled(g("enerc"), 1 / 4.184),
                "protein": g("protcnt"),
                "carbs": g("choavldf"),
                "fat": g("fatce"),
                "fiber": g("fibtg"),
                "sugar": g("fsugar"),
                "satFat": g("fasat"),
                "cholesterol": None,  # IFCT reports 0 where it wasn't measured; leave it out.
                "sodium": scaled(g("na"), 1e3),
                "potassium": scaled(g("k"), 1e3),
                "calcium": scaled(g("ca"), 1e3),
                "iron": scaled(g("fe"), 1e3),
                "magnesium": scaled(g("mg"), 1e3),
                "zinc": scaled(g("zn"), 1e3),
                "vitA": vit_a,
                "vitC": scaled(g("vitc"), 1e3),
                "vitD": scaled(g("vitd"), 1e6),
                "vitB12": None,
                "folate": scaled(g("folsum"), 1e6),
            },
        })
    return foods, digest


# ---------- CoFID 2021 ----------

def cofid_sheet(wb, name):
    """Rows of one CoFID table keyed by food code, with columns named by their short codes (row 2)."""
    ws = wb[name]
    rows = ws.iter_rows(values_only=True)
    next(rows)
    codes = list(next(rows))
    next(rows)
    out = {}
    for row in rows:
        code = row[0]
        if not code:
            continue
        out[str(code).strip()] = {codes[i]: row[i] for i in range(len(codes)) if codes[i]} | {
            "_name": row[1], "_desc": row[2], "_group": row[3],
        }
    return out


COFID_GROUPS = {
    "A": "Cereals", "B": "Milk and milk products", "C": "Eggs", "D": "Vegetables", "F": "Fruit",
    "G": "Nuts and seeds", "H": "Herbs and spices", "J": "Fish", "M": "Meat", "O": "Fats and oils",
    "P": "Beverages", "Q": "Alcoholic beverages", "S": "Sugars and snacks", "W": "Soups and sauces",
}


def cofid_foods():
    import openpyxl

    data, digest = fetch("cofid")
    wb = openpyxl.load_workbook(io.BytesIO(data), read_only=True)
    prox = cofid_sheet(wb, "1.3 Proximates")
    inorg = cofid_sheet(wb, "1.4 Inorganics")
    vit = cofid_sheet(wb, "1.5 Vitamins")
    foods = []
    for code, p in prox.items():
        i = inorg.get(code, {})
        v = vit.get(code, {})
        g = lambda d, k: num(d.get(k))
        fiber = g(p, "AOACFIB")
        if fiber is None:
            fiber = g(p, "ENGFIB")
        group = str(p.get("_group") or "")
        foods.append({
            "id": f"cofid:{code}",
            "name": str(p["_name"]).strip(),
            "aliases": [],
            "group": COFID_GROUPS.get(group[:1], group),
            "n": {
                "kcal": g(p, "KCALS"),
                "_alcohol": g(p, "ALCO") or 0,
                "protein": g(p, "PROT"),
                "carbs": g(p, "CHO"),
                "fat": g(p, "FAT"),
                "fiber": fiber,
                "sugar": g(p, "TOTSUG"),
                "satFat": g(p, "SATFOD"),
                "cholesterol": g(p, "CHOL"),
                "sodium": g(i, "NA"),
                "potassium": g(i, "K"),
                "calcium": g(i, "CA"),
                "iron": g(i, "FE"),
                "magnesium": g(i, "MG"),
                "zinc": g(i, "ZN"),
                "vitA": g(v, "RETEQU"),
                "vitC": g(v, "VITC"),
                "vitD": g(v, "VITD"),
                "vitB12": g(v, "VITB12"),
                "folate": g(v, "FOLT"),
            },
        })
    return foods, digest


# ---------- Indian packaged foods ----------

def inpf_foods():
    data, digest = fetch("inpf")
    rows = list(csv.DictReader(io.StringIO(data.decode("utf-8-sig"))))
    foods = []
    seen = set()
    for r in rows:
        # Only rows stated per 100 g: other serving sizes are ambiguous (per serving or per 100 g).
        if num(r.get("Serving_Size_g")) != 100:
            continue
        name = re.sub(r"\s+", " ", (r.get("Item name") or "").replace("®", "").replace("\xa0", " ")).strip()
        brand = (r.get("Brand_Name") or "").strip().title()
        kcal = num(r.get("Calories_kcal"))
        if not name or kcal is None or (name.lower(), brand) in seen:
            continue
        seen.add((name.lower(), brand))
        # The dataset writes 0 for values missing from the label; keep 0 only for the core macros.
        opt = lambda k: (lambda x: None if not x else x)(num(r.get(k)))
        foods.append({
            "id": f"inpf:{r['S.No'].strip()}",
            "name": name if brand.lower() in name.lower() else f"{brand} {name}".strip(),
            "aliases": [],
            "group": f"Packaged: {(r.get('Category') or '').title()}",
            "n": {
                "kcal": kcal,
                "protein": num(r.get("Proteins_g")),
                "carbs": num(r.get("Carbohydrates_g")),
                "fat": num(r.get("Total_Fat_g")),
                "fiber": opt("Dietary_Fiber_g"),
                "sugar": num(r.get("Sugar_g")),
                "satFat": num(r.get("Saturated_Fat_g")),
                "cholesterol": opt("Cholesterol_mg"),
                "sodium": opt("Sodium_mg"),
                "potassium": None,
                "calcium": opt("Calcium_mg"),
                "iron": opt("Iron_mg"),
                "magnesium": None,
                "zinc": None,
                "vitA": None,
                "vitC": None,
                "vitD": None,
                "vitB12": None,
                "folate": None,
            },
        })
    return foods, digest


def atwater(n):
    """Energy from the macros: 4 kcal/g protein and carbohydrate, 9 fat, 2 fibre, 7 alcohol."""
    return 4 * (n["protein"] or 0) + 4 * (n["carbs"] or 0) + 9 * (n["fat"] or 0) + 2 * (n["fiber"] or 0) + 7 * n.get("_alcohol", 0)


def macros_ok(n):
    """More than 100 g of macros per 100 g, negative values or sugar above carbohydrate are typos."""
    total = sum(n[k] or 0 for k in ("protein", "carbs", "fat", "fiber")) + n.get("_alcohol", 0)
    if total > 105 or any(isinstance(v, float) and v < 0 for v in n.values()):
        return False
    return (n["satFat"] or 0) <= (n["fat"] or 0) + 1


def energy_ok(n):
    return abs(atwater(n) - n["kcal"]) <= max(50, 0.3 * n["kcal"])


def main():
    ifct, ifct_sha = ifct_foods()
    cofid, cofid_sha = cofid_foods()
    inpf, inpf_sha = inpf_foods()
    foods = []
    dropped = []
    recomputed = []
    for f in ifct + cofid + inpf:
        n = f["n"]
        if n["kcal"] is None or n["protein"] is None or n["fat"] is None:
            continue  # need at least energy, protein and fat to be useful
        if not macros_ok(n):
            dropped.append(f["id"])
            continue
        if not energy_ok(n):
            if f["id"].startswith("inpf:"):
                dropped.append(f["id"])  # label typos are usually in the macros, so the row can't be trusted
                continue
            # Lab tables: the measured macros are right and the energy is missing (IFCT oils) or mistyped.
            n["kcal"] = atwater(n)
            recomputed.append(f["id"])
        n.pop("_alcohol", None)
        row = [f["id"], f["name"], f["group"], f["aliases"]]
        row.append([rnd(n[k]) for k in NUTRIENTS])
        foods.append(row)
    out = {
        "nutrients": NUTRIENTS,
        "sources": {
            "ifct": {"name": "IFCT 2017 (ICMR-NIN)", "license": "Data: ICMR-National Institute of Nutrition; packaged by @ifct2017/compositions 2.0.9 (MIT)", "sha256": ifct_sha},
            "cofid": {"name": "CoFID 2021 (McCance and Widdowson)", "license": "Open Government Licence v3.0", "sha256": cofid_sha},
            "inpf": {"name": "Indian Packaged Foods 2026 (Mendeley Data, doi:10.17632/rhswn9z9xr.1)", "license": "CC BY 4.0", "sha256": inpf_sha},
        },
        "columns": ["id", "name", "group", "aliases", "values"],
        "foods": foods,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    counts = {k: sum(1 for x in foods if x[0].startswith(k + ":")) for k in SOURCES}
    print(f"energy recomputed from macros for {len(recomputed)}: {' '.join(recomputed)}", file=sys.stderr)
    print(f"dropped {len(dropped)} implausible rows: {' '.join(dropped)}", file=sys.stderr)
    print(f"wrote {OUT}: {len(foods)} foods {counts}, {os.path.getsize(OUT) // 1024} KB", file=sys.stderr)


if __name__ == "__main__":
    main()
