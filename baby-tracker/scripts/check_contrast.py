#!/usr/bin/env python3
"""
Vérifie les contrastes (WCAG 2.1) des couleurs du thème, en clair et en sombre.
Lit app/src/main/java/app/petitrythme/ui/theme/Theme.kt : chaque couple X / onX doit
atteindre 4.5:1 (texte), et les couleurs de catégorie 3:1 sur leur fond (graphiques, accents).
"""
import re
import sys
from pathlib import Path

THEME = Path(__file__).resolve().parent.parent / "app/src/main/java/app/petitrythme/ui/theme/Theme.kt"


def lum(hex6):
    def ch(c):
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(hex6[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def scheme(src, name):
    block = re.search(name + r" = \w+ColorScheme\((.*?)\n\)", src, re.S).group(1)
    named = {"White": "FFFFFF", "Black": "000000"}
    out = {k: v for k, v in re.findall(r"(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)", block)}
    out.update({k: named[v] for k, v in re.findall(r"(\w+) = Color\.(White|Black)\b", block)})
    return out


def categories(src):
    """Couleurs d'accent par catégorie : (sombre, clair)."""
    return re.findall(r"Category\.(\w+) -> if \(dark\) Color\(0xFF(\w{6})\) else Color\(0xFF(\w{6})\)", src)


src = THEME.read_text(encoding="utf-8")
problems = []
for mode in ("Light", "Dark"):
    s = scheme(src, mode)
    for k, v in s.items():
        on = "on" + k[0].upper() + k[1:]
        if on in s:
            r = ratio(v, s[on])
            if r < 4.5:
                problems.append(f"{mode}: {on} sur {k} = {r:.2f}:1 (< 4.5)")
    for cat, dark, light in categories(src):
        bg = s.get("surfaceContainerLow") or s["surface"]
        r = ratio(dark if mode == "Dark" else light, bg)
        if r < 3:
            problems.append(f"{mode}: catégorie {cat} sur surfaceContainerLow = {r:.2f}:1 (< 3)")

if problems:
    print("Contrastes insuffisants :\n  " + "\n  ".join(problems))
    sys.exit(1)
print("   contrastes du thème : OK (texte ≥ 4.5:1, accents ≥ 3:1, clair et sombre)")
