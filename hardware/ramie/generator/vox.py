# Bryły z wokseli: zajętość -> wygładzenie -> marching cubes -> binarny STL
import numpy as np, math, struct, os
from scipy import ndimage
from skimage import measure

class Grid:
    def __init__(s, lo, hi, h):
        s.h=h; s.lo=np.array(lo,float)
        n=np.ceil((np.array(hi)-s.lo)/h).astype(int)+1; s.n=n
        xs=[s.lo[i]+np.arange(n[i],dtype=np.float32)*h for i in range(3)]
        s.X,s.Y,s.Z=np.meshgrid(*xs,indexing='ij')

def save_stl(path, occ, g, rot=None, sigma=0.6, step=2):
    v=ndimage.gaussian_filter(occ.astype(np.float32), sigma)
    v=np.pad(v,1)
    verts,faces,_,_=measure.marching_cubes(v,0.5,spacing=(g.h,)*3,step_size=step)
    verts=verts-g.h+g.lo
    if rot is not None: verts=verts@np.array(rot,float).T
    verts=verts-[ (verts[:,0].max()+verts[:,0].min())/2,(verts[:,1].max()+verts[:,1].min())/2, verts[:,2].min()]
    tri=verts[faces]; n=np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0]); ln=np.linalg.norm(n,axis=1); ln[ln==0]=1; n/=ln[:,None]
    return write_stl(path,verts,faces)

def write_stl(path,verts,faces):
    tri=verts[faces]; n=np.cross(tri[:,1]-tri[:,0],tri[:,2]-tri[:,0]); ln=np.linalg.norm(n,axis=1); ln[ln==0]=1; n/=ln[:,None]
    os.makedirs(os.path.dirname(path),exist_ok=True)
    with open(path,'wb') as f:
        f.write(b'DeskMate'.ljust(80,b' ')); f.write(struct.pack('<I',len(faces)))
        data=np.zeros(len(faces),dtype=[('n','<f4',3),('v','<f4',(3,3)),('a','<u2')])
        data['n']=n; data['v']=tri; f.write(data.tobytes())
    ext=verts.max(0)-verts.min(0)
    # szczelność: każda krawędź w dokładnie dwóch trójkątach
    e=np.sort(np.concatenate([faces[:,[0,1]],faces[:,[1,2]],faces[:,[2,0]]]),axis=1)
    _,cnt=np.unique(e,axis=0,return_counts=True)
    vol=np.abs(np.sum(np.einsum('ij,ij->i',tri[:,0],np.cross(tri[:,1],tri[:,2]))))/6
    return dict(tris=len(faces),size=tuple(np.round(ext,1)),watertight=bool((cnt==2).all()),vol_cm3=round(vol/1000,1))

# ---------- prymitywy (zwracają maski bool) ----------
def box(g,x0,x1,y0,y1,z0,z1): return (g.X>=x0)&(g.X<=x1)&(g.Y>=y0)&(g.Y<=y1)&(g.Z>=z0)&(g.Z<=z1)
def cyl(g,axis,c1,c2,r,a0,a1):
    # walec wzdłuż osi 'x','y','z'; c1,c2 = środek w dwóch pozostałych osiach (kolejność x,y,z bez osi)
    if axis=='x': A,B,C=g.X,g.Y,g.Z
    elif axis=='y': A,B,C=g.Y,g.X,g.Z
    else: A,B,C=g.Z,g.X,g.Y
    return ((B-c1)**2+(C-c2)**2<=r*r)&(A>=a0)&(A<=a1)
def puck(g,yc,R,W,f=7):
    r=np.sqrt((g.Y-yc)**2+g.Z**2); ax=np.abs(g.X)
    dx=ax-(W/2-f); dr=r-(R-f)
    return np.hypot(np.maximum(dx,0),np.maximum(dr,0))+np.minimum(np.maximum(dx,dr),0)<=f
def loft_metric(g,y0,y1,w0,w1,t0,t1,inset=0,n=3.2):
    s=np.clip((g.Y-y0)/(y1-y0),0,1); bul=1+0.06*np.sin(np.pi*s)
    hw=(w1+(w0-w1)*(1-s)**2.2)/2-inset; ht=(t0+(t1-t0)*s)/2*bul-inset
    m=(np.abs(g.X)/hw)**n+(np.abs(g.Z)/ht)**n
    return m,hw,ht,s
def lathe_y(g,prof,x0=0,z0=0):
    # bryła obrotowa wokół osi y; prof = [(r,y)] posortowane po y (zewnętrzny obrys)
    ys=np.array([p[1] for p in prof]); rs=np.array([p[0] for p in prof])
    R=np.interp(g.Y,ys,rs,left=-1,right=-1)
    return np.hypot(g.X-x0,g.Z-z0)<=R
