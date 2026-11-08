from guide import *
def sx(ah,at,y,z,col=STEEL):   # śruba wzdłuż x: łeb w ah, koniec w at
    s=1 if at>ah else -1
    return [cylX(1.5,min(ah,at),max(ah,at),col,12).tf(T(0,y,z)),cylX(2.75,min(ah,ah-3*s),max(ah,ah-3*s),col,16).tf(T(0,y,z))]
def sy(ah,at,x,z,col=STEEL):
    s=1 if at>ah else -1
    return [cylY(1.5,min(ah,at),max(ah,at),col,12).tf(T(x,0,z)),cylY(2.75,min(ah,ah-3*s),max(ah,ah-3*s),col,16).tf(T(x,0,z))]
def sr(angle,y,r0,r1):   # śruba promieniowa w płaszczyźnie x-z
    a=math.radians(angle); M=RY(-a)
    return tf(sx(r1,r0,y,0),M)
YAWS=[box(40.7,40,19.7,SERVO,(10.35,32,0)),box(54,2.5,19.7,SERVO,(10.35,39.95,0)),cylY(3,52,56.5,METAL,16).tf(T(0,0,0))]
PIL_X=(10.35-24.75,10.35+24.75)
import sys
which=sys.argv[1:] or ['1','2','3','4','5','6','7','8']

if '1' in which:
    s=Step('Krok 1 · Serwo obrotu w korpusie','Korpus odwróć do góry dnem. Serwo MG996R wkładasz od spodu, wałkiem do góry przez otwór w suficie.',eye=(380,-330,430),tgt=(10,0,0),fov=40)
    s.add([load('korpus',GRAY)])
    dy=-75; s.add(tf(YAWS,T(0,dy,0)))
    scr=[]
    for x in PIL_X:
        for z in (-5,5): scr+=tf(sy(38.7,46.5,x,z),T(0,dy-60,0))
    s.add(scr)
    s.dash((10.35,32+dy,0),(10.35,32,0))
    s.dash((PIL_X[1],38.7+dy-60,5),(PIL_X[1],40,5))
    s.lab((10.35,30+dy,-9.85),'Serwo MG996R (obrót podstawy)\nwałek do góry, w osi korpusu',-1,560)
    s.lab((PIL_X[0],38.7+dy-60,5),'4 × M3 × 10 przez uszy serwa\nod spodu w słupki',-1,760)
    s.lab((PIL_X[1],44,-10),'Słupki z otworami Ø2,6',1,520)
    s.lab((10,52,10),'Otwór na korpus serwa\nw suficie korpusu',1,300)
    s.lab((0,7,-95),'Gniazdo USB-C z tyłu',1,720)
    s.save('krok1.png')

BOARDS=[((-8,8,-86,-72,2),'Wyzwalacz USB-C PD (CH224K), 20 V'),((-90,-38,-48,12,24),'Przetwornica 20 → 6 V, 10 A'),((38,84,-60,-34,8),'Moduł USB QC3.0 (ładowarka Anker)'),
        ((58,80,-24,-6,4),'Przetwornica 5 V (ESP32)'),((36,88,4,32,6),'ESP32'),((34,96,38,64,5),'PCA9685 (sterownik serw)')]
if '2' in which:
    s=Step('Krok 2 · Elektronika, cokół z piaskiem i korpus','Cokół wypełnij piaskiem (1 kg) i zalej otwór. Płytki przyklej taśmą dwustronną. Korpus przykręć od spodu.',eye=(360,330,520),tgt=(0,30,0),fov=40)
    s.add([load('dno_piaskowe',GRAY2)])
    for (x0,x1,z0,z1,h),_ in BOARDS: s.add([box(x1-x0,h,z1-z0,PCB if h<20 else METAL,((x0+x1)/2,h/2,(z0+z1)/2))])
    s.add([cylY(9,0,32,(40,44,48),20).tf(T(-22,0,40))])
    up=95; s.add(tf([load('korpus',GRAY)]+YAWS,T(0,up,0)))
    for cx,cz in [(75,55),(-75,55),(75,-55),(-75,-55)]: s.add(tf(sy(-6,14,cx,cz),T(0,-60,0)))
    s.dash((0,up,60),(0,2,60)); s.dash((75,-66,55),(75,-20,55))
    for i,((x0,x1,z0,z1,h),t) in enumerate(BOARDS):
        side=1 if (x0+x1)/2>0 else -1
        s.lab(((x0+x1)/2,h,(z0+z1)/2),t,side,[0,0,520,600,650,700][i] if side>0 else [560,620,0,0,0,0][i])
    s.lab((-22,32,40),'Kondensator 4700 µF',-1,500)
    s.lab((-75,-60,-55),'4 × M3 × 20 stożkowe\nod spodu przez cokół w kolumny korpusu',-1,690)
    s.lab((0,-1,62),'Znacznik: nawierć Ø8, wsyp piasek,\nzalej żywicą',-1,760)
    s.lab((60,up+40,0),'Korpus (z serwem z kroku 1)',1,260)
    s.save('krok2.png')

