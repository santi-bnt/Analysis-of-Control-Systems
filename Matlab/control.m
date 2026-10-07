G = tf([3],[1 0.1])

kp = 1.3;
ki = 2.72;
kd = 0;

P = tf([kd kp ki],[1 0])

H = feedback(P*G,1)

wn = sqrt(130.6/4);

n = ((31.99/4) /2)/wn;

ts = 4/(n*wn)
roots(cell2mat(H.Denominator))

