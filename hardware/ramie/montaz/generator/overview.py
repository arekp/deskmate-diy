from guide import *
from steps import YAWS
a1,a2,a3=math.radians(-12),math.radians(98),math.radians(4)
s=Step('Mapa części','Numery to katalogi z plikami do druku (stl/ i 3mf/). Robot w pozie „Czuwa”, bez telefonu w szczękach.',eye=(640,300,170),tgt=(0,140,60),fov=34,W=1800,H=1500)
s.add([load('dno_piaskowe',GRAY2),load('korpus',GRAY),load('talerz_wiezy',GRAY),load('wieza_prawa',GRAY),load('wieza_lewa',GRAY2)])
M1=T(0,SZ,0)@RX(a1); M2=M1@T(0,L1,0)@RX(a2); M3=M2@T(0,L2,0)@RX(a3)
def kn(M,rc_name):
    return [load(f'galka_{rc_name}_prawa',KNOB).tf(M@T(33,0,0)@RY(math.pi/2)),load(f'galka_{rc_name}_lewa',KNOB).tf(M@T(-39,0,0)@RY(-math.pi/2)),
            lathe([(16.67,0),(18.27,0),(18.27,1.2),(16.67,1.2),(16.67,0)],SERVO,64).tf(M@T(33,0,0)@RZ(-math.pi/2))]
s.add(tf([load('ramie_prawa',GRAY),load('ramie_lewa',GRAY2),load('ramie_prawa_wklad',RED),load('ramie_lewa_wklad',RED)],M1)+kn(M1,'R28'))
s.add(tf([load('przedramie_prawa',GRAY),load('przedramie_lewa',GRAY2),load('przedramie_prawa_wklad',RED),load('przedramie_lewa_wklad',RED)],M2)+kn(M2,'R28'))
s.add(tf([load('szyjka_prawa',GRAY),load('szyjka_lewa',GRAY2)],M3)+kn(M3,'R25'))
Mh=M3@T(0,L3,0)@RX(-math.pi/2)@T(0,0,24)@CADM
s.add(tf([load('rama_ladowarki',PART),load('szyna_z_lewa_szczeka',PART),load('prawa_szczeka',PART),load('zaslepka_szyny',PART),load('kolnierz_przechylu',PART),
          load('nakladka_szczeki_1',(30,30,30)),load('nakladka_szczeki_2',(30,30,30))],Mh))
W=lambda M,p:tuple((M@np.array([p[0],p[1],p[2],1.0]))[:3])
s.lab((90,40,40),'01 · korpus (elektronika w środku)',-1,690)
s.lab((95,-12,40),'02b · cokół z piaskiem\n(albo 02 · dno ze stalą)',-1,770)
s.lab((0,80,26),'03 · wieża + talerz',1,560)
s.lab(W(M1,(25,60,0)),'04 · ramię',1,470)
s.lab(W(M2,(25,55,0)),'05 · przedramię',1,200)
s.lab(W(M3,(20,48,0)),'06 · nadgarstek z szyjką',-1,300)
s.lab(W(Mh,(-60,-17,0)),'07 · uchwyt telefonu z ładowarką',-1,170)
s.lab(W(M2,(33+4,0,0)),'09 · gałki, 08 · pierścienie',1,340)
s.lab(W(Mh,(-85,6,0)),'10 · nakładki TPU',-1,420)
s.save('mapa.png')
print('ok')
