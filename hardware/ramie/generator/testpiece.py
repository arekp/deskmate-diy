import numpy as np, math
from vox import *
g=Grid((-36,-16,0),(36,16,9),0.2)
o=box(g,-35,35,-15,15,0,8.5)
o&=~cyl(g,'z',-20,0,11.08,1.5,9)          # gniazdo 608 (Ø22,15 × 7) – jak w krążkach
o&=~cyl(g,'z',-20,0,6,-1,1.6)             # przelot pod śrubę M8 w kołnierzu
ang=np.arctan2(g.Y,g.X-8); r=np.hypot(g.X-8,g.Y)
o&=~((r<=6.65/np.cos(((ang+np.pi/6)%(np.pi/3))-np.pi/6))&(g.Z>=3))   # gniazdo łba M8
o&=~cyl(g,'z',8,0,4.2,-1,10)               # otwór M8
for i,(x,rr) in enumerate([(22,1.3),(28,1.3),(22,1.7),(28,1.7)]):
    y=-8 if i<2 else 8
    o&=~cyl(g,'z',x,y,rr,-1,10)            # pilot M3 Ø2,6 i przelot Ø3,4
print(save_stl(__import__('sys').argv[1]+'/00_test_pasowania_PETG/test_pasowania.stl',o,g,None))
