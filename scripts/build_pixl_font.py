#!/usr/bin/env python3
"""
PixL Font Forge
Transforms Bitcount's circular dots into sharp square pixel blocks.
Derived from Bitcount by Petr van Blokland (TYPETR) under the SIL Open Font License v1.1.
Adapted with square matrix geometry by PixL.
"""

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph, GlyphCoordinates
from fontTools.ttLib.tables import ttProgram
from fontTools.varLib.mutator import instantiateVariableFont
import os

def build_pixl_font():
    src_path = "app/src/main/res/font/bitcount_prop_single.ttf"
    dst_path = "app/src/main/res/font/pixl_font.ttf"
    
    print(f"Loading base font from {src_path}...")
    font = TTFont(src_path)
    
    # Instantiate at weight=700 (Bold) for punchy, crisp glyph strokes
    print("Instantiating static font at wght=700...")
    inst = instantiateVariableFont(font, {
        'wght': 700.0,
        'slnt': 0.0,
        'CRSV': 0.0,
        'ELSH': 0.0,
        'ELXP': 0.0
    })
    
    # Square pixel geometry:
    # Grid cell size is 100x100.
    # Hairline gap: square from (8, 8) to (92, 92), leaving an 8-unit border (16-unit gap between adjacent pixels).
    coords = GlyphCoordinates([(8, 8), (8, 92), (92, 92), (92, 8)])
    px_glyph = Glyph()
    px_glyph.numberOfContours = 1
    px_glyph.endPtsOfContours = [3]
    px_glyph.flags = bytearray([1, 1, 1, 1])
    px_glyph.coordinates = coords
    px_glyph.program = ttProgram.Program()
    
    # Assign square glyph to atomic 'px' component
    inst['glyf']['px'] = px_glyph
    
    # Update Name table metadata with full legal attribution to TYPETR under SIL OFL 1.1
    name_table = inst['name']
    
    metadata = {
        0: "Copyright (c) 2026 PixL. Portions Copyright (c) Petr van Blokland (TYPETR).",
        1: "PixL Font",
        2: "Regular",
        3: "PixL: PixL Font Regular: 2026",
        4: "PixL Font Regular",
        5: "Version 1.000;PixL",
        6: "PixLFont-Regular",
        7: "PixL is a trademark of PixL.",
        8: "PixL",
        9: "Petr van Blokland, PixL",
        10: "PixL Font. Derived from Bitcount by Petr van Blokland (TYPETR) under the SIL Open Font License v1.1. Transformed with square pixel matrix geometry by PixL.",
        11: "https://github.com/pixlofficial",
        12: "https://github.com/pixlofficial",
        13: "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
        14: "http://scripts.sil.org/OFL",
        16: "PixL Font",
        17: "Regular",
    }
    
    for name_id, text in metadata.items():
        name_table.setName(text, name_id, platformID=3, platEncID=1, langID=0x409)
        name_table.setName(text, name_id, platformID=1, platEncID=0, langID=0)
        
    os.makedirs(os.path.dirname(dst_path), exist_ok=True)
    print(f"Saving compiled PixL Font to {dst_path}...")
    inst.save(dst_path)
    print("PixL Font successfully forged!")

if __name__ == "__main__":
    build_pixl_font()
