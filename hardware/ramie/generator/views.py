from robot import *
from rast import render
from PIL import Image, ImageDraw, ImageFont
ms,q,fr=robot((0,122,168,95,0))
a=render(ms,eye=(720,230,170),tgt=(0,125,40),fov=29,bg=(247,248,249))
ms2,q2,fr2=robot((15,175,190,96,12))
b=render(ms2,eye=(-430,360,520),tgt=(30,125,60),fov=31,bg=(247,248,249))
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',17);fb=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',22)
a=a.crop((0,110,800,600));b=b.crop((0,110,800,600))
big=Image.new('RGB',(1600,590),(247,248,249));big.paste(a,(0,95));big.paste(b,(800,95))
d=ImageDraw.Draw(big)
d.text((24,18),'DeskMate na ramieniu z ażurem Voronoi',font=fb,fill=(27,36,40))
d.text((24,50),'Po lewej poza „Czuwa” z boku, po prawej „Ciekawość” z przodu. Przez otwory w ramieniu i przedramieniu widać serwa (pomarańczowe).',font=font,fill=(85,100,108))
big.save('robot-views.png')
