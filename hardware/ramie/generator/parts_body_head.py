import numpy as np, math, json, sys
from scipy import ndimage
from vox import *
R_UP=[[1,0,0],[0,0,-1],[0,1,0]]          # model: y w górę -> druk: z
CAD_YUP=[[1,0,0],[0,0,-1],[0,1,0]]       # CAD (x, y przód, z góra): y -> z druku (tył części na stole)
FLAT=np.eye(3)
R_DOWN=[[1,0,0],[0,0,1],[0,-1,0]]      # model: y w dół -> druk: z (górna ścianka na stole)

def body_occ(g):
    N=4.2
    pts=[(0.93,0)]+[(0.93+0.07*math.sin(k/6*math.pi/2),5-5*math.cos(k/6*math.pi/2)) for k in range(1,7)]+[(1,12)]
    pts+=[(0.46+0.54*math.cos(k/20*math.pi/2),12+40*math.sin(k/20*math.pi/2)**0.85) for k in range(1,21)]
    hs=np.array([p[1] for p in pts]); ss=np.array([p[0] for p in pts])
    smax=np.interp(g.Y,hs,ss,left=0.93,right=-1)
    k=1-0.08*np.clip((g.Y-12)/40,0,None)
    q=((np.abs(g.X)/110)**N+(np.abs(g.Z)/(95*k))**N)**(1/N)
    return q<=smax

def body(out,h=0.5):
    g=Grid((-112,-4,-98),(112,54,98),h)
    outer=body_occ(g)
    ext=outer.copy()
    y0=np.argmin(np.abs(g.Y[0,:,0]-0)); ext[:,:y0+1,:]=outer[:,y0:y0+1,:]
    dist=ndimage.distance_transform_edt(ext,sampling=h)
    sh=outer&~(dist>2.4)&(g.Y>=0)
    # słupki pod uszy serwa obrotu (MG996R pionowo, wałek w osi obrotu, korpus przesunięty o 10,35 w +x)
    for hx in (10.35-24.75,10.35+24.75):
        sh|=box(g,hx-4,hx+4,41.2,51,-10,10)&outer
        for hz in (-5,5): sh&=~cyl(g,'y',hx,hz,1.3,40,48.5)
    sh&=~box(g,-10.4,31.1,40,54,-10.25,10.25)                        # otwór na korpus serwa
    # kolumny pod śruby dna
    COL=[(75,55),(-75,55),(75,-55),(-75,-55)]
    for cx,cz in COL:
        sh|=cyl(g,'y',cx,cz,4.5,0,52)&outer
        sh&=~cyl(g,'y',cx,cz,1.3,-1,12)
    # tor ślizgowy pod talerz wieży
    r=np.hypot(g.X,g.Z)
    sh|=(r>=36)&(r<=39)&(g.Y>=51)&(g.Y<=52.6)
    # szczelina na kable (strona -x, obrót ±90°)
    th=np.degrees(np.arctan2(g.Z,g.X))
    sh&=~((r>=24)&(r<=34)&(np.abs(th)>=95)&(g.Y>=44))
    # gniazdo USB-C z tyłu
    sh&=~box(g,-5.5,5.5,4.5,9.5,-100,-80)
    sh&=(g.Y>=0)
    res={'korpus':save_stl(f'{out}/01_korpus_PETG_szary/korpus.stl',sh,g,R_UP)}
    # dno v0.2 z komorą obciążnika (wzorem „weighted base”): skóra 2 mm u góry, od spodu 12 przegródek głęb. 7 mm
    g2=Grid((-104,-9.6,-90),(104,0.6,90),0.4)
    N=4.2
    q=((np.abs(g2.X)/110)**N+(np.abs(g2.Z)/95)**N)**(1/N)
    inside=(q<=0.93)&(g2.Y<=0)&(g2.Y>=-9)
    plate=inside&(g2.Y>=-2)                                               # skóra
    plate|=inside&(q>=0.93-3/110)                                         # obrzeże 3 mm
    for x in (-50,0,50): plate|=inside&(np.abs(g2.X-x)<=1.2)              # żebra
    for z in (-30,30):   plate|=inside&(np.abs(g2.Z-z)<=1.2)
    FEET=[(85,62),(-85,62),(85,-62),(-85,-62)]
    for cx,cz in COL: plate|=inside&cyl(g2,'y',cx,cz,7,-10,1)
    for fx,fz in FEET: plate|=inside&cyl(g2,'y',fx,fz,8,-10,1)
    for cx,cz in COL:
        plate&=~cyl(g2,'y',cx,cz,1.7,-10,1)
        rr=np.hypot(g2.X-cx,g2.Z-cz); plate&=~((rr<=1.7+(-g2.Y-7.3))&(g2.Y<=-7.3))     # stożek pod łeb M3 od spodu
    for fx,fz in FEET: plate&=~cyl(g2,'y',fx,fz,5.2,-10,-7.8)                       # gniazda nóżek Ø10
    for i in range(-2,3): plate&=~box(g2,-44,-6,-3,1,i*11-2,i*11+2)|box(g2,6,44,-3,1,i*11-2,i*11+2)   # wentylacja w 2 środkowych przegródkach
    res['dno']=save_stl(f'{out}/02_korpus_dno_PETG_szary/dno.stl',plate,g2,R_DOWN)   # skórą do stołu, przegródki do góry
    return res

