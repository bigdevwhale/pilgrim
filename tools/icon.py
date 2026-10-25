"""Writes the launcher icon vectors: a cinnabar stamp with a domed silhouette on laid paper.

    python tools/icon.py
then `./gradlew testDebugUnitTest -Prender --tests '*RenderArtIcon*'` draws docs/logo.png from them.
"""
from pathlib import Path

DRAW = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"
HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n')
INK = "#FFA8342A"


def ring(cx, cy, r, w):
    o, i = r, r - w
    return (f"M{cx},{cy - o}A{o},{o} 0,1 1,{cx},{cy + o}A{o},{o} 0,1 1,{cx},{cy - o}Z"
            f"M{cx},{cy - i}A{i},{i} 0,1 0,{cx},{cy + i}A{i},{i} 0,1 0,{cx},{cy - i}Z")


def main():
    p = [
        (INK, ring(54, 54, 30, 3.2)),
        (INK, ring(54, 54, 25.5, 1.1)),
        # A domed house of prayer of no faith in particular: body, drum, a round dome, a needle; the door cut in paper colour.
        (INK, "M42,69.4h24v-11.4h-24z"),
        (INK, "M46.5,58h15v-5.5h-15z"),
        (INK, "M45.5,53A8.5,8.5 0,0 1,62.5 53Z"),
        (INK, "M53.4,45h1.2v-6h-1.2z"),
        (INK, "M38,69.4h32v1.4h-32z"),
        ("#FFF2E9D5", "M51.2,69.4v-4.6a2.8,2.8 0,0 1,5.6 0v4.6z"),
    ]
    # Little diamonds on the band.
    for x in (25.8, 82.2):
        p.append((INK, f"M{x},52l1.6,2l-1.6,2l-1.6,-2z"))
    body = "\n".join(f'    <path android:fillColor="{c}" android:fillType="evenOdd" android:pathData="{d}" />' for c, d in p)
    (DRAW / "ic_launcher_foreground.xml").write_text(HEAD + body + "\n</vector>\n", encoding="utf-8", newline="\n")
    (DRAW / "ic_launcher_background.xml").write_text(HEAD + '    <path android:fillColor="#FFF2E9D5" android:pathData="M0,0h108v108h-108z" />\n</vector>\n', encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
