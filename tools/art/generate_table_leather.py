"""Paint the four-seat tabletop atlases at the solo table's 32 texels per block.

The models take 32x32 UV windows from the top left. Pad each atlas to a mip-safe
128x128 and extend the outside texels, so filtering cannot pull in empty pixels.
"""
from pathlib import Path
import random

from PIL import Image, ImageDraw

TEXTURES = Path(__file__).resolve().parents[2] / "src/main/resources/assets/craftbound/textures/block"
SIZE = 96


def tint(rgb, amount):
    return tuple(max(0, min(255, channel + amount)) for channel in rgb)


def leather(height, dye, copper, seed):
    rng = random.Random(seed)
    im = Image.new("RGB", (SIZE, height))
    draw = ImageDraw.Draw(im)
    # The solo top uses two-pixel leather grain and small warm stitches.
    for z in range(0, height, 2):
        for x in range(0, SIZE, 2):
            draw.rectangle((x, z, x+1, z+1), fill=tint(dye(x, z), rng.choice((-3, -1, 0, 1, 2, 4))))
    draw.rectangle((0, 0, SIZE-1, height-1), outline=(53, 29, 22))
    draw.rectangle((1, 1, SIZE-2, height-2), outline=(118, 77, 49))
    draw.rectangle((2, 2, SIZE-3, height-3), outline=(67, 40, 32))
    for x in range(4, SIZE-4, 4):
        for z in (2, height-3):
            draw.line((x,z,x+1,z), fill=copper)
    for z in range(4, height-4, 4):
        for x in (2, SIZE-3):
            draw.line((x,z,x,z+1), fill=copper)
    for x, dx in ((1,1),(SIZE-2,-1)):
        for z, dz in ((1,1),(height-2,-1)):
            for a,b in ((0,0),(1,0),(2,0),(0,1),(0,2),(1,1)):
                draw.point((x+dx*a,z+dz*b), fill=copper)
            draw.point((x,z), fill=tint(copper,35))
    return im


def duos():
    slate, saddle = (49,80,77), (111,65,43)
    copper=(184,132,81)
    im=leather(32,lambda x,z: slate if z<16 else saddle,copper,71)
    d=ImageDraw.Draw(im)
    # Two seats to either side of a single continuous, saddle-stitched rail.
    for z,ink in ((4,tint(slate,11)),(27,tint(saddle,-13))):
        d.line((5,z,90,z),fill=ink)
    d.line((5,15,90,15),fill=(45,43,37))
    d.line((5,16,90,16),fill=(132,94,60))
    for x in (16,80):
        for z,dy in ((5,1),(26,-1)):
            d.line((x-3,z,x,z+2*dy,x+3,z),fill=tint(copper,-28))
    # Two linked card backs, as on the solo table, with opposing team dyes.
    d.rounded_rectangle((37,7,50,21),radius=1,fill=(57,94,87),outline=(31,47,43))
    d.rectangle((39,9,48,19),outline=(117,150,125))
    d.rounded_rectangle((46,11,59,25),radius=1,fill=(107,60,40),outline=(59,38,28))
    d.rectangle((48,13,57,23),outline=(180,127,77))
    for x,z,color in ((43,14,(162,180,142)),(53,18,copper)):
        d.polygon([(x,z-2),(x+2,z),(x,z+2),(x-2,z)],fill=color)
    return im


def commander():
    plum, brass=(79,48,67),(193,151,91)
    im=leather(96,lambda x,z:plum,brass,107)
    d=ImageDraw.Draw(im)
    def octagon(inset,cut,color):
        a,b=inset,SIZE-1-inset
        pts=[(a+cut,a),(b-cut,a),(b,a+cut),(b,b-cut),(b-cut,b),(a+cut,b),(a,b-cut),(a,a+cut)]
        d.line(pts+[pts[0]],fill=color)
    octagon(5,7,tint(plum,-13));octagon(6,7,tint(plum,11))
    # Each chair sees a tooled card facing it. The four cards meet at one brass seal.
    card=Image.new("RGBA",(96,96))
    c=ImageDraw.Draw(card)
    c.rounded_rectangle((42,28,53,43),radius=1,fill=(96,58,75),outline=(49,32,45))
    c.rectangle((44,30,51,41),outline=(148,104,99))
    c.polygon([(48,32),(50,35),(47,38),(45,35)],fill=brass)
    c.line((45,13,47,15,50,13),fill=(143,103,83))
    for turns in range(4):
        face=card.rotate(turns*90)
        im.paste(face,(0,0),face)
    d.polygon([(47,40),(55,47),(48,55),(40,48)],fill=(52,32,43),outline=(139,97,76))
    d.polygon([(47,43),(52,47),(48,52),(43,48)],fill=brass)
    d.line((45,47,47,45,49,47),fill=tint(brass,30))
    return im


def save_atlas(im, name):
    atlas=Image.new("RGB",(128,128))
    atlas.paste(im)
    atlas.paste(im.crop((im.width-1,0,im.width,im.height)).resize((128-im.width,im.height)),(im.width,0))
    atlas.paste(atlas.crop((0,im.height-1,128,im.height)).resize((128,128-im.height)),(0,im.height))
    atlas.save(TEXTURES / (name+".png"),optimize=True)


if __name__ == "__main__":
    save_atlas(duos(),"team_table_leather")
    save_atlas(commander(),"commander_table_leather")
