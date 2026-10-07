G = tf([1.975],[1 13.34 7.063])

kp = 1.81166012124178;
ki = 0.949432004474916;
kd = 0;

P = tf([kd kp ki],[1 0])

H = feedback(P*G,1)
roots(cell2mat(H.Denominator))



