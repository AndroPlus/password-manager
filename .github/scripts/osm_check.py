"""How many named shops does OpenStreetMap know within 150 m of a few Indian spots?"""
import json, time, urllib.parse, urllib.request

SPOTS = [
    ("Chennai, T Nagar (Ranganathan St)", 13.0418, 80.2341),
    ("Chennai, Velachery main road", 12.9791, 80.2209),
    ("Bengaluru, MG Road", 12.9757, 77.6055),
    ("Coimbatore, Gandhipuram", 11.0168, 76.9675),
    ("Madurai, near Meenakshi temple", 9.9195, 78.1193),
    ("Tiruchirappalli, Chatram bus stand", 10.8297, 78.6913),
    ("Puducherry, MG Road", 11.9346, 79.8301),
    ("Small town: Villupuram", 11.9401, 79.4861),
]
Q = """[out:json][timeout:25];
(node(around:150,{lat},{lon})["name"]["shop"];
 node(around:150,{lat},{lon})["name"]["amenity"~"restaurant|cafe|fast_food|fuel|pharmacy|bank|atm|hospital|clinic"];
 way(around:150,{lat},{lon})["name"]["shop"];);
out tags center 60;"""

for label, lat, lon in SPOTS:
    body = urllib.parse.urlencode({"data": Q.format(lat=lat, lon=lon)}).encode()
    req = urllib.request.Request("https://overpass-api.de/api/interpreter", data=body,
                                 headers={"User-Agent": "expense-tracker-coverage-check/1.0"})
    try:
        els = json.load(urllib.request.urlopen(req, timeout=60))["elements"]
    except Exception as e:
        print(f"== {label}: request failed: {e}")
        continue
    kinds = {}
    for e in els:
        t = e.get("tags", {})
        k = t.get("shop") or t.get("amenity")
        kinds[k] = kinds.get(k, 0) + 1
    print(f"== {label}: {len(els)} named places within 150 m")
    print("   types:", ", ".join(f"{k}:{v}" for k, v in sorted(kinds.items(), key=lambda x: -x[1])[:8]))
    for e in els[:8]:
        t = e.get("tags", {})
        print(f"   - {t.get('name')}  ({t.get('shop') or t.get('amenity')})")
    time.sleep(3)
