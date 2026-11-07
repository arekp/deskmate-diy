import numpy as np, math
from geo import *
SZ,L1,L2,L3,HC=92,110,100,58,30
SHELL=(232,236,238);SERVO=(214,96,44);CAP=(46,54,58);METAL=(176,186,191);BODY=(205,213,217);PART=(110,138,152);PHONE=(38,42,48);PAD=(236,238,240);EYE=(123,231,255)

def ik(r,z,f):
    d=f*D;wr=r-(HC+L3)*math.sin(d);wz=z-(HC+L3)*math.cos(d);dx,dz=wr,wz-SZ;Dd=math.hypot(dx,dz)
    c=(Dd*Dd-L1*L1-L2*L2)/(2*L1*L2);a2=math.acos(max(-1,min(1,c)))
    phi=math.atan2(dx,dz);k=math.atan2(L2*math.sin(a2),L1+L2*math.cos(a2));a1=phi-k
    return a1,a2,d-a1-a2

def body():
    NS,N,RX_,RY_,HB,ST,HS=96,4.2,110,95,52,.46,12
    prof=[(0,0),(.93,0)]+[(.93+.07*math.sin(k/6*math.pi/2),5-5*math.cos(k/6*math.pi/2)) for k in range(1,7)]
    prof+=[(ST+(1-ST)*math.cos(k/20*math.pi/2),HS+(HB-HS)*math.sin(k/20*math.pi/2)**.85) for k in range(1,21)]+[(0,HB)]
    v=[];f=[]
    for sc,h in prof:
        sy=sc*(1-.08*max(0,(h-HS)/(HB-HS)))
        for i in range(NS):
            a=2*math.pi*i/NS;c,s=math.cos(a),math.sin(a)
            v.append([RX_*np.sign(c)*abs(c)**(2/N)*sc,h,RY_*np.sign(s)*abs(s)**(2/N)*sy])
    for r in range(len(prof)-1):
        for i in range(NS):
            a=r*NS+i;b=r*NS+(i+1)%NS;c=a+NS;d=b+NS;f+=[[a,c,b],[b,c,d]]
    return Mesh(v,f,BODY)

def fork(R,W,t,yEnd):
    out=[];rc=R*.8
    for s in (-1,1):
        xc=s*(W/2+2)
        out.append(prism(cheek_poly(rc,t*.9,yEnd),xc-1.5,xc+1.5,SHELL))
        out.append(cylX(rc*.62,xc+s*1.5,xc+s*2.3,CAP))
        out.append(torusX(rc*.78,.8,SERVO,48,6).tf(T(xc+s*1.7,0,0)))
    return out

def pitch_servo(y,bearing):
    o=[box(36,40.7,19.7,SERVO,(3,y-10.35,0)),box(2.5,54,19.7,SERVO,(13,y-10.35,0)),
       cylX(3,21,25.5,METAL,20).tf(T(0,y,0)),cylX(12,25.5,28.5,METAL).tf(T(0,y,0))]
    if bearing: o+=[cylX(11,-27,-20,METAL).tf(T(0,y,0)),cylX(4,-32,-20,CAP,20).tf(T(0,y,0))]
    return o

def head():
    # w układzie uchwytu (CAD x, y przód, z góra) -> three (x, z, y)
    def b(x0,x1,y0,y1,z0,z1,col): return box(x1-x0,z1-z0,y1-y0,col,((x0+x1)/2,(z0+z1)/2,(y0+y1)/2))
    o=[]
    disc=cylY(52,-14.2,0,PART,64).tf(RX(math.pi/2))  # oś y -> z (przód)
    o.append(disc)
    o.append(cylY(50,0,0.8,PAD,64).tf(RX(math.pi/2)))
    o+= [b(-91.5,100,-21,-14.2,-8,8,PART),b(-91.5,-85.5,-24,14.5,-31,31,PART),b(85.5,91.5,-24,14.5,-31,31,PART),
         b(-85.5,-84,0,12,-14,14,(28,31,34)),b(84,85.5,0,12,-14,14,(28,31,34)),
         b(-22,22,-24,-14.2,-12,12,PART),
         b(-81.7,81.7,1.5,10.4,-39.05,39.05,PHONE),
         b(-34,-22,10.4,10.9,3,25,EYE),b(22,34,10.4,10.9,3,25,EYE),b(-9,9,10.4,10.9,-14,-11,EYE)]
    for xx in ((36,48),(-48,-36)):
        o+=[b(xx[0],xx[1],-14.2,12,-44.35,-41.35,PART),b(xx[0],xx[1],12,14.5,-44.35,-38.35,PART)]
    return o

def robot(pose=(0,122,168,95,0), servos=True, shell=True):
    yaw,r,z,f,roll=pose;a1,a2,a3=ik(r,z,f)
    meshes=[]
    def add(lst,M):
        for m in lst: meshes.append(m.tf(M))
    W0=np.eye(4)
    add([body()],W0)
    if servos:
        add([box(40.7,37,19.7,SERVO,(10.35,35.5,0))],W0)
    Y=RY(yaw*D)
    add([lathe([(0,56),(44,56),(44,58.5),(42.5,60.5),(36,63),(27,66),(23,70),(22,77),(20.5,84),(0,86)],SHELL,72)],Y)
    add([puck(28,54,SHELL).tf(T(0,SZ,0))],Y)
    if servos: add(pitch_servo(SZ,True),Y)
    S=Y@T(0,SZ,0)@RX(a1)
    add(fork(28,54,32,36),S)
    add([loft(30,L1,61,46,32,31,SHELL,True,NR=160,NP=320),loft(30,L1,61,46,32,31,SHELL,True,inset=2.4,NR=160,NP=320)],S)
    add([puck(28,54,SHELL).tf(T(0,L1,0))],S)
    if servos: add(pitch_servo(L1,True),S)
    E=S@T(0,L1,0)@RX(a2)
    add(fork(28,54,31,36),E)
    add([loft(30,L2,61,44,31,30,SHELL,True,NR=160,NP=320),loft(30,L2,61,44,31,30,SHELL,True,inset=2.4,NR=160,NP=320)],E)
    add([puck(25,50,SHELL).tf(T(0,L2,0))],E)
    if servos: add(pitch_servo(L2,False),E)
    Wr=E@T(0,L2,0)@RX(a3)
    add(fork(25,50,30,33),Wr)
    add([loft(28,42,57,46,30,44,SHELL)],Wr)
    add([lathe([(0,40),(23,40),(24.5,45),(25.5,50),(25.5,54),(24.5,56.5),(22,58),(0,58)],SHELL,72)],Wr)
    Hd=Wr@T(0,L3,0)@RX(-math.pi/2)@RZ(-roll*D)@T(0,0,24)
    add(head(),Hd)
    return meshes,(a1,a2,a3),dict(S=S,E=E,Wr=Wr)