def prim_occ(g,p):
    if p['t']=='box':
        b=p['b']; return box(g,b[0],b[1],b[2],b[3],b[4],b[5])
    c=p['c']; a0,a1=p['a']
    m=cyl(g,p['ax'],c[0],c[1],p['ro'],a0,a1)
    if p['ri']>0: m&=~cyl(g,p['ax'],c[0],c[1],p['ri'],a0-1,a1+1)
    return m
def holder(out,h=0.3):
    J=json.load(open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'holder.json')))
    parts={d['name']:d for d in J['static']+J['moving168']}
    res={}
    def make(name,file,folder,extra_add=None,extra_cut=None,rot=CAD_YUP,hh=h):
        d=parts[name] if name else None
        prims=d['prims'] if d else []
        allb=[]
        for p in prims:
            if p['t']=='box': b=p['b']; allb.append(b)
            else:
                c=p['c'];a=p['a'];ro=p['ro']
                if p['ax']=='y': allb.append([c[0]-ro,c[0]+ro,a[0],a[1],c[1]-ro,c[1]+ro])
                elif p['ax']=='x': allb.append([a[0],a[1],c[0]-ro,c[0]+ro,c[1]-ro,c[1]+ro])
                else: allb.append([c[0]-ro,c[0]+ro,c[1]-ro,c[1]+ro,a[0],a[1]])
        if extra_add: allb+=extra_add[0]
        b=np.array(allb); lo=b[:,[0,2,4]].min(0)-2; hi=b[:,[1,3,5]].max(0)+2
        g=Grid(lo,hi,hh); occ=np.zeros(g.X.shape,bool)
        for p in prims: occ|=prim_occ(g,p)
        if d:
            for c in d.get('cuts',[]): occ&=~prim_occ(g,c)
        if extra_add: occ|=extra_add[1](g)
        if extra_cut: occ&=~extra_cut(g)
        res[file]=save_stl(f'{out}/{folder}/{file}.stl',occ,g,rot)
    F='07_uchwyt_telefonu_PETG_szary'
    def rama_cut(g):
        c=np.zeros(g.X.shape,bool)
        for x in (-40,-20,20,40):
            c|=cyl(g,'y',x,0,1.7,-16,-10)
            c|=(np.hypot(g.X-x,g.Z)<=1.7+np.clip(g.Y+12.9,0,None))&(g.Y>=-12.9)&(g.Y<=-11.2)   # stożek pod łeb wpuszczany od przodu
        for x in (-16,16): c|=cyl(g,'y',x,0,1.3,-15,-12.2)                    # pilotowe pod śruby kołnierza
        return c
    make('rama','rama_ladowarki',F,extra_cut=rama_cut)
    def szyna_cut(g):
        c=np.zeros(g.X.shape,bool)
        for x in (-40,-20,20,40): c|=cyl(g,'y',x,0,1.3,-19,-13)
        for x in (-16,16): c|=cyl(g,'y',x,0,1.7,-22,-13)
        c|=cyl(g,'x',-17.6,0,1.3,91,101)          # pilot pod śrubę zaślepki
        return c
    make('szyna','szyna_z_lewa_szczeka',F,extra_cut=szyna_cut)
    make('szczeka_p','prawa_szczeka',F)
    make('zaslepka','zaslepka_szyny',F,extra_cut=lambda g: cyl(g,'x',-17.6,0,1.7,99,104),rot=[[0,0,1],[0,1,0],[-1,0,0]])
    def kol_add():
        return ([[-22,22,-27,-21,-14,14]], lambda g: box(g,-22,22,-27,-21,-14,14))
    def kol_cut(g):
        c=cyl(g,'y',0,0,3.6,-28,-20)
        for x in (-16,16): c|=cyl(g,'y',x,0,1.7,-28,-20)|cyl(g,'y',x,0,3.1,-28,-25.2)
        return c
    make(None,'kolnierz_przechylu',F,extra_add=kol_add(),extra_cut=kol_cut)
    T='10_TPU'
    make('tpu_l','nakladka_szczeki_1',T,rot=[[0,0,1],[0,1,0],[-1,0,0]])
    make('tpu_p','nakladka_szczeki_2',T,rot=[[0,0,-1],[0,1,0],[1,0,0]])
    return res

