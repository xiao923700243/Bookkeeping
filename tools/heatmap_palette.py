"""日历热力图色阶的推导与校验。这是那些色值的出处。

改热力配色时改这里再重跑，然后把输出抄进
  app/src/main/res/values/colors.xml        (浅色)
  app/src/main/res/values-night/colors.xml  (深色)
别直接手调 hex：色阶靠明度讲深浅，相邻两档差了多少肉眼判断不了。

结余有正负 → 这是**双向**色阶：两条色臂 + 中性灰中点，两臂档数相同。
色相取应用自己的 income（绿）/ expense（红）。

校验项移植自 dataviz skill 的 validate_palette.js（OKLab/OKLCH 与 WCAG 对比度），
外加两条本项目自己的约束：每条臂上文字对比度 ≥ 4.5:1，以及两臂逐档等重。
"""
import math

M3 = {
    "light": {"surface": "FEF7FF", "onSurface": "1D1B20"},
    "dark": {"surface": "141218", "onSurface": "E6E0E9"},
}
APP = {"income": "2E7D32", "expense": "D32F2F"}

ORDINAL_MIN_DL = 0.06
INK_MIN_CONTRAST = 4.5  # WCAG normal text


def hex2srgb(h):
    h = h.lstrip("#")
    return [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]


def s2lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lin2s(c):
    c = max(0.0, min(1.0, c))
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def lin(h):
    return [s2lin(c) for c in hex2srgb(h)]


