# Kontrola kolizji: serwo (korpus + uszy) i łożysko 608 względem gotowej skorupy
import numpy as np
from vox import *
import parts_arm as A, parts_rest as R
def servo_vol(g,yc):
    sv=A.SV
    case=box(g,sv['xb'],sv['xt'],yc+sv['yc']-20.35,yc+sv['yc']+20.35,-9.85,9.85)
    tabs=box(g,sv['tab0'],sv['tab1'],yc+sv['yc']-27,yc+sv['yc']+27,-9.85,9.85)
    # otwory w uszach są przy końcach – śruby mogą wchodzić w żeberka, więc liczymy bez nich
    brg=cyl(g,'x',yc,0,11,-26,-19)
    return case|tabs, brg
for name,args in [('ramie',(110,28,28,54,50,32,31,[(45,0),(64,0),(132,0)])),('przedramie',(100,28,25,54,50,31,30,[(42,0),(54,0)]))]:
    g,parts=A.link(name,*args)
    shell=parts[name+'_prawa'][0]|parts[name+'_lewa'][0]
    sv,br=servo_vol(g,args[0])
    print(name,'serwo∩skorupa [mm³]:',round((sv&shell).sum()*g.h**3,1),' łożysko∩skorupa:',round((br&shell).sum()*g.h**3,1))
print('--- czy serwo mieści się w obrysie (wystaje poza obudowę?)')
for name,(L,Rp,Rd,W,w1,t0,t1) in [('ramie',(110,28,28,54,50,32,31)),('przedramie',(100,28,25,54,50,31,30))]:
    g=Grid((-38,-26,-32),(38,L+Rd+2,32),0.35)
    m,hw,ht,s=loft_metric(g,30,L,66,w1,t0,t1)
    outer=((m<=1)&(g.Y>=30)&(g.Y<=L))|puck(g,L,Rd,W)
    from scipy import ndimage
    inner=ndimage.distance_transform_edt(outer,sampling=0.35)>0.8
    sv,br=servo_vol(g,L)
    print(name,'serwo poza wnętrzem [mm³]:',round((sv&~inner).sum()*0.35**3,1),' łożysko:',round((br&~inner).sum()*0.35**3,1))
g=Grid((-46,52.0,-46),(46,R.SZ+30,46),0.35)
prof=[(44.6,52.6),(44.6,58.5),(43,60.5),(36,63),(29,66),(25.5,70),(25,77),(23,84),(12,86.5),(0,87.5)]
outer=lathe_y(g,prof)|puck(g,R.SZ,28,54)
inner=ndimage.distance_transform_edt(outer,sampling=0.35)>0.8
sv,br=servo_vol(g,R.SZ)
tal=lathe_y(g,[(42,52.6),(42,58),(0,58.01)])
print('wieza serwo poza wnętrzem:',round((sv&~inner).sum()*0.35**3,1),' serwo∩talerz:',round((sv&tal).sum()*0.35**3,1))