def flat_ring(path,ro,ri,t,h=None):
    return ring_mesh(path,ro,ri,t)
def accents(out):
    res={}
    A='08_akcent_pomaranczowy_PETG'; B='09_dekle_czarne_PETG'
    for i in range(1,5): res[f'pierscien_R28_{i}']=flat_ring(f'{out}/{A}/pierscien_bark_lokiec_{i}.stl',18.27,16.67,1.2)
    for i in range(1,3): res[f'pierscien_R25_{i}']=flat_ring(f'{out}/{A}/pierscien_nadgarstek_{i}.stl',16.4,14.8,1.2)
    res['pierscien_szyjki']=flat_ring(f'{out}/{A}/pierscien_szyjki.stl',24.2,22.2,1.6)
    for i in range(1,5): res[f'dekiel_R28_{i}']=flat_ring(f'{out}/{B}/dekiel_bark_lokiec_{i}.stl',13.9,0,1.2)
    for i in range(1,3): res[f'dekiel_R25_{i}']=flat_ring(f'{out}/{B}/dekiel_nadgarstek_{i}.stl',12.4,0,1.2)
    return res

if __name__=='__main__':
    out=sys.argv[1]
    for fn in sys.argv[2:]:
        for k,v in globals()[fn](out).items(): print(k,v,flush=True)

def plinth(out,h=0.4):
    """Cokół piaskowy (wzorem podstawy SO-ARM101 z MakerWorld): pełna bryła do druku z rzadkim gyroidem,
    po wydruku nawiercasz otwór w znaczniku, wsypujesz suchy piasek i zalewasz otwór żywicą.
    Zastępuje dno: góra = obrys spodu korpusu, dół rozszerzony o 8% (szersza stopa)."""
    N=4.2; H=22
    g=Grid((-112,-H-0.6,-97),(112,0.6,97),h)
    t=np.clip(-g.Y/H,0,1)
    sc=0.93*(1+0.08*t)
    q=((np.abs(g.X)/110)**N+(np.abs(g.Z)/95)**N)**(1/N)
    occ=(q<=sc)&(g.Y<=0)&(g.Y>=-H)
    COL=[(75,55),(-75,55),(75,-55),(-75,-55)]
    for cx,cz in COL:
        occ&=~cyl(g,'y',cx,cz,1.7,-H-1,1)                 # przelot M3 do kolumn korpusu
        occ&=~cyl(g,'y',cx,cz,3.3,-H-1,-6)                # pogłębienie na łeb od spodu (zostaje 6 mm materiału)
    for fx,fz in [(90,68),(-90,68),(90,-68),(-90,-68)]:
        occ&=~cyl(g,'y',fx,fz,5.2,-H-1,-H+2)              # gniazda nóżek Ø10
    for i in range(-2,3):
        occ&=~box(g,-30,30,-H-1,1,i*11-2,i*11+2)          # przelotowe szczeliny wentylacyjne (slicer zrobi wokół nich ścianki)
    for mx,mz in [(0,62),(0,-62)]:
        occ&=~((np.hypot(g.X-mx,g.Z-mz)<=1.5+(g.Y+0.8)*1.5)&(g.Y>=-0.8))   # znaczniki do nawiercenia otworu na piasek
    return {'dno_piaskowe':save_stl(f'{out}/02b_dno_piaskowe_PETG_szary/dno_piaskowe.stl',occ,g,R_UP)}
