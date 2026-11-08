import numpy as np, math, json, sys
from scipy import ndimage
from vox import *

WALL=2.4
# Serwo MG996R w ramce przegubu: oś obrotu = x, wałek po stronie +x.
# Korpus x -18.5..21.5 (40), dł. 40.7 wzdłuż y (środek y=-10.35), szer. 19.7 wzdłuż z.
# Uszy: spód x=8.2, wierzch 10.7; otwory co 49.5 mm wzdłuż y (y=-35.1 i 14.4), z=±5.
SV=dict(xb=-18.5,xt=21.5,tab0=8.2,tab1=10.7,yc=-10.35,holes_y=(-35.1,14.4),holes_z=(-5,5))

def parent_joint_add(g,yc,R,W):
    """Dodatki w krążku-rodzicu: żeberka pod uszy serwa i piasta łożyska 608."""
    add=np.zeros(g.X.shape,bool)
    inner=W/2-WALL
    for hy in SV['holes_y']:
        add|=box(g,SV['tab1'],inner+0.5,yc+hy-3.5,yc+hy+3.5,-9.85,9.85)
    add|=cyl(g,'x',yc,0,14,-W/2,-18.6)          # piasta łożyska
    return add
def parent_joint_cut(g,yc,R,W):
    cut=np.zeros(g.X.shape,bool)
    for hy in SV['holes_y']:
        for hz in SV['holes_z']:
            cut|=cyl(g,'x',yc+hy,hz,1.3,SV['tab1']-0.1,22)    # otwory pilotowe M3 w żeberkach
    cut|=cyl(g,'x',yc,0,13.5,W/2-WALL-1,W/2+1)               # otwór na orczyk Ø27 w ściance +x
    cut|=cyl(g,'x',yc,0,11.08,-26,-18.0)                      # gniazdo łożyska 608 Ø22,15 × 7
    cut|=cyl(g,'x',yc,0,6,-W/2-1,-25.9)                       # przelot śruby M8 przez kołnierz
    # wnęka na serwo (zapewnia luz, gdyby EDT zostawił materiał)
    cut|=box(g,SV['xb']-0.3,SV['xt']+0.3,yc+SV['yc']-20.65,yc+SV['yc']+20.65,-10.2,10.2)
    cut|=box(g,SV['tab0']-0.3,SV['tab1']+0.3,yc+SV['yc']-27.3,yc+SV['yc']+27.3,-10.2,10.2)
    return cut

def cheeks(g,Rp,t,yEnd):
    """Widełki ogniwa-dziecka wokół krążka poprzedniego przegubu (oś w y=0).
    Prawy policzek x 30..33 (do orczyka), lewy x -39..-30 (gniazdo łba śruby M8 + miejsce na czop gałki)."""
    rc=0.8*Rp; tw=t*0.9
    poly=[(tw/2,yEnd),(-tw/2,yEnd),(-rc,0),(rc,0)]   # (z,y) – kolejność przeciwna do ruchu wskazówek
    shape=convex2d(g.Z,g.Y,[(p[0],p[1]) for p in poly])|((g.Z**2+g.Y**2)<=rc*rc)
    right=shape&(g.X>=30)&(g.X<=33)
    left=shape&(g.X>=-39)&(g.X<=-30)
    return right|left
def cheek_cut(g):
    c=cyl(g,'x',0,0,4.0,29,34)                                   # dostęp do śruby orczyka
    c|=cyl(g,'x',0,0,4.2,-40,-29)                                # śruba M8 × 12
    # gniazdo sześciokątne łba M8 (13 mm pod klucz), głęb. 7,5 od zewnątrz: łeb 5,3 + czop gałki 2
    ang=np.arctan2(g.Z,g.Y); r=np.hypot(g.Y,g.Z)
    hexr=6.65/np.cos(((ang+np.pi/6)%(np.pi/3))-np.pi/6)
    c|=(r<=hexr)&(g.X>=-39.5)&(g.X<=-31.5)
    return c

def bosses(g,pts,hwfun):
    """Tulejki łączące połówki (wzdłuż x). Zwraca (dodaj, wytnij)."""
    add=np.zeros(g.X.shape,bool); cut=np.zeros(g.X.shape,bool)
    for (y,z) in pts:
        hw=hwfun(y)
        add|=cyl(g,'x',y,z,3.6,-hw,hw)
        add|=cyl(g,'x',y,z,6.0,-hw,-hw+WALL+0.6)|cyl(g,'x',y,z,6.0,hw-WALL-0.6,hw)  # pełne łaty w ażurze
        cut|=cyl(g,'x',y,z,1.7,-hw-2,0)               # przelot M3 w lewej połówce
        cut|=cyl(g,'x',y,z,3.1,-hw-2,-hw+3.2)          # pogłębienie na łeb
        cut|=cyl(g,'x',y,z,1.3,0,hw-1.6)               # otwór pilotowy w prawej połówce
    return add,cut

def side_band(g,y0,L,w0,w1,t0,t1,a,b,depth,side,half_z):
    """Obszar przy bocznej ściance ogniwa (|z|<=half_z, y w [a,b] długości): warstwa 'depth' pod powierzchnią."""
    m_out,hw,ht,s=loft_metric(g,y0,L,w0,w1,t0,t1)
    m_in,_,_,_=loft_metric(g,y0,L,w0,w1,t0,t1,inset=depth)
    yr=(g.Y>=y0+a*(L-y0))&(g.Y<=y0+b*(L-y0))
    return (m_out<=1)&(m_in>1)&(np.abs(g.Z)<=half_z)&yr&(side*g.X>0)
