import numpy as np, math, json, sys
from scipy import ndimage
from vox import *
from parts_arm import parent_joint_add, parent_joint_cut, cheeks, cheek_cut, bosses, WALL, SV
SZ=98   # oś barku; podniesiona o 6 mm, żeby ucho serwa barku nie zahaczało o talerz
R_RIGHT=[[0,0,-1],[0,1,0],[1,0,0]]; R_LEFT=[[0,0,1],[0,1,0],[-1,0,0]]
# obrót tak, by oś y modelu (pionowa) stała się osią z druku
R_UP=[[1,0,0],[0,0,-1],[0,1,0]]
R_DOWN=[[1,0,0],[0,0,1],[0,-1,0]]   # góra modelu leży na stole
SCREW_ANG=[30,-30,150,210]

def turret(out,h=0.35):
    g=Grid((-46,52.0,-46),(46,SZ+30,46),h)
    prof=[(44.6,52.6),(44.6,58.5),(43,60.5),(36,63),(29,66),(25.5,70),(25,77),(23,84),(12,86.5),(0,87.5)]
    outer=lathe_y(g,prof)|puck(g,SZ,28,54)
    # wnętrze: odległość od brzegu, ale od dołu otwarte (talerz wchodzi w fartuch)
    ext=outer.copy(); bottom=np.argmin(np.abs(g.Y[0,:,0]-52.6))
    pad=np.repeat(outer[:,bottom:bottom+1,:],bottom+1,axis=1); ext[:,:bottom+1,:]=pad
    dist=ndimage.distance_transform_edt(ext,sampling=h)
    shell=outer&~(dist>WALL)
    shell&=~(lathe_y(g,[(42.25,50),(42.25,58.0),(41,58.6),(0,58.7)]))   # gniazdo talerza Ø84,5 × 5,4
    # wnętrze krążka i przegubu barku (z ramką: oś x, środek y=SZ)
    gj=g; shell|=parent_joint_add(gj,SZ,28,54)&outer
    shell&=~parent_joint_cut(gj,SZ,28,54)
    # tulejki łączące połówki wieży
    ba,bc=bosses(g,[(SZ+22,0),(76,14),(76,-14)],lambda y: 27 if y>90 else 20.7)
    shell|=ba&outer; shell&=~bc
    # promieniowe otwory w fartuchu (M3 do talerza)
    for a in SCREW_ANG:
        ca,sa=math.cos(math.radians(a)),math.sin(math.radians(a))
        t=np.clip(g.X*ca+g.Z*sa,0,None); perp=np.abs(-g.X*sa+g.Z*ca)
        shell&=~((np.hypot(perp,g.Y-55.3)<=1.7)&(t>38))
    right=shell&(g.X>=0); left=shell&(g.X<0)
    r={}
    r['wieza_prawa']=save_stl(f'{out}/03_wieza_PETG_szary/wieza_prawa.stl',right,g,R_RIGHT)
    r['wieza_lewa']=save_stl(f'{out}/03_wieza_PETG_szary/wieza_lewa.stl',left,g,R_LEFT)
    # talerz: dno wieży na orczyku serwa obrotu
    g2=Grid((-44,52,-44),(44,59,44),0.3)
    t=lathe_y(g2,[(42,52.6),(42,58),(0,58.01)])
    t&=~cyl(g2,'y',0,0,13.1,52,56.6)          # wnęka na okrągły orczyk Ø26 × 4
    t&=~cyl(g2,'y',0,0,4,52,59)               # dostęp do śruby środkowej orczyka
    t&=~cyl(g2,'y',-30,0,6,52,59)             # przelot kabli (po stronie -x)
    for a in SCREW_ANG:
        ca,sa=math.cos(math.radians(a)),math.sin(math.radians(a))
        tt=g2.X*ca+g2.Z*sa; perp=np.abs(-g2.X*sa+g2.Z*ca)
        t&=~((np.hypot(perp,g2.Y-55.3)<=1.3)&(tt>33))
    r['talerz']=save_stl(f'{out}/03_wieza_PETG_szary/talerz_wiezy.stl',t,g2,R_UP)
    return r

def neck(out,h=0.3):
    # nadgarstek + szyjka: widełki wokół krążka nadgarstka (R25), łącznik, szyjka z MG90S (oś wzdłuż y)
    g=Grid((-38,-22,-26),(38,59,26),h)
    m,hw,ht,s=loft_metric(g,28,42,66,46,30,44)
    outer=cheeks(g,25,30,33)|((m<=1)&(g.Y>=28)&(g.Y<=42))|lathe_y(g,[(23,40),(24.5,45),(25.5,50),(25.5,54),(24.5,56.5),(22,58),(0,58.01)])
    sh=outer.copy()
    sh&=~cheek_cut(g)
    # gniazdo MG90S: korpus 22,8 × 12,2, wys. 23; uszy 32,2 × 2,5; wałek w osi szyjki (x=0)
    sh&=~box(g,5.5-11.7,5.5+11.7,31,54.2,-6.4,6.4)
    sh&=~box(g,5.5-16.4,5.5+16.4,46.4,49.6,-6.4,6.4)
    sh&=~cyl(g,'y',0,0,3.6,53,59)
    sh&=~box(g,9,16,24,32,-4,4)                 # kanał kabla w dół
    sh&=~box(g,9,16,22,26,-4,22)                # wyjście kabla z boku
    # śruby łączące połówki (M3, z lewej)
    for (y,z) in [(36,12.5),(36,-12.5),(52,13.5),(52,-13.5)]:
        sh&=~cyl(g,'x',y,z,1.7,-40,0); sh&=~cyl(g,'x',y,z,3.1,-40,-19.5); sh&=~cyl(g,'x',y,z,1.3,0,19)
    right=sh&(g.X>=0); left=sh&(g.X<0)
    r={}
    r['szyjka_prawa']=save_stl(f'{out}/06_szyjka_PETG_szary/szyjka_prawa.stl',right,g,R_RIGHT)
    r['szyjka_lewa']=save_stl(f'{out}/06_szyjka_PETG_szary/szyjka_lewa.stl',left,g,R_LEFT)
    return r

if __name__=='__main__':
    out=sys.argv[1]
    for k,v in {**turret(out),**neck(out)}.items(): print(k,v,flush=True)
