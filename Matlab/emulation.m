G = tf(144,[1 7.2 144]);
T = 0.01;

Gz = c2d(G,T,'zoh')
[num,den] = tfdata(Gz,'v')