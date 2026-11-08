import numpy as np, math
from PIL import Image

def look(eye,tgt,up=(0,1,0)):
    eye=np.array(eye,float);tgt=np.array(tgt,float);f=tgt-eye;f/=np.linalg.norm(f)
    r=np.cross(f,up);r/=np.linalg.norm(r);u=np.cross(r,f)
    M=np.eye(4);M[0,:3]=r;M[1,:3]=u;M[2,:3]=-f;M[:3,3]=-M[:3,:3]@eye;return M

def render(meshes,eye,tgt,fov=30,W=1600,H=1200,bg=(236,240,242),light=(0.4,0.8,0.6),ortho=None):
    V=look(eye,tgt);fy=1/math.tan(fov*math.pi/360);asp=W/H
    zb=np.full((H,W),np.inf);cb=np.zeros((H,W,3));cb[:]=bg;ib=np.full((H,W),-1)
    L1=np.array(light,float);L1/=np.linalg.norm(L1);L2=np.array([-0.6,0.3,-0.5]);L2/=np.linalg.norm(L2)
    for mid,m in enumerate(meshes):
        if len(m.f)==0: continue
        v=np.c_[m.v,np.ones(len(m.v))]@V.T;v=v[:,:3]
        z=-v[:,2]
        if ortho: px=(v[:,0]/ortho*H/2/asp*asp)+W/2; py=H/2-(v[:,1]/ortho*H/2)
        else:
            px=(v[:,0]*fy/asp/z+1)*W/2;py=(1-v[:,1]*fy/z)*H/2
        P=np.c_[px,py];F=m.f
        # normalne w przestrzeni świata
        wv=m.v;n=np.cross(wv[F[:,1]]-wv[F[:,0]],wv[F[:,2]]-wv[F[:,0]]);ln=np.linalg.norm(n,axis=1);ln[ln==0]=1;n/=ln[:,None]
        cen=wv[F].mean(1);toeye=np.array(eye)-cen;flip=np.sum(n*toeye,1)<0;n[flip]*=-1
        sh=0.42+0.5*np.clip(n@L1,0,1)+0.18*np.clip(n@L2,0,1)
        cols=np.clip(m.col[None,:]*sh[:,None],0,255)
        for t in range(len(F)):
            a,b,c=F[t];pa,pb,pc=P[a],P[b],P[c];za,zb_,zc=z[a],z[b],z[c]
            if min(za,zb_,zc)<=1: continue
            x0=max(int(min(pa[0],pb[0],pc[0])),0);x1=min(int(max(pa[0],pb[0],pc[0]))+1,W-1)
            y0=max(int(min(pa[1],pb[1],pc[1])),0);y1=min(int(max(pa[1],pb[1],pc[1]))+1,H-1)
            if x0>x1 or y0>y1: continue
            den=(pb[1]-pc[1])*(pa[0]-pc[0])+(pc[0]-pb[0])*(pa[1]-pc[1])
            if abs(den)<1e-9: continue
            xs=np.arange(x0,x1+1)+.5;ys=np.arange(y0,y1+1)+.5;X,Y=np.meshgrid(xs,ys)
            w0=((pb[1]-pc[1])*(X-pc[0])+(pc[0]-pb[0])*(Y-pc[1]))/den
            w1=((pc[1]-pa[1])*(X-pc[0])+(pa[0]-pc[0])*(Y-pc[1]))/den
            w2=1-w0-w1
            ins=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)
            if not ins.any(): continue
            zz=1/(w0/za+w1/zb_+w2/zc)
            sub=zb[y0:y1+1,x0:x1+1];upd=ins&(zz<sub)
            if not upd.any(): continue
            sub[upd]=zz[upd];cb[y0:y1+1,x0:x1+1][upd]=cols[t];ib[y0:y1+1,x0:x1+1][upd]=mid
    # kontury: zmiana obiektu lub skok głębokości
    e=np.zeros((H,W),bool)
    for dy,dx in ((0,1),(1,0)):
        a=ib[:H-dy,:W-dx];b=ib[dy:,dx:];za=zb[:H-dy,:W-dx];zb2=zb[dy:,dx:]
        d=(a!=b)|(np.abs(np.where(np.isinf(za),1e6,za)-np.where(np.isinf(zb2),1e6,zb2))>np.minimum(za,zb2)*0.04)
        e[:H-dy,:W-dx]|=d
    cb[e]=cb[e]*0.25+np.array([30,40,46])*0.75
    img=Image.fromarray(cb.astype(np.uint8)).resize((W//2,H//2),Image.LANCZOS)
    return img

def project(pt,eye,tgt,fov,W,H):
    V=look(eye,tgt);fy=1/math.tan(fov*math.pi/360);asp=W/H
    v=V@np.array([pt[0],pt[1],pt[2],1.0]);z=-v[2]
    return ((v[0]*fy/asp/z+1)*W/2/2,(1-v[1]*fy/z)*H/2/2)   # w pikselach obrazu po zmniejszeniu 2×
