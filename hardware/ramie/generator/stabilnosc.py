# Stabilność: przesunięcie środka masy całego robota względem krawędzi podstawy
import math
D=math.pi/180; SZ=98+9; L1,L2,L3,HC=110,100,58,30   # SZ = oś barku nad blatem z dnem v0.2
def ik(r,z,f):
    d=f*D;wr=r-(HC+L3)*math.sin(d);wz=z-(HC+L3)*math.cos(d);dx,dz=wr,wz-SZ;Dd=min(math.hypot(dx,dz),(L1+L2)*.995)
    c=(Dd*Dd-L1*L1-L2*L2)/(2*L1*L2);a2=math.acos(max(-1,min(1,c)));phi=math.atan2(dx,dz);k=math.atan2(L2*math.sin(a2),L1+L2*math.cos(a2));a1=phi-k
    return a1,a2,d-a1-a2
# masy [kg] (PETG 1,27 g/cm³; skorupy prawie pełne, części grube ok. 50% przez wypełnienie)
base=dict(korpus=0.19,dno=0.08,elektronika=0.17,serwo_obrotu=0.055,wieza=0.07,serwo_barku=0.06)
def arm_moment(r,z,f):
    a1,a2,a3=ik(r,z,f); E=L1*math.sin(a1); W=E+L2*math.sin(a1+a2); d=a1+a2+a3; J=W+L3*math.sin(d); H=J+HC*math.sin(d)
    parts=[(0.075,E/2+5),(0.06,E),(0.07,(E+W)/2),(0.055,W),(0.065,(W+J)/2),(0.015,J),(0.35,H)]
    return sum(m*x for m,x in parts), sum(m for m,_ in parts), H
M0=sum(base.values())
print(f"masa podstawy {M0:.2f} kg")
poses={'czuwa':(122,168,95),'patrzy':(120,215,80),'ciekawosc':(175,190,96),'klawiatura':(170,135,132),'maks. wysiegu':(230,150,90)}
for ballast in (0,0.5,0.8):
    print(f"--- obciążnik {ballast*1000:.0f} g")
    for k,p in poses.items():
        mom,ma,H=arm_moment(*p); Mt=M0+ma+ballast; x=mom/Mt
        for name,edge in (('przód',88),('bok (obrót 90°)',102)):
            dyn=1.6*mom/Mt   # szybki gest: ok. 1,6× moment statyczny (szacunek)
            print(f"  {k:14s} {name:16s} środek masy {x:5.1f} mm od osi, krawędź {edge} mm, zapas {edge-x:5.1f} mm statycznie, {edge-dyn:5.1f} mm w szybkim geście, kąt przewrócenia ok. {math.degrees(math.atan2(edge-x,45)):.0f}°")
