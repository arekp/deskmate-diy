"""Uproszczona kontrola DfAM (FDM): szczelność, nawisy > 45°, powierzchnia na stole, wymiary vs stół P2S.
Pomiar z gotowych plików STL (w orientacji do druku). Grubość ścianek mierzy save_stl (wokseli) – osobno."""
import numpy as np, glob, os, sys, json
BED=(256,256,256)
def load(p):
    d=np.fromfile(p,dtype=[('n','<f4',3),('v','<f4',(3,3)),('a','<u2')],offset=84)
    return d['v'].astype(float)
def check(p,limit=45):
    t=load(p); v=t.reshape(-1,3)
    ext=v.max(0)-v.min(0)
    n=np.cross(t[:,1]-t[:,0],t[:,2]-t[:,0]); a=np.linalg.norm(n,axis=1)/2; nz=n[:,2]/np.maximum(np.linalg.norm(n,axis=1),1e-12)
    zmin=v[:,2].min(); zc=t[:,:,2].mean(1)
    on_bed=(zc<zmin+0.3)&(nz<-0.99)
    over=(nz<-np.sin(np.radians(limit)))&~on_bed
    # krawędzie: szczelność
    q=np.round(v,4); _,inv=np.unique(q,axis=0,return_inverse=True); f=inv.reshape(-1,3)
    e=np.sort(np.concatenate([f[:,[0,1]],f[:,[1,2]],f[:,[2,0]]]),axis=1); _,cnt=np.unique(e,axis=0,return_counts=True)
    return dict(plik=os.path.relpath(p),rozmiar=tuple(np.round(ext,1)),miesci_sie=bool((ext<=np.array(BED)).all()),
                szczelny=bool((cnt==2).all()),stol_cm2=round(a[on_bed].sum()/100,1),nawisy_cm2=round(a[over].sum()/100,1),
                nawisy_proc=round(100*a[over].sum()/a.sum(),1))
if __name__=='__main__':
    root=sys.argv[1]
    for p in sorted(glob.glob(root+'/**/*.stl',recursive=True)):
        print(json.dumps(check(p),ensure_ascii=False))

ROTS={'obecna':np.eye(3),'+x w dół':np.array([[0,0,1],[0,1,0],[-1,0,0]]),'-x w dół':np.array([[0,0,-1],[0,1,0],[1,0,0]]),
      '+y w dół':np.array([[1,0,0],[0,0,1],[0,-1,0]]),'-y w dół':np.array([[1,0,0],[0,0,-1],[0,1,0]]),'do góry nogami':np.array([[1,0,0],[0,-1,0],[0,0,-1]])}
def orient(p,limit=45):
    t0=load(p); out={}
    for k,R in ROTS.items():
        t=t0@R.T; v=t.reshape(-1,3)
        n=np.cross(t[:,1]-t[:,0],t[:,2]-t[:,0]); ln=np.maximum(np.linalg.norm(n,axis=1),1e-12); a=ln/2; nz=n[:,2]/ln
        zmin=v[:,2].min(); zc=t[:,:,2].mean(1)
        bed=(zc<zmin+0.3)&(nz<-0.99); over=(nz<-np.sin(np.radians(limit)))&~bed
        out[k]=(round(a[over].sum()/100,1),round(a[bed].sum()/100,1),round(v[:,2].max()-zmin,1))
    return out
