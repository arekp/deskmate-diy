# Eksport części w układzie montażowym (bez obrotu do druku, bez przesunięcia), siatka zgrubna do renderów.
import sys,os
HERE=os.path.dirname(os.path.abspath(__file__))
G=os.path.join(HERE,'..','..','generator'); sys.path.insert(0,G); os.chdir(G)
OUT=os.path.join(HERE,'stl'); os.makedirs(OUT,exist_ok=True)
import vox
_orig=vox.save_stl
def patched(path,occ,g,rot=None,sigma=0.6,step=2,offset=None):
    name=os.path.basename(path)
    return _orig(os.path.join(OUT,name),occ,g,None,sigma,3,[0,0,0])
import parts_arm as A, parts_rest as R, parts_body_head as B
for m in (A,R,B,vox): m.save_stl=patched
for nm,args,deco in [('ramie',(110,28,28,54,50,32,31,[(45,0),(64,0),(132,0)]),[('listwa',1,None),('listwa',-1,None)]),
                     ('przedramie',(100,28,25,54,50,31,30,[(42,0),(54,0)]),[('napis',1,'DeskMate'),('listwa',-1,None)])]:
    g,parts=A.link(nm,*args,deco=deco)
    for k,v in parts.items():
        if v[0].any(): patched(k+'.stl',v[0],g)
R.turret(OUT); R.neck(OUT); B.body(OUT); B.plinth(OUT); B.holder(OUT)
for nm,rc in (('R28',22.4),('R25',20.0)):
    B.knob(f'{OUT}/galka_{nm}_prawa.stl',rc,1); B.knob(f'{OUT}/galka_{nm}_lewa.stl',rc,-1)
print('ok')
