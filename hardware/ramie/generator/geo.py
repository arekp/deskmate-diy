import numpy as np, math
from PIL import Image

# ---------- macierze ----------
def T(x,y,z): m=np.eye(4); m[:3,3]=[x,y,z]; return m
def RX(a): c,s=math.cos(a),math.sin(a); m=np.eye(4); m[1,1]=c;m[1,2]=-s;m[2,1]=s;m[2,2]=c; return m
def RY(a): c,s=math.cos(a),math.sin(a); m=np.eye(4); m[0,0]=c;m[0,2]=s;m[2,0]=-s;m[2,2]=c; return m
def RZ(a): c,s=math.cos(a),math.sin(a); m=np.eye(4); m[0,0]=c;m[0,1]=-s;m[1,0]=s;m[1,1]=c; return m
D=math.pi/180

class Mesh:
    def __init__(s,v,f,col,name=''): s.v=np.asarray(v,float); s.f=np.asarray(f,int); s.col=np.asarray(col,float); s.name=name
    def tf(s,M):
        v=np.c_[s.v,np.ones(len(s.v))]@M.T; return Mesh(v[:,:3],s.f,s.col,s.name)

def box(w,h,d,col,c=(0,0,0)):
    x,y,z=w/2,h/2,d/2
    v=np.array([[-x,-y,-z],[x,-y,-z],[x,y,-z],[-x,y,-z],[-x,-y,z],[x,-y,z],[x,y,z],[-x,y,z]])+np.array(c)
    f=[[0,2,1],[0,3,2],[4,5,6],[4,6,7],[0,1,5],[0,5,4],[2,3,7],[2,7,6],[1,2,6],[1,6,5],[0,4,7],[0,7,3]]
    return Mesh(v,f,col)
def lathe(prof,col,seg=64):
    # profil (r,y) wokół osi y
    v=[];f=[];n=len(prof)
    for i in range(seg):
        a=2*math.pi*i/seg
        for r,y in prof: v.append([r*math.cos(a),y,r*math.sin(a)])
    for i in range(seg):
        i2=(i+1)%seg
        for j in range(n-1):
            a=i*n+j;b=i*n+j+1;c=i2*n+j;d=i2*n+j+1
            f+= [[a,b,d],[a,d,c]]
    return Mesh(v,f,col)
def cylX(r,x0,x1,col,seg=48):  # walec wzdłuż x
    m=lathe([(0,x0),(r,x0),(r,x1),(0,x1)],col,seg)
    return m.tf(RZ(-math.pi/2))  # oś y -> x
def cylY(r,y0,y1,col,seg=48): return lathe([(0,y0),(r,y0),(r,y1),(0,y1)],col,seg)
def torusX(R,r,col,seg=64,ts=10):
    v=[];f=[]
    for i in range(seg):
        a=2*math.pi*i/seg
        for j in range(ts):
            b=2*math.pi*j/ts
            v.append([r*math.sin(b),(R+r*math.cos(b))*math.cos(a),(R+r*math.cos(b))*math.sin(a)])
    for i in range(seg):
        for j in range(ts):
            a=i*ts+j;b=i*ts+(j+1)%ts;c=((i+1)%seg)*ts+j;d=((i+1)%seg)*ts+(j+1)%ts
            f+=[[a,b,d],[a,d,c]]
    return Mesh(v,f,col)
def puck(R,W,col):
    f=min(7,R*.3);p=[(0,-W/2)]
    for i in range(9):
        a=-math.pi/2+i/8*math.pi/2;p.append((R-f+f*math.cos(a),-W/2+f+f*math.sin(a)))
    for i in range(9):
        a=i/8*math.pi/2;p.append((R-f+f*math.cos(a),W/2-f+f*math.sin(a)))
    p.append((0,W/2))
    return lathe(p,col).tf(RZ(-math.pi/2))