def turret_full(): return [load('wieza_prawa',GRAY),load('wieza_lewa',GRAY2),load('talerz_wiezy',GRAY)]
if '3' in which:
    s=Step('Krok 3 · Talerz i wieża z serwem barku','Talerz przykręć do orczyka serwa obrotu. Serwo barku do prawej połówki, łożysko 608 do lewej. Połówki nałóż fartuchem na talerz.',eye=(420,330,430),tgt=(0,70,0),fov=40)
    s.add([load('korpus',GRAY)]+YAWS)
    s.add([cylY(13,52.6,56.4,METAL,40)])
    tl=[load('talerz_wiezy',GRAY)]; s.add(tf(tl,T(0,25,0)))
    s.add(tf(sy(62,57,0,0),T(0,25,0)))
    R=tf([load('wieza_prawa',GRAY)]+tf(servo_pitch(),T(0,SZ,0)),T(75,60,0))
    Lf=tf([load('wieza_lewa',GRAY2)]+tf(bearing(),T(0,SZ,0)),T(-75,60,0))
    s.add(R+Lf)
    for a in (30,-30):  s.add(tf(sr(a,55.3,40,50),T(30,60,0)))
    for a in (150,210): s.add(tf(sr(a,55.3,40,50),T(-30,60,0)))
    for (y,z,hw) in [(SZ+22,0,27),(76,14,20.7),(76,-14,20.7)]: s.add(tf(sx(-hw+3.2,hw-1.6,y,z),T(-150,60,0)))
    s.dash((0,52.6+25,0),(0,55,0)); s.dash((75,60+SZ,0),(12,SZ,0)); s.dash((-75,60+SZ,0),(-12,SZ,0))
    s.lab((0,58+25,0),'Talerz: orczyk Ø26 we wnęce od spodu,\nśruba środkowa orczyka przez otwór Ø8',-1,330)
    s.lab((75+1.5,60+SZ-10,-9.85),'Serwo barku (DS3218)\n4 × M3 × 10 w żeberka',1,280)
    s.lab((75+27.7,60+SZ,0),'Orczyk 25T na wałku',1,390)
    s.lab((-75-22,60+SZ,0),'Łożysko 608 wciśnięte\nod środka',-1,250)
    s.lab((-150-20.7,60+76,14),'1 × M3 × 45 + 2 × M3 × 35\nod lewej, łączą połówki',-1,470)
    s.lab((30+45*0.87,60+55.3,25),'4 × M3 × 10 promieniowo\nprzez fartuch w talerz',1,520)
    s.save('krok3.png')

def joint_step(fname,title,sub,parent,parent_shift,child,L,Rd,bosses,next_servo=True,extra=None,lab_child='',lab_screws=''):
    s=Step(title,sub,eye=(430,250,470),tgt=(0,L*0.45,0),fov=40)
    s.add(tf(parent,T(0,-parent_shift,0)))
    E=70
    R=[load(child+'_prawa',GRAY)]+([load(child+'_prawa_wklad',RED)] if os.path.exists(S+child+'_prawa_wklad.stl') else [])
    Lf=[load(child+'_lewa',GRAY2)]+([load(child+'_lewa_wklad',RED)] if os.path.exists(S+child+'_lewa_wklad.stl') else [])
    if next_servo: R+=tf(servo_pitch(),T(0,L,0)); Lf+=tf(bearing(),T(0,L,0))
    s.add(tf(R,T(E,0,0))+tf(Lf,T(-E,0,0)))
    s.add(tf(bolt_m8(),T(-E-45,0,0)))
    s.add(tf(horn(),T(0,0,0)))
    for (y,z,hw) in bosses: s.add(tf(sx(-hw+3.2,hw-1.6,y,z),T(-E-90,0,0)))
    s.dash((E+31,0,0),(31,0,0)); s.dash((-E-45-34,0,0),(-34,0,0)); s.dash((-E-30,L*0.5,0),(-10,L*0.5,0))
    s.lab((31.5+E,0,-10),'Prawy policzek: przykręć do orczyka\n(nawierć Ø2 przez otwory orczyka)',1,640)
    s.lab((27.7,0,12),'Orczyk na wałku serwa\n(serwo w pozycji 90°)',1,780)
    s.lab((-E-45-34,0,0),'Śruba M8 × 12: łeb w gnieździe\nlewego policzka, trzon w łożysku',-1,700)
    s.lab((-E-90-20,bosses[0][0],bosses[0][1]),lab_screws,-1,420)
    if next_servo:
        s.lab((E+1.5,L-10,9.85),'Serwo następnego przegubu\n4 × M3 × 10 w żeberka',1,250)
        s.lab((-E-22,L,0),'Łożysko 608 następnego przegubu',-1,250)
    s.lab((E+20,L*0.55,15),lab_child,1,420)
    s.save(fname)

