import sys,numpy as np
sys.path.insert(0,__import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from geo import Mesh
from rast import render
def load(p):
    d=np.fromfile(p,dtype=[('n','<f4',3),('v','<f4',(3,3)),('a','<u2')],offset=84)
    v=d['v'].reshape(-1,3).astype(float); f=np.arange(len(v)).reshape(-1,3); return Mesh(v,f,(205,212,216))
def show(paths,out,eye,tgt,fov=30,col=None,offs=None):
    ms=[]
    for i,p in enumerate(paths):
        m=load(p)
        if offs: m.v=m.v+np.array(offs[i])
        if col: m.col=np.array(col[i],float)
        # z (druk) -> y (góra w rendererze)
        m.v=m.v[:,[0,2,1]]*np.array([1,1,-1])
        ms.append(m)
    render(ms,eye=eye,tgt=tgt,fov=fov,W=1400,H=1000,bg=(247,248,249)).save(out)
if __name__=='__main__':
    pass
