from robot import *
from rast import render, project
from PIL import ImageDraw, ImageFont
import numpy as np

def half(m,front):
    c=m.v[m.f].mean(1)[:,2]; keep=(c>0) if front else (c<=0)
    return Mesh(m.v,m.f[keep],m.col)

parts=[]; anchors={}
def add(ms,M=np.eye(4)):
    for m in ms: parts.append(m.tf(M))

# ogniwo-rodzic (koniec ramienia): skorupa ażurowa + krążek, podzielone na połówki
L1loc=T(0,-L1,0)
shell=[loft(30,L1,61,46,32,31,SHELL,True,NR=160,NP=320).tf(L1loc),loft(30,L1,61,46,32,31,SHELL,True,inset=2.4,NR=160,NP=320).tf(L1loc),puck(28,54,SHELL)]
ribs=[box(8.75,5,9.85,SHELL,(18.6,-34.5,-4.9)),box(8.75,5,9.85,SHELL,(18.6,14.5,-4.9))]
ribsF=[box(8.75,5,9.85,SHELL,(18.6,-34.5,4.9)),box(8.75,5,9.85,SHELL,(18.6,14.5,4.9))]
FRONT=125
add([half(m,False) for m in shell]+ribs)
add([half(m,True) for m in shell]+ribsF,T(-75,0,FRONT))
# serwo
SZV=60
servo=[box(36,40.7,19.7,SERVO,(3,-10.35,0)),box(2.5,54,19.7,SERVO,(13,-10.35,0)),cylX(3,21,25.5,METAL,20)]
add(servo,T(0,0,SZV))
# śruby M3 przez uszy (wysunięte w +x)
for yy in (-34.5,14.5):
    for zz in (-5,5):
        add([cylX(1.5,14,26,METAL,12),cylX(2.8,26,29,METAL,16)],T(16,yy,zz+SZV))
# orczyk
HX=34
add([cylX(12,25.5,28.5,METAL)],T(HX,0,SZV))
# łożysko 608 (wysunięte w -x)
BX=-40
add([lathe([(4,-3.5),(11,-3.5),(11,3.5),(4,3.5),(4,-3.5)],METAL,48).tf(RZ(-math.pi/2))],T(-23.5+BX,0,0))
# przedramię z widełkami i trzpieniem M8 (wysunięte w górę)
CY=110
child=fork(28,54,31,36)+[loft(30,85,61,44,31,30,SHELL,True,NR=120,NP=320),loft(30,85,61,44,31,30,SHELL,True,inset=2.4,NR=120,NP=320),cylX(4,-44,-31,CAP,20)]
add(child,T(0,CY,0))
# śruby orczyka do policzka
for a in range(4):
    an=a*math.pi/2+math.pi/4
    add([cylX(1.5,30,40,METAL,12),cylX(2.8,40,42.5,METAL,16)],T(10,CY+8*math.sin(an),8*math.cos(an)))

eye=(150,250,500);tgt=(0,12,50);fov=44;W,H=1800,1700
img=render(parts,eye=eye,tgt=tgt,fov=fov,W=W,H=H,bg=(247,248,249))
d=ImageDraw.Draw(img);font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',17);fb=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',22)
P=lambda p: project(p,eye,tgt,fov,W,H)
def lab(pt,txt,side,ty):
    x,y=P(pt);lines=txt.split('\n');w=max(d.textlength(l,font=font) for l in lines)
    tx=24+w if side<0 else W/2-24-w
    ex=tx+6 if side<0 else tx-6
    d.line([(x,y),(ex,ty)],fill=(85,100,108),width=1);d.ellipse([x-3,y-3,x+3,y+3],fill=(30,40,46))
    ox=24 if side<0 else W/2-24-w
    for i,l in enumerate(lines): d.text((ox,ty-10+i*20),l,font=font,fill=(27,36,40))
def dash(a,b):
    (x0,y0),(x1,y1)=P(a),P(b);n=int(math.hypot(x1-x0,y1-y0)/9)
    for i in range(0,n,2): d.line([(x0+(x1-x0)*i/n,y0+(y1-y0)*i/n),(x0+(x1-x0)*(i+1)/n,y0+(y1-y0)*(i+1)/n)],fill=(194,65,12),width=2)
dash((3,-10,SZV-10),(3,-10,2));dash((-23.5+BX+4,0,0),(-27,0,0));dash((0,CY-22,0),(0,30,0));dash((-75,-45,FRONT-16),(0,-45,16))
lab((-75-12,-55,FRONT+14),'Przednia połówka skorupy\n(zamyka serwo, skręcana\nod spodu 4× M3)',-1,770)
lab((-27,-60,-4),'Tylna połówka skorupy ramienia\n(ażur, żeberka pod uszy serwa)',1,720)
lab((-15,-10,SZV),'Serwo MG996R / DS3218\nwsuwane w kieszeń',-1,500)
lab((27,14.5,5+SZV),'4× M3 przez uszy serwa\ndo żeberek',1,540)
lab((HX+28.5,0,SZV),'Okrągły orczyk 25T\nna wałku serwa',1,460)
lab((-23.5+BX-3,8,0),'Łożysko 608 wciśnięte\nw lewą ściankę krążka',-1,400)
lab((-44,CY,0),'Trzpień M8 w lewym policzku\n(wchodzi w łożysko)',-1,270)
lab((31,CY+18,0),'Prawy policzek przedramienia,\n4× M3 do orczyka',1,320)
lab((0,CY+70,0),'Przedramię z widełkami',1,215)
from PIL import Image as _I
big=_I.new('RGB',(img.width,img.height+100),(247,248,249));big.paste(img,(0,100));d=ImageDraw.Draw(big)
d.text((24,18),'Łokieć: widok rozstrzelony',font=fb,fill=(27,36,40))
d.text((24,50),'Pomarańczowe kreski pokazują, gdzie część wraca przy składaniu.',font=font,fill=(85,100,108))
d.text((24,72),'Bark jest zbudowany tak samo, nadgarstek nie ma łożyska.',font=font,fill=(85,100,108))
big.save('exploded.png')