# ---------- maska ażuru (jak w stronie) ----------
def lattice_mask():
    W,H=512,256;PX=W/160;seed=[7]
    def rnd():
        seed[0]=(seed[0]*16807)%2147483647; return seed[0]/2147483647
    pts=[]
    for j in range(6):
        for i in range(12): pts.append(((i+0.15+0.7*rnd())*W/12,(j+0.15+0.7*rnd())*H/6))
    pts=np.array(pts)
    X,Y=np.meshgrid(np.arange(W),np.arange(H))
    ds=[]
    for a,b in pts:
        dx=np.abs(X-a);dx=np.minimum(dx,W-dx);ds.append(np.hypot(dx,Y-b))
    ds=np.sort(np.stack(ds),axis=0);f1,f2=ds[0],ds[1]
    s=np.clip((2.6*PX-(f2-f1))/2+.5,0,1)
    ux=np.minimum(np.minimum(X,W-X),np.abs(X-W/2))
    s[(Y<.09*H)|(Y>H-.09*H)|(ux<1.6*PX)]=1
    def blur(a,r):
        k=2*r+1;a=np.pad(a,((0,0),(r,r)),mode='wrap');c=np.cumsum(a,1);c=np.pad(c,((0,0),(1,0)));a=(c[:,k:]-c[:,:-k])/k
        a=np.pad(a,((r,r),(0,0)),mode='edge');c=np.cumsum(a,0);c=np.pad(c,((1,0),(0,0)));return (c[k:]-c[:-k])/k
    b=blur(blur(s,4),4)
    return np.clip((b-.42)*6+.5,0,1)
MASK=lattice_mask()

def loft(y0,y1,w0,w1,t0,t1,col,lattice=False,inset=0,NR=None,NP=None):
    NR=NR or (90 if lattice else 28); NP=NP or (180 if lattice else 48); n=3.2;v=[];f=[]
    for j in range(NR+1):
        s=j/NR;bul=1+.06*math.sin(math.pi*s)
        hw=(w1+(w0-w1)*(1-s)**2.2)/2-inset;ht=(t0+(t1-t0)*s)/2*bul-inset
        for i in range(NP+1):
            a=2*math.pi*i/NP;c=math.cos(a);sn=math.sin(a)
            v.append([hw*np.sign(c)*abs(c)**(2/n),y0+s*(y1-y0),ht*np.sign(sn)*abs(sn)**(2/n)])
    R1=NP+1
    for j in range(NR):
        for i in range(NP):
            if lattice:
                u=(i+.5)/NP;vv=(j+.5)/NR
                if MASK[min(255,int(vv*256)),min(511,int(u*512))]<.5: continue
            a=j*R1+i;b=a+1;c=a+R1;d=b+R1
            f+=[[a,c,b],[b,c,d]]
    if not inset:
        for jj in (0,NR):
            ci=len(v);v.append([0,y0+jj/NR*(y1-y0),0])
            for i in range(NP): f.append([ci,jj*R1+i,jj*R1+i+1])
    return Mesh(v,f,col)

def merge(ms):
    return ms

def cheek_poly(rc,tw,yEnd,n=40):
    # policzek widełek: okrąg rc wokół osi zwężający się do szerokości tw na wysokości yEnd (współrzędne (z, y))
    p=[(tw/2,yEnd),(rc,0)]
    for i in range(1,n):
        a=-math.pi*i/n; p.append((rc*math.cos(a),rc*math.sin(a)))
    p+=[(-rc,0),(-tw/2,yEnd)]
    return p
def prism(poly,x0,x1,col):
    n=len(poly);v=[];f=[]
    for x in (x0,x1):
        for z,y in poly: v.append([x,y,z])
    cz=np.mean([p[0] for p in poly]);cy=np.mean([p[1] for p in poly])
    c0=len(v);v.append([x0,cy,cz]);c1=len(v);v.append([x1,cy,cz])
    for i in range(n):
        j=(i+1)%n;f+=[[i,j,n+j],[i,n+j,n+i],[c0,j,i],[c1,n+i,n+j]]
    return Mesh(v,f,col)