def text_mask(g,text,y_c,height,side,font='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'):
    """Maska napisu na boku ogniwa: kierunek czytania +y, góra liter +z (czytelny z zewnątrz po stronie +x)."""
    from PIL import Image,ImageDraw,ImageFont
    px=20; f=ImageFont.truetype(font,int(height*px*1.35))
    w=int(f.getlength(text))+20; im=Image.new('L',(w,int(height*px*2)),0); d=ImageDraw.Draw(im)
    bb=d.textbbox((0,0),text,font=f); d.text((10-bb[0],(im.height-(bb[3]-bb[1]))/2-bb[1]),text,font=f,fill=255)
    a=np.array(im)>127; ys,xs=np.nonzero(a); a=a[ys.min():ys.max()+1,xs.min():xs.max()+1]
    H,Wd=a.shape; sc=height/H; length=Wd*sc
    col=((g.Y-(y_c-length/2))/sc).astype(int)
    row=((height/2-g.Z)/sc).astype(int) if side>0 else ((height/2-g.Z)/sc).astype(int)
    if side<0: col=Wd-1-col                                        # po stronie -x lustrzanie, żeby czytać z zewnątrz
    ok=(col>=0)&(col<Wd)&(row>=0)&(row<H)
    m=np.zeros(g.X.shape,bool); m[ok]=a[row[ok],col[ok]]
    return m,length

def link(name,L,Rp,Rd,W,w1,t0,t1,boss_pts,h=0.35,deco=None):
    """Ogniwo: widełki wokół przegubu y=0 (krążek Rp), ażurowa skorupa y 30..L, krążek Rd z serwem w y=L."""
    w0=66
    g=Grid((-42,-26,-32),(38,L+Rd+2,32),h)
    m,hw,ht,s=loft_metric(g,30,L,w0,w1,t0,t1)
    outer=((m<=1)&(g.Y>=30)&(g.Y<=L))|puck(g,L,Rd,W)|cheeks(g,Rp,t0,36)
    # skorupa: wydrążenie przez odległość od brzegu (ścianka 2,4 mm); wyjątek: widełki pozostają pełne
    dist=ndimage.distance_transform_edt(outer,sampling=h)
    shell=outer&~(dist>WALL)
    shell&=~lattice_holes(g,30,L,w0,w1,t0,t1,ylo=36,yhi=L-Rd+2)
    shell|=parent_joint_add(g,L,Rd,W)&outer
    def hwfun(y):
        if y>L-Rd+4: return W/2
        ss=(y-30)/(L-30); return (w1+(w0-w1)*(1-ss)**2.2)/2
    ba,bc=bosses(g,boss_pts,hwfun)
    shell|=ba&outer
    shell&=~parent_joint_cut(g,L,Rd,W)
    shell&=~cheek_cut(g)
    shell&=~bc
    # ozdoby v0.2: rowek 0,8 mm na czerwoną listwę (a,b = zakres długości) i wgłębiony napis; wkładki do druku wielokolorowego
    inlay=np.zeros(g.X.shape,bool)
    for kind,side,arg in (deco or []):
        if kind=='listwa':
            gr=side_band(g,30,L,w0,w1,t0,t1,0.38,0.80,0.8,side,1.75)
            shell&=~gr; inlay|=side_band(g,30,L,w0,w1,t0,t1,0.38,0.80,0.8,side,1.75)
        if kind=='napis':
            tm,_=text_mask(g,arg,55.0,5.5,side)   # napis 5,5 mm, przed krążkiem
            gr=side_band(g,30,L,w0,w1,t0,t1,0.0,1.0,0.6,side,3.2)&tm
            shell&=~gr; inlay|=gr
    right=shell&(g.X>=0); left=shell&(g.X<0)
    R_right=[[0,0,-1],[0,1,0],[1,0,0]]   # x -> wysokość druku
    R_left=[[0,0,1],[0,1,0],[-1,0,0]]
    out={name+'_prawa':(right,R_right),name+'_lewa':(left,R_left)}
    if inlay.any():
        out[name+'_prawa_wklad']=(inlay&(g.X>=0),R_right,name+'_prawa')
        out[name+'_lewa_wklad']=(inlay&(g.X<0),R_left,name+'_lewa')
    return g,out

if __name__=='__main__':
    out=sys.argv[1]; which=sys.argv[2]
    if which=='ramie':
        g,parts=link('ramie',110,28,28,54,50,32,31,[(45,0),(110-46,0),(110+22,0)],deco=[('listwa',1,None),('listwa',-1,None)])
        folder='04_ramie_PETG_szary'
    if which=='przedramie':
        g,parts=link('przedramie',100,28,25,54,50,31,30,[(42,0),(100-46,0)],deco=[('napis',1,'DeskMate'),('listwa',-1,None)])
        folder='05_przedramie_PETG_szary'
    offs={}
    for k,v in parts.items():
        if len(v)==2:
            occ,rot=v; r=save_stl(f'{out}/{folder}/{k}.stl',occ,g,rot); offs[k]=r['offset']; print(k,r,flush=True)
    for k,v in parts.items():
        if len(v)==3 and v[0].any():
            occ,rot,base=v
            r=save_stl(f'{out}/_wklady_wielokolor/{k}.stl',occ,g,rot,offset=offs[base],step=1); print(k,r,flush=True)