def rel_lum(h):
    r, g, b = lin(h)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    hi, lo = sorted([rel_lum(a), rel_lum(b)], reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def oklab_from_lin(r, g, b):
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    return (
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )


def oklch(h):
    L, a, b = oklab_from_lin(*lin(h))
    return L, math.hypot(a, b)


def okhue(h):
    _, a, b = oklab_from_lin(*lin(h))
    return (math.degrees(math.atan2(b, a)) % 360 + 360) % 360


def oklab_to_lin(L, a, b):
    l_ = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m_ = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s_ = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (
        4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
        -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
        -0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_,
    )


def to_hex(L, C, hue_deg):
    a = C * math.cos(math.radians(hue_deg))
    b = C * math.sin(math.radians(hue_deg))
    rgb = oklab_to_lin(L, a, b)
    if any(c < -0.001 or c > 1.001 for c in rgb):
        return None
    return "".join(f"{round(lin2s(c) * 255):02X}" for c in rgb)


def max_chroma(L, hue_deg):
    """Largest in-gamut chroma at this lightness/hue, by bisection."""
    lo, hi = 0.0, 0.4
    for _ in range(40):
        mid = (lo + hi) / 2
        if to_hex(L, mid, hue_deg) is None:
            hi = mid
        else:
            lo = mid
    return lo


def shared_chroma(lightnesses, hues, chroma_scale, cap):
    """One chroma per step, shared by both arms.

    A diverging scale needs arms of equal *weight*. Taking each arm's own
    max in-gamut chroma makes green steps (high chroma at high lightness in
    sRGB) shout next to much paler red ones, so both arms take the smaller
    of the two caps and the green end stops going neon.
    """
    return [min(min(max_chroma(L, h) for h in hues) * chroma_scale, cap)
            for L in lightnesses]


def build_ramp(hue_deg, lightnesses, chromas):
    return ["#" + to_hex(L, C, hue_deg) for L, C in zip(lightnesses, chromas)]


def check_ordinal(name, ramp, surface, ink):
    Ls = [oklch(c)[0] for c in ramp]
    asc = all(Ls[i] < Ls[i + 1] for i in range(len(Ls) - 1))
    desc = all(Ls[i] > Ls[i + 1] for i in range(len(Ls) - 1))
    gaps = [abs(Ls[i + 1] - Ls[i]) for i in range(len(Ls) - 1)]
    hues = [okhue(c) for c in ramp]
    spread = max(hues) - min(hues)
    if spread > 180:
        spread = 360 - spread
    inks = [contrast(c, ink) for c in ramp]
    print(f"\n{name}  {'/'.join(ramp)}")
    print(f"  L        {[round(l, 3) for l in Ls]}")
    print(f"  monotone {'PASS' if asc or desc else 'FAIL'}   "
          f"min dL {min(gaps):.3f} {'PASS' if min(gaps) >= ORDINAL_MIN_DL else 'FAIL'}   "
          f"hue spread {spread:.0f}deg {'PASS' if spread <= 40 else 'FAIL'}")
    print(f"  ink contrast vs {ink}: {[round(c, 2) for c in inks]}  "
          f"min {min(inks):.2f} {'PASS' if min(inks) >= INK_MIN_CONTRAST else 'FAIL'}")
    print(f"  vs surface {surface}: {[round(contrast(c, surface), 2) for c in ramp]}")
    return min(gaps) >= ORDINAL_MIN_DL and (asc or desc) and spread <= 40 \
        and min(inks) >= INK_MIN_CONTRAST


if __name__ == "__main__":
    income_hue = okhue("#" + APP["income"])
    expense_hue = okhue("#" + APP["expense"])
    print(f"seed hues: income {income_hue:.1f}deg (C {oklch('#' + APP['income'])[1]:.3f}), "
          f"expense {expense_hue:.1f}deg (C {oklch('#' + APP['expense'])[1]:.3f})")

    hues = (income_hue, expense_hue)

    # Light: fills recede toward the near-white surface, dark ink throughout.
    # Gaps of 0.09 leave headroom over the 0.06 floor after gamut clipping rounds
    # a step - at 0.07 the red arm's middle gap computed 0.059 and failed.
    light_L = [0.93, 0.84, 0.75, 0.66]
    light_C = shared_chroma(light_L, hues, 0.85, 0.14)
    light_pos = build_ramp(income_hue, light_L, light_C)
    light_neg = build_ramp(expense_hue, light_L, light_C)
    light_zero = "#E4E1E6"

    # Dark: the same four steps re-anchored to the near-black surface - a
    # sequential ramp flips its anchor in dark rather than inverting in place.
    dark_L = [0.22, 0.2933, 0.3667, 0.44]
    dark_C = shared_chroma(dark_L, hues, 0.85, 0.14)
    dark_pos = build_ramp(income_hue, dark_L, dark_C)
    dark_neg = build_ramp(expense_hue, dark_L, dark_C)
    dark_zero = "#3A383D"

    ok = True
    for mode, pos, neg in (("light", light_pos, light_neg), ("dark", dark_pos, dark_neg)):
        ink = "#" + M3[mode]["onSurface"]
        surf = "#" + M3[mode]["surface"]
        print(f"\n===== {mode} (surface {surf}, ink {ink}) =====")
        ok &= check_ordinal(f"{mode} pos", pos, surf, ink)
        ok &= check_ordinal(f"{mode} neg", neg, surf, ink)
        for label, arm in (("pos", pos), ("neg", neg)):
            print(f"  {label} chroma {[round(oklch(c)[1], 3) for c in arm]}")
        zero = light_zero if mode == "light" else dark_zero
        print(f"\n  zero {zero} ink {contrast(zero, ink):.2f} "
              f"vs surface {contrast(zero, surf):.2f}")

    # Both arms must be equal-weight: same L per step, so one ink serves both.
    for mode, pos, neg in (("light", light_pos, light_neg), ("dark", dark_pos, dark_neg)):
        d = [abs(oklch(p)[0] - oklch(n)[0]) for p, n in zip(pos, neg)]
        print(f"\nequal-arm {mode}: max dL {max(d):.4f} {'PASS' if max(d) < 0.01 else 'FAIL'}")
        ok &= max(d) < 0.01

    print("\nALL CHECKS:", "PASS" if ok else "FAIL")