if '4' in which:
    joint_step('krok4.png','Krok 4 · Bark: ramię na wieży','Ramię to dwie połówki. Widełki obejmują krążek wieży: prawy policzek na orczyku, lewy na łożysku i śrubie M8.',
        turret_full()+tf(servo_pitch(),T(0,SZ,0)),SZ,'ramie',110,28,[(45,0,30),(64,0,27.3),(132,0,27)],
        lab_child='Ramię, prawa połówka\n(ażur, rowek z czerwoną listwą)',lab_screws='Łączenie połówek od lewej:\n1 × M3 × 50 + 2 × M3 × 45')
if '5' in which:
    joint_step('krok5.png','Krok 5 · Łokieć: przedramię','Tak samo jak bark. Serwo łokcia i łożysko siedzą już w ramieniu (krok 4).',
        [load('ramie_prawa',GRAY),load('ramie_lewa',GRAY2)]+tf(servo_pitch(),T(0,110,0)),110,'przedramie',100,25,[(42,0,30),(54,0,28.2)],
        lab_child='Przedramię, prawa połówka\nz napisem DeskMate',lab_screws='Łączenie połówek od lewej:\n1 × M3 × 50 + 1 × M3 × 45')

if '6' in which:
    s=Step('Krok 6 · Nadgarstek i szyjka z serwem przechyłu','MG90S zaciśnij między połówkami szyjki, bez śrub. Widełki nadgarstka jak w kroku 4.',eye=(380,230,420),tgt=(0,15,0),fov=40)
    s.add(tf([load('przedramie_prawa',GRAY),load('przedramie_lewa',GRAY2)]+tf(servo_pitch(),T(0,100,0)),T(0,-100,0)))
    s.add(horn())
    E=60
    mg=[box(22.8,23,12.2,SERVO,(5.5,42.5,0)),box(32.2,2.5,12.2,SERVO,(5.5,48,0)),cylY(2.4,54,58,METAL,12).tf(T(0,0,0))]
    s.add(tf([load('szyjka_prawa',GRAY)],T(E,0,0))+tf([load('szyjka_lewa',GRAY2)],T(-E,0,0))+tf(mg,T(0,0,45)))
    s.add(tf(bolt_m8(),T(-E-45,0,0)))
    for (y,z) in [(36,12.5),(36,-12.5),(52,13.5),(52,-13.5)]: s.add(tf(sx(-19.5,19,y,z),T(-E-80,0,0)))
    s.dash((5.5,42.5,45),(5.5,42.5,8)); s.dash((-E-45-34,0,0),(-34,0,0))
    s.lab((5.5+11.4,42.5,45),'Serwo MG90S (przechył głowy)\nwałek w osi szyjki, uszy w szczelinie',1,260)
    s.lab((E+31.5,0,-10),'Prawy policzek na orczyku\nserwa nadgarstka',1,620)
    s.lab((-E-45-34,0,0),'Śruba M8 × 12 + łożysko 608\nw krążku przedramienia',-1,640)
    s.lab((-E-80-19.5,52,13.5),'4 × M3 × 35 od lewej\nłączą połówki szyjki',-1,380)
    s.save('krok6.png')