def convex2d(A,B,poly):
    m=np.ones(A.shape,bool); n=len(poly)
    for i in range(n):
        (a0,b0),(a1,b1)=poly[i],poly[(i+1)%n]
        m&=((a1-a0)*(B-b0)-(b1-b0)*(A-a0))>=0
    return m

# ---------- maska ażuru Voronoi (jak na stronie; pełne pasy na szwie połówek) ----------
def lattice_mask():
    W,H=512,256;PX=W/160;seed=[7]
    def rnd():
        seed[0]=(seed[0]*16807)%2147483647; return seed[0]/2147483647
    pts=[]
    for j in range(6):
        for i in range(12): pts.append(((i+0.15+0.7*rnd())*W/12,(j+0.15+0.7*rnd())*H/6))
    X,Y=np.meshgrid(np.arange(W),np.arange(H)); ds=[]
    for a,b in pts:
        dx=np.abs(X-a);dx=np.minimum(dx,W-dx);ds.append(np.hypot(dx,Y-b))
    ds=np.sort(np.stack(ds),axis=0);f1,f2=ds[0],ds[1]
    s=np.clip((2.6*PX-(f2-f1))/2+.5,0,1)
    ux=np.minimum.reduce([X,W-X,np.abs(X-W/2),np.abs(X-W/4),np.abs(X-3*W/4)])
    s[(Y<.09*H)|(Y>H-.09*H)|(ux<1.6*PX)]=1
    def blur(a,r):
        k=2*r+1;a=np.pad(a,((0,0),(r,r)),mode='wrap');c=np.cumsum(a,1);c=np.pad(c,((0,0),(1,0)));a=(c[:,k:]-c[:,:-k])/k
        a=np.pad(a,((r,r),(0,0)),mode='edge');c=np.cumsum(a,0);c=np.pad(c,((1,0),(0,0)));return (c[k:]-c[:-k])/k
    b=blur(blur(s,4),4)
    return np.clip((b-.42)*6+.5,0,1)
MASK=lattice_mask()
def lattice_holes(g,y0,y1,w0,w1,t0,t1,n=3.2,ylo=None,yhi=None):
    m_out,hw,ht,s=loft_metric(g,y0,y1,w0,w1,t0,t1)
    m_in,_,_,_=loft_metric(g,y0,y1,w0,w1,t0,t1,inset=2.4)
    wall=(m_out<=1.15)&(m_in>0.85)
    cx=np.sign(g.X)*np.abs(g.X/hw)**(n/2); sz=np.sign(g.Z)*np.abs(g.Z/ht)**(n/2)
    u=(np.arctan2(sz,cx)/(2*np.pi))%1.0
    ui=np.clip((u*512).astype(int),0,511); vi=np.clip((s*256).astype(int),0,255)
    hole=MASK[vi,ui]<0.5
    rng=(g.Y>(ylo if ylo is not None else y0))&(g.Y<(yhi if yhi is not None else y1))
    return wall&hole&rng

def ring_mesh(path,ro,ri,t,seg=180):
    """Płaski pierścień lub krążek (ri=0) jako dokładna siatka."""
    a=np.linspace(0,2*np.pi,seg,endpoint=False); v=[];f=[]
    rs=[ro,ri] if ri>0 else [ro]
    for z in (0,t):
        for r in rs:
            for x in a: v.append([r*np.cos(x),r*np.sin(x),z])
    v=np.array(v)
    if ri>0:
        O0,I0,O1,I1=0,seg,2*seg,3*seg
        for i in range(seg):
            j=(i+1)%seg
            f+=[[O0+i,I0+i,I0+j],[O0+i,I0+j,O0+j]]          # spód
            f+=[[O1+i,O1+j,I1+j],[O1+i,I1+j,I1+i]]          # wierzch
            f+=[[O0+i,O0+j,O1+j],[O0+i,O1+j,O1+i]]          # zewn.
            f+=[[I0+i,I1+j,I0+j],[I0+i,I1+i,I1+j]]          # wewn.
    else:
        c0=len(v); c1=c0+1; v=np.vstack([v,[0,0,0],[0,0,t]])
        for i in range(seg):
            j=(i+1)%seg
            f+=[[c0,j,i],[c1,seg+i,seg+j],[i,j,seg+j],[i,seg+j,seg+i]]
    v[:,0]+=ro; v[:,1]+=ro
    return write_stl(path,v,np.array(f))
