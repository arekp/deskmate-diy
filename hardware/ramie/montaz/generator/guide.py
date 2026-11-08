import sys,math,numpy as np,struct,os
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from geo import Mesh,box,cylX,cylY,lathe,T,RX,RY,RZ,D
from rast import render,project
from PIL import Image,ImageDraw,ImageFont
S=os.path.join(os.path.dirname(os.path.abspath(__file__)),'stl')+'/'
SZ,L1,L2,L3=98,110,100,58
GRAY=(205,212,216);GRAY2=(178,188,194);SERVO=(214,96,44);METAL=(176,186,191);STEEL=(80,86,92);KNOB=(52,60,65);RED=(200,58,40);PCB=(40,120,80);SAND=(217,192,140);PART=(110,138,152);PHONE=(38,42,48)
def load(n,col=GRAY):
    b=open(S+n+'.stl','rb').read(); k=struct.unpack('<I',b[80:84])[0]
    v=np.frombuffer(b[84:84+k*50],dtype=[('n','<f4',3),('v','<f4',(3,3)),('a','<u2')])['v'].astype(float).reshape(-1,3)
    return Mesh(v,np.arange(len(v)).reshape(-1,3),col)
CADM=np.array([[1,0,0,0],[0,0,1,0],[0,1,0,0],[0,0,0,1.]])   # CAD (x, y przód, z góra) -> (x, z, y)
# --- osprzęt ---
def servo_pitch():   # MG996R w ramce przegubu (oś x), korpus wzdłuż -y
    return [box(40,40.7,19.7,SERVO,(1.5,-10.35,0)),box(2.5,54,19.7,SERVO,(9.45,-10.35,0)),cylX(3,21.5,26,METAL,16),cylX(12,26,29.5,METAL,40)]
def horn(): return [cylX(12,26,29.5,METAL,40)]
def servo_only(): return [box(40,40.7,19.7,SERVO,(1.5,-10.35,0)),box(2.5,54,19.7,SERVO,(9.45,-10.35,0)),cylX(3,21.5,26,METAL,16)]
def bearing(): return [lathe([(4,-26),(11,-26),(11,-19),(4,-19),(4,-26)],METAL,40).tf(RZ(-math.pi/2))]
def bolt_m8(): # łeb w gnieździe lewego policzka, trzon do łożyska
    return [cylX(4,-31.5,-19.5,STEEL,20),cylX(7.3,-36.8,-31.5,STEEL,6)]
def m3(x0,x1,y,z,head_at,axis='x'):
    # śruba M3 wzdłuż osi; head_at = współrzędna łba
    if axis=='x':
        L=abs(x1-x0); s=1 if x1>x0 else -1
        return [cylX(1.5,min(x0,x1),max(x0,x1),STEEL,12).tf(T(0,y,z)),cylX(2.75,head_at-(1.5 if s>0 else 0)*2,head_at+(0 if s>0 else 3),STEEL,16).tf(T(0,y,z))]
    else:
        return [cylY(1.5,min(x0,x1),max(x0,x1),STEEL,12).tf(T(y,0,z)),cylY(2.75,head_at-3,head_at,STEEL,16).tf(T(y,0,z))]
def tf(lst,M): return [m.tf(M) for m in lst]
def colr(lst,c):
    for m in lst: m.col=np.array(c,float)
    return lst
# --- rysowanie ---
FONT=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',16);FB=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',22)
class Step:
    def __init__(s,title,sub,eye,tgt,fov=36,W=1700,H=1300):
        s.title,s.sub,s.eye,s.tgt,s.fov,s.W,s.H=title,sub,eye,tgt,fov,W,H; s.ms=[];s.labels=[];s.dashes=[]
    def add(s,lst): s.ms+=lst
    def lab(s,p,txt,side,ty): s.labels.append((p,txt,side,ty))
    def dash(s,a,b): s.dashes.append((a,b))
    def save(s,path):
        img=render(s.ms,eye=s.eye,tgt=s.tgt,fov=s.fov,W=s.W,H=s.H,bg=(250,250,250))
        import textwrap
        Wd=img.width; subl=textwrap.wrap(s.sub,width=int(Wd/9.6)); band=58+22*len(subl)
        big=Image.new('RGB',(Wd,img.height+band),(250,250,250)); big.paste(img,(0,band)); d=ImageDraw.Draw(big)
        P=lambda p:(lambda q:(q[0],q[1]+band))(project(p,s.eye,s.tgt,s.fov,s.W,s.H))
        for a,b in s.dashes:
            (x0,y0),(x1,y1)=P(a),P(b); n=max(2,int(math.hypot(x1-x0,y1-y0)/8))
            for i in range(0,n,2): d.line([(x0+(x1-x0)*i/n,y0+(y1-y0)*i/n),(x0+(x1-x0)*(i+1)/n,y0+(y1-y0)*(i+1)/n)],fill=(194,65,12),width=2)
        for p,txt,side,ty in s.labels:
            x,y=P(p); ty=band+min(0.95,max(0.05,ty/800))*img.height; lines=txt.split('\n'); w=max(d.textlength(l,font=FONT) for l in lines)
            if side<0: tx=18; ex=tx+w+6
            else: tx=Wd-18-w; ex=tx-6
            d.line([(x,y),(ex,ty)],fill=(90,104,112),width=1); d.ellipse([x-3,y-3,x+3,y+3],fill=(30,40,46))
            for i,l in enumerate(lines): d.text((tx,ty-10+i*19),l,font=FONT,fill=(27,36,40))
        d.text((18,14),s.title,font=FB,fill=(27,36,40))
        for i,l in enumerate(subl): d.text((18,48+i*22),l,font=FONT,fill=(85,100,108))
        big.save(path)