if '7' in which:
    s=Step('Krok 7 · Uchwyt telefonu z ładowarką','Rama przesuwa się po szynie o ±8 mm. Ustaw ją tak, żeby telefon się ładował, i dopiero wtedy dokręć.',eye=(260,260,-420),tgt=(0,0,-10),fov=42)
    C=lambda lst,M=np.eye(4): [m.tf(M@CADM) for m in lst]
    rama=[load('rama_ladowarki',PART)]; pad=[cylY(50,-11.2,0,(230,233,235),64).tf(RX(-math.pi/2)).tf(np.linalg.inv(CADM))]
    s.add(C(rama+[load('szyna_z_lewa_szczeka',PART)]))
    s.add([m.tf(T(0,0,0)) for m in []])
    s.add(C([load('prawa_szczeka',PART)],T(35,0,0)))
    s.add(C([load('zaslepka_szyny',PART)],T(60,0,0)))
    s.add(C([load('kolnierz_przechylu',PART)],T(0,-45,0)))
    s.add(C([load('nakladka_szczeki_1',(30,30,30)),load('nakladka_szczeki_2',(30,30,30))]))
    for x in (-40,-20,20,40):
        s.add([m.tf(T(0,0,0)) for m in tf(sy(0,0,0,0),np.eye(4))][:0])
    # śruby (w CAD): rama–szyna od przodu, kołnierz od tyłu, zaślepka wzdłuż x
    scr=[]
    for x in (-40,-20,20,40):
        scr+= [m.tf(CADM) for m in [cylY(1.5,-21,-11.2,STEEL,12).tf(T(x,0,0)),cylY(2.75,-11.2,-9.5,STEEL,16).tf(T(x,0,0))]]
    s.add(tf(scr,T(0,0,0)))
    kscr=[]
    for x in (-16,16): kscr+=[m.tf(CADM) for m in [cylY(1.5,-25.2,-15.2,STEEL,12).tf(T(x,0,0)),cylY(2.75,-28.2,-25.2,STEEL,16).tf(T(x,0,0))]]
    s.add(tf(kscr,CADM@T(0,-80,0)@np.linalg.inv(CADM)))
    s.add([m.tf(CADM) for m in [cylX(1.5,91,103,STEEL,12).tf(T(0,-17.6,0)),cylX(2.75,103,106,STEEL,16).tf(T(0,-17.6,0))]])
    s.add([m.tf(CADM@T(75,0,0)) for m in [cylX(1.5,91,103,STEEL,12).tf(T(0,-17.6,0)),cylX(2.75,103,106,STEEL,16).tf(T(0,-17.6,0))]][:0])
    hornk=[m.tf(CADM) for m in [cylY(9,-34,-31,METAL,30).tf(T(0,-60,0))]]
    s.add(hornk)
    p=lambda x,y,z:(x,z,y)
    s.dash(p(0,-45-24,0),p(0,-24,0)); s.dash(p(35+88,-5,0),p(88,-5,0))
    s.lab(p(30,-12,30),'Rama ładowarki z półką\n(podłużne otwory ±8 mm)',-1,250)
    s.lab(p(-40,-11.2,0),'4 × M3 × 10 stożkowe od przodu\nprzez ramę w szynę',1,560)
    s.lab(p(-60,-17.6,8),'Szyna z lewą szczęką',1,380)
    s.lab(p(35+88,-5,-25),'Prawa szczęka: nasuń na szynę',-1,190)
    s.lab(p(60+101,-17.6,0),'Zaślepka + 1 × M3 × 12',-1,560)
    s.lab(p(0,-45-27,10),'Kołnierz przechyłu: 2 × M3 × 10 w szynę,\norczyk MG90S przykręcony do kołnierza',-1,760)
    s.lab(p(-84,6,0),'Nakładki TPU na szczękach (klej)',1,250)
    s.save('krok7.png')

if '8' in which:
    s=Step('Krok 8 · Gałki, pierścienie i listwy','Gałki wciskasz czopem w policzki. Pierścienie i listwy (gdy drukujesz bez AMS) przyklej klejem CA.',eye=(420,200,380),tgt=(0,0,0),fov=40)
    s.add(tf([load('ramie_prawa',GRAY),load('ramie_lewa',GRAY2)],T(0,-110,0)))
    s.add([load('przedramie_prawa',GRAY),load('przedramie_lewa',GRAY2),load('przedramie_prawa_wklad',RED)])
    kR=load('galka_R28_prawa',KNOB).tf(T(33+45,0,0)@RY(math.pi/2)); kL=load('galka_R28_lewa',KNOB).tf(T(-39-45,0,0)@RY(-math.pi/2))
    ringR=lathe([(16.67,0),(18.27,0),(18.27,1.2),(16.67,1.2),(16.67,0)],SERVO,64).tf(RZ(-math.pi/2)).tf(T(33+20,0,0))
    ringL=lathe([(16.67,0),(18.27,0),(18.27,1.2),(16.67,1.2),(16.67,0)],SERVO,64).tf(RZ(math.pi/2)).tf(T(-39-20,0,0))
    s.add([kR,kL,ringR,ringL])
    strip=box(1.4,33.6,3.2,RED,(0,0,0))
    s.add([strip.tf(T(-33,-110+30+0.59*80,0))])
    s.dash((33+45,0,0),(33,0,0)); s.dash((-39-45,0,0),(-39,0,0)); s.dash((-33,-110+30+0.59*80,0),(-25.5,-110+30+0.59*80,0))
    s.lab((33+50,0,-12),'Gałka prawa: czop Ø7,8 w otwór Ø8\n(zdejmowana, pod nią śruba orczyka)',1,520)
    s.lab((33+20,17.5,0),'Pierścień 36,5 mm (klej)',1,330)
    s.lab((-39-50,0,-12),'Gałka lewa: czop sześciokątny\nnad łbem M8',-1,520)
    s.lab((-33,-110+30+0.59*80,1.6),'Listwa 33,6 mm w rowek\n(tylko druk bez AMS)',-1,740)
    s.lab((25,55,0),'Napis DeskMate: wkładka z AMS\nalbo farba',1,700)
    s.save('krok8.png')
print('ok',which)
